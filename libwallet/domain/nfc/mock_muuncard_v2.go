package nfc

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/asn1"
	"encoding/hex"
	"fmt"
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/cryptography"
	"math/big"
)

type MockMuunCardV2 struct {
	// Global card state
	globalPrivateKey []byte // 32 bytes
	globalPublicKey  []byte // 65 bytes
	metadata         *Metadata

	// Pairing slots (8 slots max)
	pairingSlotsBitmap byte // bitmap: each bit = slot status
	pairingSlots       map[uint16]*PairingData
}

// Enforce we implement the interface
var _ JavaCardApplet = (*MockMuunCardV2)(nil)

type Metadata struct {
	cardVendor      [2]byte // 2 bytes - Vendor name
	cardModel       [2]byte // 2 bytes - Model name
	firmwareVersion [2]byte // 2 bytes - Firmware version
	usageCount      uint16  // 2 bytes - Number of operations performed
	languageCode    [2]byte // 2 bytes - Language preference
}

type PairingData struct {
	ClientPublicKey  []byte
	SharedSecret     [32]byte
	SharedSecretNext [32]byte
	Counter          uint16
}

func NewMockMuunCardV2() (*MockMuunCardV2, error) {
	// Generate global key pair for the card
	privateKey, err := cryptography.GenerateSecp256r1PrivateKey()
	if err != nil {
		return nil, err
	}

	publicKey, err := cryptography.GenerateSecp256r1PublicKey(privateKey)
	if err != nil {
		return nil, err
	}

	// Initialize card metadata with default values
	metadata := &Metadata{
		cardVendor:      [2]byte{0x4D, 0x55}, // "MU" for Muun
		cardModel:       [2]byte{0x56, 0x32}, // "V2" for Version 2
		firmwareVersion: [2]byte{0x02, 0x00}, // Version 2.0
		usageCount:      0,
		languageCode:    [2]byte{0x65, 0x6E}, // "en" for English
	}

	return &MockMuunCardV2{
		globalPrivateKey: privateKey,
		globalPublicKey:  publicKey,
		metadata:         metadata,
		pairingSlots:     make(map[uint16]*PairingData),
	}, nil
}

func (c *MockMuunCardV2) getAppletId() string {
	return MuuncardV2AppletId
}

