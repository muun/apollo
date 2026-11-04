package nfc

import (
	"bytes"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"math/big"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/base/domain/cryptography"
	"github.com/muun/libwallet/platform/preconditions"
)

// Domain separation tags, byte-identical to the firmware's and the vendor
// CLI's.
var (
	tagPairingSecret    = []byte("pairing-secret")
	tagPairingResponse  = []byte("pairing-response")
	tagChallengeReq     = []byte("challenge-request")
	tagChallengeResp    = []byte("challenge-response")
	tagChallengeRatchet = []byte("challenge-ratchet")
	tagCardApproval     = []byte("card-approval")
)

// MaxUsesPerKeyV3 is MAX_USES_PER_KEY: how many pairings one key agreement
// key serves before PAIR moves to the next, so pairing n uses key n / 16.
// Each pairing makes the card multiply that key by a point the caller
// chose, which is the sampling a differential side-channel attack collects,
// so the count bounds it. It is the protocol spec's value.
const MaxUsesPerKeyV3 = 16

// MaxPairingIndexV3_1 is MAX_PAIRING_INDEX, the last index a card hands out
// in its life: the pairing usage budget. The first pairing gets 0, so a card
// answers MaxPairingIndexV3_1 + 1 pairings, MaxUsesPerKeyV3 on each of its
// key agreement keys.
const MaxPairingIndexV3_1 = MaxUsesPerKeyV3*KeyAgreementKeyCountV3_1 - 1

// metadataKeyIndexV3 is METADATA_KEY_INDEX: the key agreement key
// GET_METADATA describes, whichever one PAIR is up to.
const metadataKeyIndexV3 = 0

// maxFailuresV3 is the number of consecutive challenges the card answers
// without one ever confirming the ratchet, after which it refuses until a
// new pairing.
const maxFailuresV3 = 32

// MockMuunCardV3 reimplements the V3 firmware logic in Go for testing without
// hardware, mirroring cards/vendor-cli/muuncardv3/mockcard.go so the two stay
// legible side by side. Any firmware protocol change has to reach both.
//
// It is split the way the firmware is: a mockMetadataAppletV3 holds the
// persistent state MetadataApplet owns, one per firmware generation, and
// this type holds the protocol and the pairing state MuunCardV3 owns,
// reaching the other through the methods that mirror the SIO interface. The
// generation it runs as decides which applet it holds, what it answers at
// SELECT and which secret keys its challenge response MAC; the rest is the
// same for all of them.
//
// It starts where the vendor CLI's mock ends: libwallet never runs an
// initialization ceremony, so this one holds its keys and certificates,
// READY to pair. From there it covers pairing and the challenge flow,
// reasons streamed in chunks included.
//
// The pairing MAC and the metadata's authentication form are built here from
// the firmware's layout, independently of the Houston simulator that verifies them,
// so a pairing test fails when the two disagree.
type MockMuunCardV3 struct {
	metadataApplet mockMetadataAppletV3

	// muunPub is Muun's identity key, which the firmware carries as the
	// MUUN_PUB constant. It is a parameter here so a test can pair against
	// whichever key its server side holds.
	muunPub []byte

	// The generation this card runs as, and the firmware version it reports
	// within it, held as state because a reinstall changes the first and an
	// upgrade the second, and the pairing state below goes with either.
	generation      MockFirmwareGenerationV3
	firmwareVersion [VersionSizeV3]byte

	// pairingIndex names the live pairing: a copy of the index the metadata
	// applet handed out for it, kept with the rest of the pairing state so a
	// challenge for any other pairing is refused without reaching it.
	pairingIndex  uint16
	paired        bool
	confSecret    [32]byte
	secretNext    [32]byte
	lastCallCount uint32
	// Challenges answered since the last one that confirmed the ratchet.
	// Bounds how many times an attacker can make the card HMAC under the
	// same conf_secret, which is what caps the traces a differential
	// side-channel attack can collect.
	failureCounter byte

	// A reason too long for one command arrives in chunks, so the card has
	// to hold the request while it waits for the rest: what it will
	// authenticate against, and how much of the reason has landed.
	streamingActive       bool
	streamingChallengeC   []byte // C, the server's ephemeral public key
	streamingCallCounter  uint32
	streamingIndex        uint16
	streamingMac          []byte
	streamingReasonLength int // the total the first command declared
	accumulatedReason     []byte
	accumulatedLength     int
}

var _ JavaCardApplet = (*MockMuunCardV3)(nil)

// NewMockMuunCardV3 returns a card of the given generation that has already
// completed its initialization ceremony, READY to pair against the given
// Muun identity key. The ceremony is the metadata applet's: what keys and
// certificates the card holds is that applet's business, and this one only
// reaches them over SIO.
func NewMockMuunCardV3(
	muunPub []byte,
	generation MockFirmwareGenerationV3,
) (*MockMuunCardV3, error) {
	if err := cryptography.ValidateSecp256r1PublicKey(muunPub); err != nil {
		return nil, errors.Errorf("invalid muun public key: %w", err)
	}

	metadataApplet, err := generation.firmware().newMetadataApplet()
	if err != nil {
		return nil, err
	}

	card := &MockMuunCardV3{
		metadataApplet:    metadataApplet,
		muunPub:           muunPub,
		generation:        generation,
		firmwareVersion:   generation.firmware().version,
		accumulatedReason: make([]byte, MaxReasonSizeV3),
	}

	// The firmware pushes its version into the metadata applet on its first
	// SIO connect, in registerFirmwareVersion.
	card.metadataApplet.setFirmwareVersion(card.FirmwareVersion())

	return card, nil
}

// CommitmentPublicKey returns the key the card signs its approvals with, so a
// test outside this package can verify an approval under the card's own key
// rather than under anything the server holds.
func (m *MockMuunCardV3) CommitmentPublicKey() []byte {
	publicKey, err := cryptography.GenerateSecp256r1PublicKey(
		m.metadataApplet.getCommitmentKeyBytes(),
	)
	if err != nil {
		panic(errors.Errorf("mock card holds an unusable commitment key: %w", err))
	}
	return publicKey
}

// ConfirmedSecret returns the secret the card is authenticating challenges
// under, so a test outside this package can assert both sides reached the
// same one.
func (m *MockMuunCardV3) ConfirmedSecret() []byte {
	secret := m.confSecret
	return secret[:]
}

// ShortDeviceID returns the id the card reports at SELECT, so a test outside
// this package can assert the client read it.
func (m *MockMuunCardV3) ShortDeviceID() [ShortDeviceIDSizeV3]byte {
	return m.metadataApplet.getShortDeviceID()
}

// VendorCertificate returns the certificate the card holds for key agreement
// key keyIndex, so a test outside this package can check that a pairing
// carries the certificate of the key it used.
func (m *MockMuunCardV3) VendorCertificate(keyIndex int) []byte {
	return bytes.Clone(m.metadataApplet.getCertificate(keyIndex))
}

// SetDeviceStatus moves the card along its configuration lifecycle, so a test
// outside this package can put it where the ceremony has not finished.
func (m *MockMuunCardV3) SetDeviceStatus(status DeviceStatusV3) {
	m.metadataApplet.setDeviceStatus(status)
}

// UpgradeFirmware replaces MuunCardV3 alone with firmware major.minor, and
// leaves the card unpaired, which is the half of an upgrade a client has to
// survive: the pairing lives in the upgradeable applet, so a new one lands
// without it. The metadata applet stays as it was, and with it the
// generation, the keys, the certificates and the short device id: it is the
// same card, running newer firmware. The new version reaches that applet the
// way registerFirmwareVersion pushes it on the firmware's first SIO connect.
//
// A new MuunCardV3 has to speak the SIO interface of the metadata applet it
// finds, so an upgrade stays within the card's generation. A version older
// than the one the card runs, or one that introduced a later generation,
// panics: moving to another generation is ReinstallFirmware.
func (m *MockMuunCardV3) UpgradeFirmware(major uint16, minor uint16) {
	var version [VersionSizeV3]byte
	binary.BigEndian.PutUint16(version[:2], major)
	binary.BigEndian.PutUint16(version[2:], minor)

	preconditions.CheckStatef(
		bytes.Compare(version[:], m.firmwareVersion[:]) >= 0,
		"firmware %d.%d is older than the %d.%d the card runs",
		major, minor,
		binary.BigEndian.Uint16(m.firmwareVersion[:2]),
		binary.BigEndian.Uint16(m.firmwareVersion[2:]),
	)
	preconditions.CheckStatef(
		m.generation.runs(version),
		"firmware %d.%d does not run on a %s metadata applet: reinstall the card instead",
		major, minor, m.generation,
	)

	m.firmwareVersion = version
	m.metadataApplet.setFirmwareVersion(version)
	m.forgetPairing()
}