func (c *MockMuunCardV2) processCommand(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {
	ins := apdu[iso7816OffsetIns]

	fmt.Printf("MockMuunCardV2 command apdu %s\n", hex.EncodeToString(apdu))

	switch ins {
	case insMuuncardV2GetVersion:
		return c.handleGetVersion(), nil
	case insMuuncardV2GetMetadata:
		return c.handleGetMetadata(), nil
	case insMuuncardV2Setup:
		return c.handlePairCard(apdu)
	case insMuuncardV2SignChallenge:
		return c.handleSignChallenge(apdu)
	case insMuuncardV2ContinueChallenge:
		return c.handleContinueChallenge(apdu)

	default:
		return newErrorResponse(swInsNotSupported), nil
	}
}

func (c *MockMuunCardV2) handleGetVersion() *app_provided_data.NfcBridgeResponse {
	// Return: vendor(6) + major(1) + minor(1) = 8 bytes total
	response := make([]byte, 8)

	// Vendor string
	vendor := []byte{'M', 'u', 'u', 'n', 'V', '2'}
	copy(response[0:6], vendor)

	// Version (major/minor)
	response[6] = 2
	response[7] = 0

	return newSuccessResponse(response)
}

func (c *MockMuunCardV2) handleGetMetadata() *app_provided_data.NfcBridgeResponse {
	// Build metadata (75 bytes)
	metadata := c.buildMetadata()

	return newSuccessResponse(metadata)
}

func (c *MockMuunCardV2) handlePairCard(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {
	// This should:
	// 1. Extract serverRandomPublicKey and clientPublicKey from APDU data
	// 2. Generate ephemeral key pair for this pairing session
	// 3. Compute shared secret using ECDH
	// 4. Generate pairing slot identifier
	// 5. Create MAC for authentication
	// 6. Sign metadata with global private key
	// 7. Return PairingResponse format: P || index || metadata || mac || signature

	data, errResp := c.validateAPDULength(apdu)
	if errResp != nil {
		return errResp, nil
	}

	// Expected: C(65) + pub_client(65) = 130 bytes
	if len(data) != 130 {
		return newErrorResponse(swMuuncardV2WrongLength), nil
	}
	serverPubKey := data[0:65]   // C
	clientPubKey := data[65:130] // pub_client

	// Validate public key formats
	err := cryptography.ValidateSecp256r1PublicKey(serverPubKey)
	if err != nil {
		return newErrorResponse(swMuuncardV2InvalidPubKey), nil
	}

	err = cryptography.ValidateSecp256r1PublicKey(clientPubKey)
	if err != nil {
		return newErrorResponse(swMuuncardV2InvalidPubKey), nil
	}

	// Find available slot
	slot := c.findAvailablePairingSlot()
	if slot == -1 {
		return newErrorResponse(swMuuncardV2NoSlotsAvailable), nil

	}

	// Generate ephemeral keypair [p], [P]
	ephemeralPrivate, err := cryptography.GenerateSecp256r1PrivateKey()
	if err != nil {
		return newErrorResponse(swMuuncardV2CryptoError), nil

	}
	ephemeralPublic, err := cryptography.GenerateSecp256r1PublicKey(ephemeralPrivate)
	if err != nil {
		return nil, err
	}

	// Perform ECDH: shared_x = x-coordinate of (p * C)
	sharedPoint, err := cryptography.ECDH(ephemeralPrivate, serverPubKey)

	if err != nil {
		return newErrorResponse(swMuuncardV2CryptoError), nil
	}

	// Derive secret_card = SHA256(shared_x)
	secretCard := sha256.Sum256(sharedPoint)

	// Store PairingData for this slot
	// copy apdu array slice
	clientKeyCopy := make([]byte, len(clientPubKey))
	copy(clientKeyCopy, clientPubKey)

	c.pairingSlots[uint16(slot)] = &PairingData{
		ClientPublicKey: clientKeyCopy,
		SharedSecret:    secretCard,
	}

	// Build metadata (75 bytes)
	metadata := c.buildMetadata()

	// Compute MAC. mac = hmac(secret_card,  C || P || index || metadata || pub_client)
	mac := c.computePairingMac(
		secretCard,
		serverPubKey,
		ephemeralPublic,
		slot,
		metadata,
		clientPubKey,
	)

	// Sign MAC with global private key
	signature, err := c.signWithGlobalKey(mac)
	if err != nil {
		return newErrorResponse(swMuuncardV2CryptoError), nil

	}

	// Build final response: P(65) || index(2) || metadata(75) || mac(32) || signature
	response := make([]byte, 0, 65+2+75+32+len(signature))
	response = append(response, ephemeralPublic...)             // P (65 bytes)
	response = append(response, byte(slot>>8), byte(slot&0xFF)) // index (2 bytes)
	response = append(response, metadata...)                    // metadata (75 bytes)
	response = append(response, mac...)                         // mac (32 bytes)
	response = append(response, signature...)                   // signature (variable)

	// Mark slot as used
	c.pairingSlotsBitmap |= 1 << slot

	fmt.Printf("MockMuunCardV2 pair response %s\n", hex.EncodeToString(response[:]))

	return newSuccessResponse(response), nil
}

// Input: [C] || [count_card] || [index] || [has_more_chunks] || [reason] || [mac]
// Output: [P] || [response_mac] (97 bytes)
func (c *MockMuunCardV2) handleSignChallenge(apdu []byte) (
	*app_provided_data.NfcBridgeResponse,
	error,
) {
	// This should:
	// 1. Extract challenge parameters from APDU data
	// 2. Validate pairing slot and counter
	// 3. Verify MAC using shared secret
	// 4. Generate ephemeral key pair for response
	// 5. Sign the challenge with appropriate private key
	// 6. Return ChallengeResponse format: P || mac

	data, errResp := c.validateAPDULength(apdu)
	if errResp != nil {
		return errResp, nil
	}

	// Minimum: C(65) + count(2) + index(2) + has_more_chunks(1) + mac(32) = 102 bytes
	if len(data) < 102 {
		return newErrorResponse(swMuuncardV2WrongLength), nil
	}

	// Parse has_more_chunks flag - after C(65) + count(2) + index(2)
	hasMoreChunksOffset := 65 + 2 + 2
	hasMoreChunks := data[hasMoreChunksOffset]

	if hasMoreChunks == 0 {
		return c.signChallengeSingle(data)
	} else if hasMoreChunks == 1 {
		return c.startStreamingFromSignChallenge(data)
	} else {
		return newErrorResponse(swWrongData), nil
	}
}

// signChallengeSingle processes single-chunk challenge (reason fits in one APDU)
// Input: [C] || [count_card] || [index] || [has_more_chunks] || [reason] || [mac]
// Output: [P] || [response_mac] (97 bytes)
func (c *MockMuunCardV2) signChallengeSingle(data []byte) (
	*app_provided_data.NfcBridgeResponse,
	error,
) {

	offset := 0

	// Extract challenge public key [C] (65 bytes)
	challengeC := data[offset : offset+65]
	err := cryptography.ValidateSecp256r1PublicKey(challengeC)
	if err != nil {
		return newErrorResponse(swMuuncardV2InvalidPubKey), nil

	}
	offset += 65

	// Extract counter (2 bytes, big-endian)
	receivedCounter := uint16(data[offset])<<8 | uint16(data[offset+1])
	offset += 2

	// Extract index (2 bytes, big-endian)
	receivedIndex := uint16(data[offset])<<8 | uint16(data[offset+1])
	offset += 2

	// Skip has_more_chunks flag (should be 0 for single chunk)
	offset += 1

	// MAC is at the end (32 bytes)
	macOffset := len(data) - 32
	reason := data[offset:macOffset]
	mac := data[macOffset:]

	// Find slot by index
	slot := receivedIndex

	if c.pairingSlots[slot] == nil {
		return newErrorResponse(swMuuncardV2SlotNotPaired), nil
	}

	// Verify counter increment
	currentCounter := c.pairingSlots[slot].Counter
	if receivedCounter <= currentCounter {
		return newErrorResponse(swMuuncardV2InvalidCounter), nil
	}

	macValid := c.verifyChallengeMac(slot, challengeC, receivedCounter, receivedIndex, reason, mac)
	if !macValid {
		return newErrorResponse(swMuuncardV2InvalidMac), nil
	}

	// Update counter
	c.pairingSlots[slot].Counter = receivedCounter

	// Generate response
	return c.generateChallengeResponse(slot, challengeC)
}

func (c *MockMuunCardV2) startStreamingFromSignChallenge(data []byte) (
	*app_provided_data.NfcBridgeResponse,
	error,
) {
	// TODO implement
	return newErrorResponse(swInsNotSupported), nil
}

func (c *MockMuunCardV2) handleContinueChallenge(apdu []byte) (
	*app_provided_data.NfcBridgeResponse,
	error,
) {
	// TODO implement
	return newErrorResponse(swInsNotSupported), nil
}

func (c *MockMuunCardV2) buildMetadata() []byte {
	// Build metadata structure:
	// global_pub_card(65) + vendor(2) + model(2) + firmware(2) + usage_count(2) + language(2)
	metadata := make([]byte, 75)
	offset := 0

	// Global public key (65 bytes)
	copy(metadata[offset:], c.globalPublicKey)
	offset += 65

	// Card vendor (2 bytes)
	copy(metadata[offset:], c.metadata.cardVendor[:])
	offset += 2

	// Card model (2 bytes)
	copy(metadata[offset:], c.metadata.cardModel[:])
	offset += 2

	// Firmware version (2 bytes)
	copy(metadata[offset:], c.metadata.firmwareVersion[:])
	offset += 2

	// Usage count (2 bytes, big-endian)
	metadata[offset] = byte(c.metadata.usageCount >> 8)
	metadata[offset+1] = byte(c.metadata.usageCount & 0xFF)
	offset += 2

	// Language code (2 bytes)
	copy(metadata[offset:], c.metadata.languageCode[:])

	return metadata
}

func (c *MockMuunCardV2) findAvailablePairingSlot() int {
	foundSlot := -1
	foundFlag := byte(0) // 0 = not found, 0xFF = found

	// Examine all slots in constant time
	for i := 0; i < 8; i++ {
		mask := byte(1 << i)
		slotFree := byte(0)
		if (c.pairingSlotsBitmap & mask) == 0 {
			slotFree = 0xFF
		}

		notFoundYet := ^foundFlag
		selectThis := slotFree & notFoundYet

		if selectThis != 0 {
			foundSlot = i
		}

		// Update found flag - once set to 0xFF, stays 0xFF
		foundFlag = foundFlag | selectThis
	}

	return foundSlot
}

func (c *MockMuunCardV2) computePairingMac(
	secretCard [32]byte,
	serverPubKey []byte,
	ephemeralPublic []byte,
	slot int,
	metadata []byte,
	clientPubKey []byte,
) []byte {

	// Build MAC input: C || P || index || metadata || pub_client
	macInput := make([]byte, 0, 65+65+2+75+65)
	macInput = append(macInput, serverPubKey...)    // C (65 bytes)
	macInput = append(macInput, ephemeralPublic...) // P (65 bytes)

	// index (2 bytes, big-endian)
	macInput = append(macInput, byte(slot>>8), byte(slot&0xFF))

	macInput = append(macInput, metadata...)     // metadata (75 bytes)
	macInput = append(macInput, clientPubKey...) // pub_client (65 bytes)

	// Compute MAC using first 16 bytes of secret_card
	macSecretCard := secretCard[:16]
	return ComputeHMACSHA256(macSecretCard, macInput)
}

func (c *MockMuunCardV2) signWithGlobalKey(data []byte) ([]byte, error) {
	hash := sha256.Sum256(data)

	fmt.Printf("MockMuunCardV2 mac hash %s\n", hex.EncodeToString(hash[:]))

	// Create ECDSA private key. Sadly, PublicKey also needs to be initialized here. Apparently,
	// ecdsa.PrivateKey represents the whole keypair and ecdsa.Sign() uses X and Y from Public key.
	privKeyBig := new(big.Int).SetBytes(c.globalPrivateKey)
	privKey := &ecdsa.PrivateKey{
		PublicKey: parsePublicKeyIntoEcdsaModel(c.globalPublicKey),
		D:         privKeyBig,
	}

	r, s, err := ecdsa.Sign(rand.Reader, privKey, hash[:])
	if err != nil {
		return nil, err
	}

	// Convert to DER format
	return encodeDERSignature(r, s), nil
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

// validateAPDULength validates APDU structure and returns the data portion
func (c *MockMuunCardV2) validateAPDULength(apdu []byte) (
	[]byte,
	*app_provided_data.NfcBridgeResponse,
) {

	if len(apdu) < 5 {
		return nil, newErrorResponse(swMuuncardV2WrongLength)
	}

	dataLength := int(apdu[4])
	if len(apdu) != 5+dataLength {
		return nil, newErrorResponse(swMuuncardV2WrongLength)
	}

	return apdu[5:], nil
}

func encodeDERSignature(r, s *big.Int) []byte {

	// Either this or an anonymous struct
	type ecdsaSignature struct {
		R, S *big.Int
	}

	sig := ecdsaSignature{R: r, S: s}
	der, _ := asn1.Marshal(sig)
	return der
}

func (c *MockMuunCardV2) verifyChallengeMac(
	slot uint16,
	challengeC []byte,
	receivedCounter uint16,
	receivedIndex uint16,
	reason []byte,
	mac []byte,
) bool {

	// Build MAC input: C || counter || index || reason
	macInput := buildChallengeMacInput(challengeC, receivedCounter, receivedIndex, reason)

	// Try to verify MAC with current secret first
	secret := c.pairingSlots[slot].SharedSecret[:]
	macValid := c.verifyChallengeMacForInput(secret, macInput, mac)

	if !macValid {
		// Try with secret_next (fallback for forward secrecy)
		secret = c.pairingSlots[slot].SharedSecretNext[:]
		macValid = c.verifyChallengeMacForInput(secret, macInput, mac)
		if macValid {
			// Update secret_card = secret_next since client is using next secret
			copy(c.pairingSlots[slot].SharedSecret[:], c.pairingSlots[slot].SharedSecretNext[:])
		}
	}
	return macValid
}

// buildChallengeMacInput builds MAC input for challenge verification
// Format: C || counter || index || payload
func buildChallengeMacInput(challengeC []byte, counter, index uint16, payload []byte) []byte {
	macInput := make([]byte, 0, 65+2+2+len(payload))
	macInput = append(macInput, challengeC...)                        // C (65 bytes)
	macInput = append(macInput, byte(counter>>8), byte(counter&0xFF)) // counter (2 bytes)
	macInput = append(macInput, byte(index>>8), byte(index&0xFF))     // index (2 bytes)
	macInput = append(macInput, payload...)                           // payload (reason or reason_hash)
	return macInput
}

// verifyChallengeMac verifies challenge MAC with given input
func (c *MockMuunCardV2) verifyChallengeMacForInput(secret, macInput, mac []byte) bool {
	// Compute expected MAC using first 16 bytes of secret
	macSecretCard := secret[:16]
	expectedMAC := ComputeHMACSHA256(macSecretCard, macInput)

	// Constant-time MAC comparison
	return constantTimeCompare(expectedMAC, mac)
}

// constantTimeCompare performs constant-time comparison of two byte slices
func constantTimeCompare(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}

	result := byte(0)
	for i := 0; i < len(a); i++ {
		result |= a[i] ^ b[i]
	}
	return result == 0
}

// generateChallengeResponse generates unified challenge response for both single and streaming modes
func (c *MockMuunCardV2) generateChallengeResponse(slot uint16, challengeC []byte) (
	*app_provided_data.NfcBridgeResponse,
	error,
) {
	// Generate ephemeral keypair for response: ephemeralPrivate [p], ephemeralPublic [P]
	ephemeralPrivate, err := cryptography.GenerateSecp256r1PrivateKey()
	if err != nil {
		return newErrorResponse(swMuuncardV2CryptoError), nil

	}
	ephemeralPublic, err := cryptography.GenerateSecp256r1PublicKey(ephemeralPrivate)
	if err != nil {
		return nil, fmt.Errorf("failed to generate ephemeral public key: %v", err)
	}

	// Perform ECDH: shared_point = p * C
	sharedPoint, err := cryptography.ECDH(ephemeralPrivate, challengeC)
	if err != nil {
		return newErrorResponse(swMuuncardV2CryptoError), nil
	}

	// Store secret_next = HMAC(secret_card, shared_point)
	hmacKey := c.pairingSlots[slot].SharedSecret[:] // Use full secret_card as key
	secretNext := ComputeHMACSHA256(hmacKey, sharedPoint)
	copy(c.pairingSlots[slot].SharedSecretNext[:], secretNext)

	// Compute response MAC = HMAC(mac_secret_card, C || P)
	macInput := make([]byte, 0, 65+65)
	macInput = append(macInput, challengeC...)      // [C]
	macInput = append(macInput, ephemeralPublic...) // [P]

	macSecretCard := c.pairingSlots[slot].SharedSecret[:16] // only first 16 bytes of secret_card
	responseMAC := ComputeHMACSHA256(macSecretCard, macInput)

	// Build response: [P] (65) || MAC (32)
	response := make([]byte, 97)
	copy(response[0:65], ephemeralPublic)
	copy(response[65:97], responseMAC)

	return newSuccessResponse(response), nil
}