// ReinstallFirmware installs both applets of a generation, the only way a
// card moves between generations, and leaves it unpaired. Installing a
// metadata applet erases what the previous one held, so the card comes out
// with a new identity: it runs the generation's ceremony and reports the
// version that introduced the generation. A reinstall wipes the card, so any
// generation goes, the one the card ran included.
func (m *MockMuunCardV3) ReinstallFirmware(generation MockFirmwareGenerationV3) {
	metadataApplet, err := generation.firmware().newMetadataApplet()
	if err != nil {
		preconditions.Failf("ceremony of firmware %s: %v", generation, err)
	}

	m.metadataApplet = metadataApplet
	m.generation = generation
	m.firmwareVersion = generation.firmware().version
	m.metadataApplet.setFirmwareVersion(m.firmwareVersion)
	m.forgetPairing()
}

// FirmwareVersion mirrors MuunCardV3.writeFirmwareVersion:
// major(u16be) || minor(u16be).
func (m *MockMuunCardV3) FirmwareVersion() [VersionSizeV3]byte {
	return m.firmwareVersion
}

// ProposedSecret returns the secret the card ratcheted to on its last
// challenge and offered to the server.
//
// The card holds two: it keeps answering under the confirmed one until a
// request arrives authenticated with the proposed one, which is the only
// proof the server received the response carrying it. So after a successful
// challenge this is what the server holds, and the confirmed secret is still
// one step behind.
func (m *MockMuunCardV3) ProposedSecret() []byte {
	secret := m.secretNext
	return secret[:]
}

func (m *MockMuunCardV3) getAppletID() string {
	return MuuncardV3AppletID
}

func (m *MockMuunCardV3) processCommand(
	apdu []byte,
) (*app_provided_data.NfcBridgeResponse, error) {
	if len(apdu) < 4 {
		return newErrorResponse(SWMuuncardV3WrongLength), nil
	}

	instruction := apdu[iso7816OffsetIns]

	// The firmware resets streaming state before it dispatches, so a fresh
	// pair or challenge abandons a stream in flight instead of being
	// refused.
	if m.streamingActive &&
		(instruction == INSMuuncardV3Pair || instruction == INSMuuncardV3SignChallenge) {
		m.resetStreaming()
	}

	switch instruction {
	case insSelect:
		return m.handleSelect(apdu), nil
	case insMuuncardV3GetVersion:
		return m.handleGetVersion(), nil
	case INSMuuncardV3GetMetadata:
		return m.handleGetMetadata(), nil
	case INSMuuncardV3Pair:
		return m.handlePair(apdu), nil
	case INSMuuncardV3SignChallenge:
		return m.handleSignChallenge(apdu), nil
	case insMuuncardV3ContinueChallenge:
		return m.handleContinueChallenge(apdu), nil
	default:
		return newErrorResponse(SWINSNotSupported), nil
	}
}

// handleSelect mirrors MuunCardV3.sendSelectResponse: the SELECT response
// carries as much of firmware_version || short_device_id as the generation
// sends when Le can hold all of it, and nothing otherwise, which includes
// the no-Le form clients from before the payload send. Le is the byte after
// the AID, where 0x00 means 256.
func (m *MockMuunCardV3) handleSelect(apdu []byte) *app_provided_data.NfcBridgeResponse {
	expectedLength := 0
	if leOffset := iso7816OffsetCData + int(apdu[iso7816OffsetLc]); leOffset < len(apdu) {
		expectedLength = int(apdu[leOffset])
		if expectedLength == 0 {
			expectedLength = 256
		}
	}

	responseSize := m.generation.firmware().selectResponseSize
	if expectedLength < responseSize {
		return newOkResponse(nil)
	}

	version := m.FirmwareVersion()
	shortDeviceID := m.metadataApplet.getShortDeviceID()

	response := make([]byte, 0, SelectResponseWithDeviceIDSizeV3)
	response = append(response, version[:]...)
	response = append(response, shortDeviceID[:]...)
	return newOkResponse(response[:responseSize])
}

func (m *MockMuunCardV3) handleGetVersion() *app_provided_data.NfcBridgeResponse {
	version := m.FirmwareVersion()

	response := make([]byte, 12)
	copy(response[0:6], []byte{'M', 'u', 'u', 'n', 'V', '3'})
	response[6] = version[1] // the low byte of the major
	response[7] = version[3] // the low byte of the minor
	return newOkResponse(response)
}

func (m *MockMuunCardV3) handleGetMetadata() *app_provided_data.NfcBridgeResponse {
	return newOkResponse(m.metadataApplet.getMetadataForTransmission(metadataKeyIndexV3))
}

func (m *MockMuunCardV3) handlePair(apdu []byte) *app_provided_data.NfcBridgeResponse {
	data, errResp := m.validateAPDUData(apdu)
	if errResp != nil {
		return errResp
	}

	// Length before status, the order MuunCardV3.pair uses.
	if len(data) != Secp256r1PointSize {
		return newErrorResponse(SWMuuncardV3WrongLength)
	}

	// Only a READY card pairs. One that handed out its last pairing stays
	// READY, and the pairing index check below refuses it.
	status := m.metadataApplet.getDeviceStatus()
	if status != DeviceStatusReadyV3 {
		return newErrorResponse(SWMuuncardV3NotReady)
	}

	serverC := data[0:Secp256r1PointSize]
	if err := cryptography.ValidateSecp256r1PublicKey(serverC); err != nil {
		return newErrorResponse(SWMuuncardV3InvalidPubKey)
	}

	// The SIO calls first, as in the firmware, where every SIO call has to
	// complete before any crypto because a call disturbs the crypto engine.
	// The pairing is spent ahead of the key agreement it bounds: a tear
	// before the commit costs the attacker the trace, one after it still
	// spends the pairing. A card that handed out its last one never pairs
	// again.
	lastPairingIndex := m.metadataApplet.getPairingIndex()
	if lastPairingIndex >= MaxPairingIndexV3_1 {
		return newErrorResponse(SWMuuncardV3TooManyPairings)
	}
	m.metadataApplet.incrementPairingIndex()
	pairingIndex := uint16(lastPairingIndex + 1)
	// Each key serves MaxUsesPerKeyV3 pairings, then the next takes over,
	// with its own certificate in the metadata.
	keyIndex := int(pairingIndex) / MaxUsesPerKeyV3
	keyAgreementPriv := m.metadataApplet.getKeyAgreementKeyBytes(keyIndex)
	metadataForAuth := m.metadataApplet.getMetadataForAuth(keyIndex)
	metadata := m.metadataApplet.getMetadataForTransmission(keyIndex)

	ephemeral, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		return newErrorResponse(swMuuncardV3CryptoError)
	}

	dh1, err := cryptography.ECDH(keyAgreementPriv, serverC)
	if err != nil {
		return newErrorResponse(swMuuncardV3CryptoError)
	}

	dh2, err := cryptography.ECDH(ephemeral.PrivateKey, m.muunPub)
	if err != nil {
		return newErrorResponse(swMuuncardV3CryptoError)
	}

	ikm := make([]byte, 0, 64)
	ikm = append(ikm, dh1...)
	ikm = append(ikm, dh2...)

	// Written after the crypto above, the way the firmware commits in one
	// transaction, so a pair that fails leaves the previous pairing intact.
	secret := ComputeHMACSHA256(tagPairingSecret, ikm)
	copy(m.confSecret[:], secret)
	copy(m.secretNext[:], secret)
	m.lastCallCount = 0
	m.failureCounter = 0
	m.pairingIndex = pairingIndex
	m.paired = true

	macInput := make([]byte, 0, len(tagPairingResponse)+
		Secp256r1PointSize+Secp256r1PointSize+PairIndexSize+len(metadataForAuth))
	macInput = append(macInput, tagPairingResponse...)
	macInput = append(macInput, serverC...)
	macInput = append(macInput, ephemeral.PublicKey...)
	macInput = binary.BigEndian.AppendUint16(macInput, pairingIndex)
	macInput = append(macInput, metadataForAuth...)

	mac := ComputeHMACSHA256(m.confSecret[:], macInput)

	// P(65) || index(2) || metadata || MAC(32)
	response := make([]byte, 0, Secp256r1PointSize+PairIndexSize+len(metadata)+MACSize)
	response = append(response, ephemeral.PublicKey...)
	response = binary.BigEndian.AppendUint16(response, pairingIndex)
	response = append(response, metadata...)
	response = append(response, mac...)

	return newOkResponse(response)
}

func (m *MockMuunCardV3) validateAPDUData(
	apdu []byte,
) ([]byte, *app_provided_data.NfcBridgeResponse) {
	if len(apdu) < 5 {
		return nil, newErrorResponse(SWMuuncardV3WrongLength)
	}
	headerSize := 5
	dataLength := int(apdu[iso7816OffsetLc])
	if apdu[iso7816OffsetLc] == NullByte && len(apdu) >= 7 {
		headerSize = 7
		dataLength = int(binary.BigEndian.Uint16(apdu[5:]))
	}
	expectedLen := headerSize + dataLength
	// Whatever trails the data is the Le field: absent, or one or two bytes.
	trailing := len(apdu) - expectedLen
	if trailing == 0 || trailing == 1 || trailing == 2 {
		return apdu[headerSize:expectedLen], nil
	}
	return nil, newErrorResponse(SWMuuncardV3WrongLength)
}

// matchChallengeMac reports which secret authenticated the request. The
// proposed one is tried first, since a MAC under it is what proves the server
// received the response that carried it.
func (m *MockMuunCardV3) matchChallengeMac(
	macInput, receivedMac []byte,
) (secretSlot, bool) {
	if m.verifyChallengeMac(macInput, receivedMac, proposedSecret) {
		return proposedSecret, true
	}
	if m.verifyChallengeMac(macInput, receivedMac, confirmedSecret) {
		return confirmedSecret, true
	}
	return confirmedSecret, false
}

// handleSignChallenge runs the card side of a challenge: it authenticates the
// request under one of the two secrets it holds, answers with a MAC and a
// commitment signature, and ratchets to a fresh secret.
//
// A reason too long for one APDU only begins here: the request is held while
// the rest of it arrives through CONTINUE_CHALLENGE.
func (m *MockMuunCardV3) handleSignChallenge(
	apdu []byte,
) *app_provided_data.NfcBridgeResponse {
	data, errorResponse := m.validateAPDUData(apdu)
	if errorResponse != nil {
		return errorResponse
	}

	// The status, then the pairing, each checked once and first, the way
	// MuunCardV3.signChallenge does: a card that left READY answers no
	// challenge, even for a pairing it made before. The vendor CLI's mock
	// checks the pairing inside each path instead, so a malformed challenge
	// on an unpaired card answers differently there.
	if m.metadataApplet.getDeviceStatus() != DeviceStatusReadyV3 {
		return newErrorResponse(SWMuuncardV3NotReady)
	}
	if !m.paired {
		return newErrorResponse(SWMuuncardV3SlotNotPaired)
	}
	if len(data) < SignChallengeHeaderSizeV3+MACSize {
		return newErrorResponse(SWMuuncardV3WrongLength)
	}

	challengeC := data[:Secp256r1PointSize]
	if err := cryptography.ValidateSecp256r1PublicKey(challengeC); err != nil {
		return newErrorResponse(SWMuuncardV3InvalidPubKey)
	}

	offset := Secp256r1PointSize
	callCounter := binary.BigEndian.Uint32(data[offset:])
	offset += CallCounterSizeV3
	receivedIndex := binary.BigEndian.Uint16(data[offset:])
	offset += PairIndexSize
	declaredLength := int(binary.BigEndian.Uint16(data[offset:]))
	offset += reasonLengthSizeV3

	// The firmware reads reason_len as a signed short and answers
	// WRONG_LENGTH when it is negative, so nothing past MaxReasonSizeV3 ever
	// opens a stream on a card. Read unsigned, a declared 32768 would open
	// one here, on an accumulator that is a byte shorter than that.
	if declaredLength > MaxReasonSizeV3 {
		return newErrorResponse(SWMuuncardV3WrongLength)
	}

	if declaredLength > MaxReasonChunkSizeV3 {
		return m.startStreaming(data, declaredLength)
	}
	if len(data) != SignChallengeHeaderSizeV3+declaredLength+MACSize {
		return newErrorResponse(SWMuuncardV3WrongLength)
	}

	reason := data[offset : offset+declaredLength]
	receivedMac := data[offset+declaredLength:]
	reasonHash := sha256.Sum256(reason)

	return m.verifyChallengeAndRespond(
		challengeC, callCounter, receivedIndex, reasonHash[:], receivedMac,
	)
}

// verifyChallengeAndRespond is the shared tail of the single-shot and the
// streaming paths, the way MuunCardV3.verifyChallengeAndRespond is. Chunking
// is a transport decision, so what the card verifies, answers and commits
// cannot depend on how the reason arrived.
func (m *MockMuunCardV3) verifyChallengeAndRespond(
	challengeC []byte,
	callCounter uint32,
	receivedIndex uint16,
	reasonHash []byte,
	receivedMac []byte,
) *app_provided_data.NfcBridgeResponse {
	// The card keeps one pairing, so an index other than the live one names
	// a pairing it does not have. Ahead of the failure budget: a challenge
	// for a pairing that was never made must not spend an attempt of the
	// one that was.
	if receivedIndex != m.pairingIndex {
		return newErrorResponse(SWMuuncardV3SlotNotPaired)
	}

	// Ahead of the MAC, so a replayed request never reaches the HMAC.
	// Repeating one call counter would otherwise hand an attacker any number
	// of traces of the same computation over the same input, which is what a
	// differential side-channel attack needs.
	if callCounter <= m.lastCallCount {
		return newErrorResponse(swMuuncardV3InvalidCounter)
	}

	if m.failureCounter >= maxFailuresV3 {
		return newErrorResponse(swMuuncardV3TooManyFailures)
	}

	// Persisted before the HMAC below runs: an attacker who cuts the field
	// mid-verification still spends the attempt.
	m.failureCounter++

	macInput := buildChallengeMacInputV3(
		challengeC, callCounter, receivedIndex, reasonHash,
	)

	matchedSlot, ok := m.matchChallengeMac(macInput, receivedMac)
	if !ok {
		return newErrorResponse(swMuuncardV3InvalidMac)
	}

	// The card promotes in RAM, builds the response, and only then commits
	// the whole transition at once. Splitting the promotion from the ratchet
	// would let a tear-off between them leave conf_secret == secret_next with
	// the budget cleared. A mock cannot tear, but the two implementations
	// stay legible side by side.
	answer, err := m.generateChallengeAnswer(
		challengeC, reasonHash, m.secretFor(matchedSlot),
	)
	if err != nil {
		return newErrorResponse(swMuuncardV3CryptoError)
	}
	m.commitChallenge(matchedSlot, callCounter, answer.nextSecret)

	m.metadataApplet.incrementChallengeCounter()
	return newOkResponse(answer.response)
}

// generateChallengeAnswer builds the card's answer and the secret it will
// ratchet to, without touching stored state. matchedSecret is the secret the
// request MAC verified under, the one the ratchet starts from.
func (m *MockMuunCardV3) generateChallengeAnswer(
	challengeC []byte,
	reasonHash []byte,
	matchedSecret []byte,
) (*challengeAnswer, error) {
	ephemeral, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		return nil, err
	}

	sharedSecret, err := cryptography.ECDH(ephemeral.PrivateKey, challengeC)
	if err != nil {
		return nil, err
	}

	ikm := append([]byte{}, tagChallengeRatchet...)
	ikm = append(ikm, sharedSecret...)
	nextSecret := ComputeHMACSHA256(matchedSecret, ikm)

	responseKey := matchedSecret
	if m.generation.firmware().responseMACUnderNextSecret {
		responseKey = nextSecret
	}

	macInput := append([]byte{}, tagChallengeResp...)
	macInput = append(macInput, challengeC...)
	macInput = append(macInput, ephemeral.PublicKey...)
	responseMac := ComputeHMACSHA256(responseKey, macInput)

	toBeSigned := append([]byte{}, tagCardApproval...)
	toBeSigned = append(toBeSigned, challengeC...)
	toBeSigned = append(toBeSigned, reasonHash...)

	commitmentSig, err := signWithCommitmentKeyV3(
		m.metadataApplet.getCommitmentKeyBytes(), toBeSigned,
	)
	if err != nil {
		return nil, err
	}

	// P(65) || MAC(32) || sig_len(2) || commitment_sig
	response := append([]byte{}, ephemeral.PublicKey...)
	response = append(response, responseMac...)
	response = binary.BigEndian.AppendUint16(response, uint16(len(commitmentSig)))
	response = append(response, commitmentSig...)

	return &challengeAnswer{response: response, nextSecret: nextSecret}, nil
}

func (m *MockMuunCardV3) commitChallenge(
	matchedSlot secretSlot, callCounter uint32, nextSecret []byte,
) {
	if matchedSlot == proposedSecret {
		copy(m.confSecret[:], m.secretNext[:])
		// Only a MAC under secret_next proves the server received the
		// previous response, so only that clears the budget.
		m.failureCounter = 0
	}
	m.lastCallCount = callCounter
	copy(m.secretNext[:], nextSecret)
}

func (m *MockMuunCardV3) secretFor(slot secretSlot) []byte {
	if slot == proposedSecret {
		return m.secretNext[:]
	}
	return m.confSecret[:]
}

func (m *MockMuunCardV3) verifyChallengeMac(
	macInput, receivedMac []byte, slot secretSlot,
) bool {
	return constantTimeCompare(ComputeHMACSHA256(m.secretFor(slot), macInput), receivedMac)
}

// startStreaming takes the header and the first chunk of a reason too long
// for one APDU, and waits for the rest.
func (m *MockMuunCardV3) startStreaming(
	data []byte, declaredLength int,
) *app_provided_data.NfcBridgeResponse {
	macOffset := len(data) - MACSize
	firstChunkLength := macOffset - SignChallengeHeaderSizeV3

	// A first chunk carrying the whole reason is not a stream. An extended
	// APDU can hold more than the single path accepts, so without this the
	// card would wait for bytes that never come.
	if firstChunkLength < 0 || firstChunkLength >= declaredLength {
		return newErrorResponse(SWMuuncardV3WrongLength)
	}

	m.streamingChallengeC = append([]byte{}, data[:Secp256r1PointSize]...)
	m.streamingCallCounter = binary.BigEndian.Uint32(data[Secp256r1PointSize:])
	m.streamingIndex = binary.BigEndian.Uint16(
		data[Secp256r1PointSize+CallCounterSizeV3:],
	)
	m.streamingReasonLength = declaredLength
	m.streamingMac = append([]byte{}, data[macOffset:]...)

	if firstChunkLength > 0 {
		copy(m.accumulatedReason[:firstChunkLength], data[SignChallengeHeaderSizeV3:macOffset])
	}
	m.accumulatedLength = firstChunkLength

	m.streamingActive = true
	return &app_provided_data.NfcBridgeResponse{
		Response:   []byte{},
		StatusCode: swMuuncardV3MoreDataNeeded,
	}
}

func (m *MockMuunCardV3) handleContinueChallenge(
	apdu []byte,
) *app_provided_data.NfcBridgeResponse {
	data, errorResponse := m.validateAPDUData(apdu)
	if errorResponse != nil {
		return errorResponse
	}

	if !m.streamingActive {
		return newErrorResponse(swMuuncardV3StreamingNotStarted)
	}
	if len(data) < 1 {
		return newErrorResponse(SWMuuncardV3WrongLength)
	}
	if m.accumulatedLength+len(data) > m.streamingReasonLength {
		m.resetStreaming()
		return newErrorResponse(SWMuuncardV3WrongLength)
	}

	copy(m.accumulatedReason[m.accumulatedLength:], data)
	m.accumulatedLength += len(data)

	// The declared total says when the reason is complete, so a stream ends
	// where it said it would rather than where a flag claims.
	if m.accumulatedLength < m.streamingReasonLength {
		return &app_provided_data.NfcBridgeResponse{
			Response:   []byte{},
			StatusCode: swMuuncardV3MoreDataNeeded,
		}
	}

	response := m.completeStreaming()
	m.resetStreaming()
	return response
}

// completeStreaming hashes the reason its chunks assembled and answers
// through the shared tail.
func (m *MockMuunCardV3) completeStreaming() *app_provided_data.NfcBridgeResponse {
	reasonHash := sha256.Sum256(m.accumulatedReason[:m.accumulatedLength])

	return m.verifyChallengeAndRespond(
		m.streamingChallengeC,
		m.streamingCallCounter,
		m.streamingIndex,
		reasonHash[:],
		m.streamingMac,
	)
}

// forgetPairing clears what a newly installed MuunCardV3 starts without: the
// pairing and the challenge state that hangs off it.
func (m *MockMuunCardV3) forgetPairing() {
	m.paired = false
	m.pairingIndex = 0
	m.confSecret = [32]byte{}
	m.secretNext = [32]byte{}
	m.lastCallCount = 0
	m.failureCounter = 0
	m.resetStreaming()
}

func (m *MockMuunCardV3) resetStreaming() {
	m.streamingActive = false
	m.streamingCallCounter = 0
	m.streamingIndex = 0
	m.streamingReasonLength = 0
	m.streamingChallengeC = nil
	m.streamingMac = nil
	m.accumulatedLength = 0
	for i := range m.accumulatedReason {
		m.accumulatedReason[i] = 0
	}
}

func newOkResponse(response []byte) *app_provided_data.NfcBridgeResponse {
	return &app_provided_data.NfcBridgeResponse{
		Response:   response,
		StatusCode: ResponseOK,
	}
}

// secretSlot names the two secrets a challenge can authenticate under: the one
// the card confirmed, and the one it proposed in its last response. Which of
// them matched decides the rest of the flow, so it travels as a value rather
// than as a flag.
type secretSlot byte

const (
	confirmedSecret secretSlot = iota
	proposedSecret
)

// challengeAnswer is what a challenge produces before anything is committed:
// the bytes that go back to the client and the secret the card ratchets to.
type challengeAnswer struct {
	response   []byte
	nextSecret []byte
}

func buildChallengeMacInputV3(
	challengeC []byte,
	callCounter uint32,
	index uint16,
	reasonHash []byte,
) []byte {
	input := make([]byte, 0,
		len(tagChallengeReq)+Secp256r1PointSize+CallCounterSizeV3+PairIndexSize+sha256.Size)
	input = append(input, tagChallengeReq...)
	input = append(input, challengeC...)
	input = binary.BigEndian.AppendUint32(input, callCounter)
	input = binary.BigEndian.AppendUint16(input, index)
	return append(input, reasonHash...)
}

// signWithCommitmentKeyV3 signs SHA-256 of the message, which is what
// ALG_ECDSA_SHA_256 does on the card.
func signWithCommitmentKeyV3(privateKey, message []byte) ([]byte, error) {
	key, err := parsePrivateKeyIntoEcdsaModel(privateKey)
	if err != nil {
		return nil, errors.Errorf("commitment key: %w", err)
	}

	hash := sha256.Sum256(message)
	return ecdsa.SignASN1(rand.Reader, key, hash[:])
}

// parsePrivateKeyIntoEcdsaModel builds an ecdsa.PrivateKey from a raw
// 32-byte scalar.
//
// The public half has to be filled in too: ecdsa.PrivateKey models the whole
// keypair, and signing reads X and Y from it.
func parsePrivateKeyIntoEcdsaModel(privateKey []byte) (*ecdsa.PrivateKey, error) {
	publicKey, err := cryptography.GenerateSecp256r1PublicKey(privateKey)
	if err != nil {
		return nil, errors.Errorf("derive public key: %w", err)
	}

	return &ecdsa.PrivateKey{
		PublicKey: parsePublicKeyIntoEcdsaModel(publicKey),
		D:         new(big.Int).SetBytes(privateKey),
	}, nil
}

func parsePublicKeyIntoEcdsaModel(publicKey []byte) ecdsa.PublicKey {
	// Parse the public key (65 bytes: 0x04 + 32-byte X + 32-byte Y)
	x := new(big.Int).SetBytes(publicKey[1:33])
	y := new(big.Int).SetBytes(publicKey[33:65])

	return ecdsa.PublicKey{
		Curve: elliptic.P256(),
		X:     x,
		Y:     y,
	}
}

// constantTimeCompare performs constant-time comparison of two byte slices
func constantTimeCompare(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}

	result := byte(0)
	for i := range len(a) {
		result |= a[i] ^ b[i]
	}
	return result == 0
}
