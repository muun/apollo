package simulator

import (
	"crypto/ecdh"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"log/slog"
	"math/big"
	"time"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/base/domain/cryptography"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/subsystems/security_card" //nolint:depguard // reuses the card protocol helpers the simulator verifies against
)

type RandomPrivateKeyMetadata struct {
	privateKey *ecdh.PrivateKey
	timeStamp  time.Time
	// reasonHash is what the card signs over, so the submit has to check
	// the signature against the reason this challenge issued rather than
	// anything the client hands back.
	reasonHash []byte
}

// keySecurityCardShortDeviceIDInHex holds the short device id of the paired
// card, as its certificate attests it.
const keySecurityCardShortDeviceIDInHex = "securityCardShortDeviceIdInHex"

type SimulatedHoustonService struct {
	keyValueStorage              *storage.KeyValueStorage
	userPublicKeyProvider        UserPublicKeyProvider
	lastRandomPrivateKeyMetadata *RandomPrivateKeyMetadata
	secretCardBytes              [32]byte
	callCounter                  uint32
	pairingSlot                  uint16
	commitmentPub                []byte
	shortDeviceID                []byte
}

var _ houston.HoustonService = (*SimulatedHoustonService)(nil)

const challengeTimeoutInSeconds = 90

// NewSimulatedHoustonService builds the simulator over the given storage. The user
// public key provider is only read to verify a V3 client approval, so a simulator
// that never sees one can take nil.
func NewSimulatedHoustonService(
	storage *storage.KeyValueStorage,
	userPublicKeyProvider UserPublicKeyProvider,
) *SimulatedHoustonService {
	return &SimulatedHoustonService{
		keyValueStorage:       storage,
		userPublicKeyProvider: userPublicKeyProvider,
	}
}

// PairingSecret returns the card secret recovered by the last successful pairing.
func (m *SimulatedHoustonService) PairingSecret() []byte {
	return m.secretCardBytes[:]
}

func (m *SimulatedHoustonService) HealthCheck() error {
	//TODO implement me
	panic("implement me")
}

func (m *SimulatedHoustonService) ChallengeKeySetupStart(
	_ model.ChallengeSetupJSON,
) (model.SetupChallengeResponseJSON, error) {
	//TODO implement me
	panic("implement me")
}

func (m *SimulatedHoustonService) ChallengeKeySetupFinish(_ model.ChallengeSetupVerifyJSON) error {
	//TODO implement me
	panic("implement me")
}

func (m *SimulatedHoustonService) ChallengeSetupFinishWithVerifiableCosignerKey(
	_ model.ChallengeSetupVerifyJSON,
) (model.VerifiableCosignerKeyJSON, error) {
	//TODO implement me
	panic("implement me")
}

func (m *SimulatedHoustonService) VerifiableCosignerKey() (model.VerifiableCosignerKeyJSON, error) {
	//TODO implement me
	panic("implement me")
}

func (m *SimulatedHoustonService) CreateFirstSession(
	_ model.CreateFirstSessionJSON,
) (model.CreateFirstSessionOkJSON, error) {
	//TODO implement me
	panic("implement me")
}

func (m *SimulatedHoustonService) FetchFeeWindow() (model.FeeWindowJSON, error) {
	//TODO implement me
	panic("implement me")
}

func (m *SimulatedHoustonService) SubmitDiagnosticsScanData(_ model.DiagnosticScanDataJSON) error {
	//TODO implement me
	panic("implement me")
}

func (m *SimulatedHoustonService) PairRequestChallenge() (
	model.PairRequestChallengeResponseJSON, error,
) {
	err := m.loadCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error loading houston data", err)
		return model.PairRequestChallengeResponseJSON{}, houstonError
	}

	randomPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error generating private key", err)
		return model.PairRequestChallengeResponseJSON{}, houstonError
	}

	m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
		privateKey: randomPrivateKey,
		timeStamp:  time.Now(),
	}

	// Persists the fresh challenge so the upcoming submit loads it.
	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return model.PairRequestChallengeResponseJSON{}, houstonError
	}

	randomPubKey := randomPrivateKey.PublicKey().Bytes()

	return model.PairRequestChallengeResponseJSON{
		ServerPubKeyInHex: hex.EncodeToString(randomPubKey),
	}, nil
}

// PairSubmitSignedChallenge completes the V3 pairing flow.
func (m *SimulatedHoustonService) PairSubmitSignedChallenge(
	req model.PairSubmitSignedChallengeJSON,
) (model.PairSubmitSignedChallengeResponseJSON, error) {
	err := m.loadCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error loading houston data", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	if m.lastRandomPrivateKeyMetadata == nil {
		return model.PairSubmitSignedChallengeResponseJSON{}, &houston.HoustonResponseError{
			DeveloperMessage: "no pending pair challenge",
			ErrorCode:        houston.ErrChallengeExpired,
			Message:          "no pending pair challenge",
			RequestID:        0,
			Status:           houston.StatusClientFailure,
		}
	}

	// Validates the challenge timeout (90s since PairRequestChallenge).
	timeSinceLastChallenge := time.Since(m.lastRandomPrivateKeyMetadata.timeStamp).Seconds()
	if timeSinceLastChallenge > challengeTimeoutInSeconds {
		return model.PairSubmitSignedChallengeResponseJSON{}, &houston.HoustonResponseError{
			DeveloperMessage: "challenge has expired",
			ErrorCode:        houston.ErrChallengeExpired,
			Message:          "challenge has expired",
			RequestID:        0,
			Status:           houston.StatusClientFailure,
		}
	}

	// The request, decoded: the card's ephemeral key P, which has to lie on
	// secp256r1 before it goes into a DH, its MAC, and its metadata.
	cardPubKeyBytes, err := hex.DecodeString(req.CardPubKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding card pub key", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}
	if err := cryptography.ValidateSecp256r1PublicKey(cardPubKeyBytes); err != nil {
		houstonError := mapToInternalServerHoustonError("invalid card public key", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	receivedMacBytes, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding mac", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	metadataBytes, err := hex.DecodeString(req.MetadataInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding metadata", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	serverPubKeyBytes := m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes()

	// Reads the metadata with each generation, newest first, and keeps the
	// first whose reading the MAC verifies: a wrong reading yields another
	// authentication form or another key, so its MAC never does. Metadata no
	// generation can read never reaches a MAC, so it is refused as invalid
	// metadata, with each generation's reason.
	var pairedMetadata *metadataV3
	var secretCard []byte
	var readErrors []error
	for _, generation := range cardGenerationsV3 {
		metadata, err := generation.ParseMetadata(metadataBytes)
		if err != nil {
			readErrors = append(readErrors, errors.Errorf("%s: %w", generation.Name(), err))
			continue
		}

		secret, err := m.derivePairingSecretV3(metadata.KeyAgreementPub, cardPubKeyBytes)
		if err != nil {
			houstonError := mapToInternalServerHoustonError("error deriving pairing secret", err)
			return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
		}

		err = verifyPairingMACV3(
			secret,
			serverPubKeyBytes,
			cardPubKeyBytes,
			req.Index,
			metadata.MetadataForAuth,
			receivedMacBytes,
		)
		if err != nil {
			continue
		}

		pairedMetadata = metadata
		secretCard = secret
		break
	}
	if len(readErrors) == len(cardGenerationsV3) {
		houstonError := mapToInternalServerHoustonError(
			"invalid metadata", errors.Join(readErrors...),
		)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}
	if pairedMetadata == nil {
		return model.PairSubmitSignedChallengeResponseJSON{}, &houston.HoustonResponseError{
			DeveloperMessage: "v3 pairing MAC mismatch under every known card generation",
			ErrorCode:        houston.ErrInvalidMac,
			Message:          "invalid mac: the message data has been tampered with or corrupted.",
			RequestID:        0,
			Status:           houston.StatusClientFailure,
		}
	}

	// callCounter starts at 0 for this slot — it tracks per-slot sign
	// challenge usage for replay protection.
	m.callCounter = 0
	copy(m.secretCardBytes[:], secretCard)
	m.pairingSlot = req.Index
	m.commitmentPub = pairedMetadata.CommitmentPub
	m.shortDeviceID = pairedMetadata.ShortDeviceID

	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return model.PairSubmitSignedChallengeResponseJSON{}, houstonError
	}

	return model.PairSubmitSignedChallengeResponseJSON{
		SecurityCard: model.PairedSecurityCardJSON{
			ID:       1,
			Metadata: pairedMetadata.JSON,
			PairedAt: time.Now().UTC().Format(time.RFC3339),
		},
		IsKnownProvider:   true,
		IsCardAlreadyUsed: false,
	}, nil
}

// SignRequestChallenge issues a V3 sign challenge to approve a sensitive
// action (e.g. a new on-chain operation). The card MAC-verifies the
// challenge and emits a response after the user taps to approve.
func (m *SimulatedHoustonService) SignRequestChallenge(
	_ model.SignRequestChallengeJSON,
) (model.SignRequestChallengeResponseJSON, error) {

	// Loads the paired card state (secret_card, counter).
	err := m.loadCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error loading houston data", err)
		return model.SignRequestChallengeResponseJSON{}, houstonError
	}

	// Generates ephemeral (c, C), increments callCounter.
	randomPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error generating private key", err)
		return model.SignRequestChallengeResponseJSON{}, houstonError
	}

	// NOTE (Houston): issuing a new challenge should expire any open
	// challenge already outstanding for this wallet. The simulator holds a
	// single in-flight challenge, so overwriting lastRandomPrivateKeyMetadata
	// implicitly drops the previous one; real Houston must expire them
	// explicitly so stale challenges can't be replayed.
	m.callCounter++
	serverPubKeyBytes := randomPrivateKey.PublicKey().Bytes()

	// Empty reason: the card displays this text on its screen during
	// approval, so only screen-capable cards need it populated. The
	// simulator doesn't persist capabilities yet, so it defaults to the
	// non-screen behavior (empty).
	var reasonBytes []byte
	reasonHash := sha256.Sum256(reasonBytes)

	m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
		privateKey: randomPrivateKey,
		timeStamp:  time.Now(),
		reasonHash: reasonHash[:],
	}

	// index is the pairing slot persisted at pair time.
	// V3 firmware is single-slot today (so it is 0).
	macBytes := computeSignChallengeMACV3(
		m.secretCardBytes[:],
		serverPubKeyBytes,
		m.callCounter,
		m.pairingSlot,
		reasonBytes,
	)

	// Persists the advanced counter and new ephemeral so the upcoming
	// submit can verify the card's response.
	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return model.SignRequestChallengeResponseJSON{}, houstonError
	}

	return model.SignRequestChallengeResponseJSON{
		ServerPubKeyInHex: hex.EncodeToString(serverPubKeyBytes),
		ReasonInHex:       hex.EncodeToString(reasonBytes),
		// NOTE: the simulator keeps a single paired card: each pairing replaces the
		// previous one, so a challenge carries one payload. Houston keeps every
		// card a wallet paired and sends a payload for each (spec, "Allow using
		// multiple cards with the same wallet"). Whether the simulator should do the
		// same, or only the production Houston, is still undecided, so the simulator
		// leaves it out on purpose for now.
		PerCardPayloads: []model.SignChallengePerCardPayloadJSON{
			{
				ShortDeviceIDInHex: hex.EncodeToString(m.shortDeviceID),
				Index:              m.pairingSlot,
				MacInHex:           hex.EncodeToString(macBytes),
				CallCounter:        m.callCounter,
			},
		},
	}, nil
}

// SignSubmitSignedChallenge completes the V3 sign flow. The client
// forwards the card's response (P, MAC) from the sign challenge.
func (m *SimulatedHoustonService) SignSubmitSignedChallenge(
	req model.SignSubmitSignedChallengeJSON,
) error {
	// Loads the in-flight ephemeral c and the current secret_card.
	err := m.loadCardData()
	if err != nil {
		return mapToInternalServerHoustonError("error loading houston data", err)
	}

	if m.lastRandomPrivateKeyMetadata == nil {
		return &houston.HoustonResponseError{
			DeveloperMessage: "no pending sign challenge",
			ErrorCode:        houston.ErrChallengeExpired,
			Message:          "no pending sign challenge",
			RequestID:        0,
			Status:           houston.StatusClientFailure,
		}
	}

	// Validates the challenge timeout (90s since SignRequestChallenge).
	timeSinceLastChallenge := time.Since(m.lastRandomPrivateKeyMetadata.timeStamp).Seconds()
	if timeSinceLastChallenge > challengeTimeoutInSeconds {
		return &houston.HoustonResponseError{
			DeveloperMessage: "challenge has expired",
			ErrorCode:        houston.ErrChallengeExpired,
			Message:          "challenge has expired",
			RequestID:        0,
			Status:           houston.StatusClientFailure,
		}
	}

	// The response answers the challenge of the card the submit names. The
	// simulator keeps one card, so any other card has no challenge to answer.
	if req.ShortDeviceIDInHex != hex.EncodeToString(m.shortDeviceID) {
		return &houston.HoustonResponseError{
			DeveloperMessage: "no pending sign challenge for this card",
			ErrorCode:        houston.ErrChallengeExpired,
			Message:          "no pending sign challenge for this card",
			RequestID:        0,
			Status:           houston.StatusClientFailure,
		}
	}

	// Decodes and validates P on secp256r1.
	cardPubKeyBytes, err := hex.DecodeString(req.CardPubKeyInHex)
	if err != nil {
		return mapToInternalServerHoustonError("error decoding card pub key", err)
	}
	if err := cryptography.ValidateSecp256r1PublicKey(cardPubKeyBytes); err != nil {
		return mapToInternalServerHoustonError("invalid card public key", err)
	}

	// Derives the secret the card ratchets to ahead of the MAC, because a 3.1
	// card keys its response MAC with it.
	nextSecret, err := m.nextSecretV3(cardPubKeyBytes)
	if err != nil {
		return mapToInternalServerHoustonError("error deriving next secret", err)
	}

	// Verifies MAC = HMAC(key, "challenge-response"||C||P), with the key the
	// paired card's generation uses.
	receivedMacBytes, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		return mapToInternalServerHoustonError("error decoding mac", err)
	}

	serverPubKeyBytes := m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes()
	err = verifySignChallengeResponseMACV3(
		m.pairedCardGenerationV3().ChallengeResponseKey(m.secretCardBytes[:], nextSecret),
		serverPubKeyBytes,
		cardPubKeyBytes,
		receivedMacBytes,
	)
	if err != nil {
		return &houston.HoustonResponseError{
			DeveloperMessage: err.Error(),
			ErrorCode:        houston.ErrInvalidMac,
			Message:          "invalid mac: the message data has been tampered with or corrupted.",
			RequestID:        0,
			Status:           houston.StatusClientFailure,
		}
	}

	// The MAC only convinces whoever already shares the card's secret, and
	// the server is one of them, so it cannot stand as evidence the card
	// approved anything. This signature can: only the card holds the
	// commitment key.
	commitSigBytes, err := hex.DecodeString(req.CommitSigInHex)
	if err != nil {
		return mapToInternalServerHoustonError("error decoding commit sig", err)
	}
	err = m.verifyCardApprovalV3(
		serverPubKeyBytes,
		m.lastRandomPrivateKeyMetadata.reasonHash,
		commitSigBytes,
	)
	if err != nil {
		return &houston.HoustonResponseError{
			DeveloperMessage: err.Error(),
			ErrorCode:        houston.ErrInvalidSignature,
			Message:          "invalid card approval: the card did not approve this action.",
			RequestID:        0,
			Status:           houston.StatusClientFailure,
		}
	}

	// The card's approval says the card approved; the client's says this
	// wallet did. A response relayed by another wallet's client carries a
	// signature by another user key, so it does not verify here. Checked
	// before the ratchet, so a refused approval leaves the secret as it was.
	clientApprovalPublicKey, err := m.clientApprovalPublicKeyV3()
	if err != nil {
		return mapToInternalServerHoustonError("error deriving client approval key", err)
	}
	clientSigBytes, err := hex.DecodeString(req.ClientSigInHex)
	if err != nil {
		return mapToInternalServerHoustonError("error decoding client sig", err)
	}
	err = verifyClientApprovalV3(
		clientApprovalPublicKey,
		serverPubKeyBytes,
		m.lastRandomPrivateKeyMetadata.reasonHash,
		clientSigBytes,
	)
	if err != nil {
		return &houston.HoustonResponseError{
			DeveloperMessage: err.Error(),
			ErrorCode:        houston.ErrInvalidSignature,
			Message:          "invalid client approval: this wallet did not approve this action.",
			RequestID:        0,
			Status:           houston.StatusClientFailure,
		}
	}

	// The card ratchets to the same secret on its side, so the next sign
	// challenge uses a fresh secret on both ends.
	copy(m.secretCardBytes[:], nextSecret)

	// Persists the new secret_card.
	err = m.persistCardData()
	if err != nil {
		return mapToInternalServerHoustonError("error persisting houston data", err)
	}

	return nil
}

// derivePairingSecretV3 derives the secret a card and Houston share after a
// pairing, from the card's static key agreement key and its ephemeral key P:
//
//	DH1 = c · keyAgreementPub
//	DH2 = muunPriv · P
//	secret_card = HMAC("pairing-secret", DH1 || DH2)
//
// c is the challenge key Houston issued for this pairing, and muunPriv is
// the identity key the card binds the pairing to.
func (m *SimulatedHoustonService) derivePairingSecretV3(
	keyAgreementPub []byte,
	cardPubKey []byte,
) ([]byte, error) {
	serverPrivKeyBytes := m.lastRandomPrivateKeyMetadata.privateKey.Bytes()
	dh1, err := cryptography.ECDH(serverPrivKeyBytes, keyAgreementPub)
	if err != nil {
		return nil, errors.Errorf("DH1: %w", err)
	}
	dh2, err := cryptography.ECDH(cosignerPrivDevBytes, cardPubKey)
	if err != nil {
		return nil, errors.Errorf("DH2: %w", err)
	}

	ikm := make([]byte, 0, len(dh1)+len(dh2))
	ikm = append(ikm, dh1...)
	ikm = append(ikm, dh2...)
	return security_card.ComputeHMACSHA256([]byte("pairing-secret"), ikm), nil
}

// verifyPairingMACV3 verifies the V3 pair-response MAC:
//
//	MAC = HMAC(secret_card, "pairing-response" || C || P || index || metadata_for_auth)
//
// metadataForAuth is the authentication form of the metadata, not the
// transmission form that arrives on the wire: the card MACs the
// certificate's hash rather than the certificate. Each card generation
// derives it from its own layout.
//
// Uses hmac.Equal for constant-time comparison.
func verifyPairingMACV3(
	secretCard,
	serverPubKey,
	cardPubKey []byte,
	index uint16,
	metadataForAuth,
	receivedMac []byte,
) error {
	tag := []byte("pairing-response")
	macInput := make(
		[]byte,
		0,
		len(tag)+len(serverPubKey)+len(cardPubKey)+2+len(metadataForAuth),
	)
	macInput = append(macInput, tag...)
	macInput = append(macInput, serverPubKey...)
	macInput = append(macInput, cardPubKey...)
	macInput = binary.BigEndian.AppendUint16(macInput, index)
	macInput = append(macInput, metadataForAuth...)

	expectedMAC := security_card.ComputeHMACSHA256(secretCard, macInput)
	if !hmac.Equal(receivedMac, expectedMAC) {
		return errors.Errorf("v3 pairing MAC mismatch")
	}
	return nil
}

// computeSignChallengeMACV3 computes the V3 sign-request MAC:
//
//	MAC = HMAC(secret_card, "challenge-request" || C || call_counter || index || SHA256(reason))
//
// C is the server's ephemeral pub key, call_counter is the per-slot monotonic
// counter, index is the pairing slot, and reason is the human-readable
// action text that the card would display in screen-capable variants.
func computeSignChallengeMACV3(
	secretCard,
	serverPubKey []byte,
	callCounter uint32,
	index uint16,
	reason []byte,
) []byte {
	reasonHash := sha256.Sum256(reason)

	tag := []byte("challenge-request")
	macInput := make(
		[]byte,
		0,
		len(tag)+len(serverPubKey)+4+2+sha256.Size,
	)
	macInput = append(macInput, tag...)
	macInput = append(macInput, serverPubKey...)
	macInput = binary.BigEndian.AppendUint32(macInput, callCounter)
	macInput = binary.BigEndian.AppendUint16(macInput, index)
	macInput = append(macInput, reasonHash[:]...)
	return security_card.ComputeHMACSHA256(secretCard, macInput)
}

// ComputeSignChallengeMAC MACs a challenge the way SignRequestChallenge does.
// Tests that need a challenge carrying a reason build it here, since Houston
// does not generate the text yet.
func (m *SimulatedHoustonService) ComputeSignChallengeMAC(
	serverPubKey []byte,
	callCounter uint32,
	index uint16,
	reason []byte,
) []byte {
	return computeSignChallengeMACV3(
		m.secretCardBytes[:], serverPubKey, callCounter, index, reason,
	)
}

// VerifySignChallengeResponseMAC checks a card's response MAC to the pending
// sign challenge, the same check SignSubmitSignedChallenge makes: under the
// key the paired card's generation uses, which for 3.1 is the secret the
// ratchet derives from the challenge's private key and P. serverPubKey has
// to be the pending challenge's key.
func (m *SimulatedHoustonService) VerifySignChallengeResponseMAC(
	serverPubKey,
	cardPubKey,
	receivedMac []byte,
) error {
	nextSecret, err := m.nextSecretV3(cardPubKey)
	if err != nil {
		return err
	}

	return verifySignChallengeResponseMACV3(
		m.pairedCardGenerationV3().ChallengeResponseKey(m.secretCardBytes[:], nextSecret),
		serverPubKey,
		cardPubKey,
		receivedMac,
	)
}

// verifyCardApprovalV3 verifies the card's approval signature:
//
//	commit_sig = ECDSA(commitment_priv, "card-approval" || C || HASH(reason))
//
// Unlike the response MAC, this binds the reason: a server that swapped the
// action after the tap would hold a signature over a hash the card never saw.
func (m *SimulatedHoustonService) verifyCardApprovalV3(
	serverPubKey, reasonHash, commitSig []byte,
) error {
	if len(m.commitmentPub) == 0 {
		return errors.Errorf("no commitment key stored for the paired card")
	}

	tag := []byte("card-approval")
	toBeSigned := make([]byte, 0, len(tag)+len(serverPubKey)+len(reasonHash))
	toBeSigned = append(toBeSigned, tag...)
	toBeSigned = append(toBeSigned, serverPubKey...)
	toBeSigned = append(toBeSigned, reasonHash...)

	valid, err := m.VerifySignature(m.commitmentPub, toBeSigned, commitSig)
	if err != nil {
		return errors.Errorf("verify card approval signature: %w", err)
	}
	if !valid {
		return errors.Errorf("v3 card approval signature mismatch")
	}
	return nil
}

// clientApprovalPublicKeyV3 returns the key the wallet's client approves
// security card challenges with: the user's base public key derived at
// hd_keys.SecurityCardClientApprovalKeyPath, the way Houston derives it from
// the base public key it holds.
func (m *SimulatedHoustonService) clientApprovalPublicKeyV3() (*btcec.PublicKey, error) {
	if m.userPublicKeyProvider == nil {
		return nil, errors.New("the simulator was given no user public key provider")
	}
	userPublicKey, err := m.userPublicKeyProvider.UserPublicKey()
	if err != nil {
		return nil, errors.Errorf("load user public key: %w", err)
	}
	clientApprovalPublicKey, err := userPublicKey.DeriveTo(
		hd_keys.SecurityCardClientApprovalKeyPath,
	)
	if err != nil {
		return nil, err
	}
	return clientApprovalPublicKey.ECPubKey()
}

// pairedCardGenerationV3 returns the generation of the paired card, which
// decides the secret its challenge response MAC is keyed with.
//
// The simulator keeps no record of the generation a pairing was read with. It
// tells the generations apart by the short device id instead, which is a
// stand-in signal: 3.1 is the generation that introduced the short device
// id, so a 3.0 pairing stores none and a 3.1 pairing stores the one its
// certificate attests. That only works while every generation that reports
// a short device id keys the response MAC the way 3.1 does. A generation
// that breaks this has to record its name at pairing instead, which takes a
// new key in the KV migration plan.
//
// A pairing stored before the simulator kept the short device id has none and
// reads as 3.0, which is how every card paired then keys its response MAC.
func (m *SimulatedHoustonService) pairedCardGenerationV3() cardGenerationV3 {
	if len(m.shortDeviceID) == 0 {
		return cardGenerationV3_0{}
	}
	return cardGenerationV3_1{}
}

// nextSecretV3 returns the secret the paired card ratchets to when it
// answers the pending sign challenge with P:
//
//	next_secret = HMAC(secret_card, "challenge-ratchet" || DH), DH = ECDH(c, P)
//
// c is the challenge's private key. The card reaches the same DH from its
// ephemeral p and C.
func (m *SimulatedHoustonService) nextSecretV3(cardPubKey []byte) ([]byte, error) {
	if m.lastRandomPrivateKeyMetadata == nil {
		return nil, errors.Errorf("no pending sign challenge")
	}

	dh, err := cryptography.ECDH(m.lastRandomPrivateKeyMetadata.privateKey.Bytes(), cardPubKey)
	if err != nil {
		return nil, err
	}

	ratchetIKM := append([]byte("challenge-ratchet"), dh...)
	return security_card.ComputeHMACSHA256(m.secretCardBytes[:], ratchetIKM), nil
}

// verifyClientApprovalV3 verifies the client's approval signature:
//
//	client_sig = BIP340(client_priv, SHA256("client-approval" || C || HASH(reason)))
//
// It is the card's approval under the client's own tag, signed with Schnorr
// rather than ECDSA. C is the server's ephemeral pub key and reasonHash the
// hash of the reason this challenge issued, never one the client hands back.
func verifyClientApprovalV3(
	clientApprovalPublicKey *btcec.PublicKey,
	serverPubKey,
	reasonHash,
	clientSig []byte,
) error {
	signature, err := schnorr.ParseSignature(clientSig)
	if err != nil {
		return errors.Errorf("parse client approval signature: %w", err)
	}

	tag := []byte("client-approval")
	toBeSigned := make([]byte, 0, len(tag)+len(serverPubKey)+len(reasonHash))
	toBeSigned = append(toBeSigned, tag...)
	toBeSigned = append(toBeSigned, serverPubKey...)
	toBeSigned = append(toBeSigned, reasonHash...)
	digest := sha256.Sum256(toBeSigned)

	if !signature.Verify(digest[:], clientApprovalPublicKey) {
		return errors.New("v3 client approval signature mismatch")
	}
	return nil
}

// verifySignChallengeResponseMACV3 verifies the V3 sign-response MAC:
//
//	MAC = HMAC(responseKey, "challenge-response" || C || P)
//
// responseKey is the secret the card's generation keys the MAC with
// (cardGenerationV3.ChallengeResponseKey). C is the server's ephemeral pub
// key from the sign-request and P is the card's ephemeral pub key from its
// response.
// No counter binding on the response MAC — that's a deliberate spec choice;
// the request MAC already commits to the counter.
func verifySignChallengeResponseMACV3(
	responseKey,
	serverPubKey,
	cardPubKey,
	receivedMac []byte,
) error {
	tag := []byte("challenge-response")
	macInput := make(
		[]byte,
		0,
		len(tag)+len(serverPubKey)+len(cardPubKey),
	)
	macInput = append(macInput, tag...)
	macInput = append(macInput, serverPubKey...)
	macInput = append(macInput, cardPubKey...)

	expectedMAC := security_card.ComputeHMACSHA256(responseKey, macInput)
	if !hmac.Equal(receivedMac, expectedMAC) {
		return errors.Errorf("v3 sign challenge response MAC mismatch")
	}
	return nil
}

// VerifySignature verifies a signature from a muuncard.
func (m *SimulatedHoustonService) VerifySignature(
	publicKeyBytes, messageBytes, signedMessageBytes []byte,
) (bool, error) {

	// verify expected public key
	if len(publicKeyBytes) != 65 || publicKeyBytes[0] != 0x04 {
		return false, nil
	}

	ecdhPub, err := ecdh.P256().NewPublicKey(publicKeyBytes)
	if err != nil {
		return false, nil
	}

	pub, err := ecdhToECDSAPublicKey(ecdhPub)
	if err != nil {
		return false, nil
	}

	h := sha256.Sum256(messageBytes)

	// Verify the signature
	return ecdsa.VerifyASN1(pub, h[:], signedMessageBytes), nil
}

// ecdhToECDSAPublicKey converts an *ecdh.PublicKey into an *ecdsa.PublicKey.
// Hacky workaround to avoid deprecated elliptic.Unmarshal() and ignoring lint check.
// Only works for P-256 NIST curve, which is what we are using.
// Once we upgrade to Go 1.25 we could use proper support to transform ecdh.PublicKey to
// ecdsa.PublicKey. for now this is what we got.
func ecdhToECDSAPublicKey(key *ecdh.PublicKey) (*ecdsa.PublicKey, error) {
	if key.Curve() != ecdh.P256() {
		return nil, errors.New("public key curve not supported. We work with P256")
	}

	rawKey := key.Bytes()
	return &ecdsa.PublicKey{
		Curve: elliptic.P256(),
		// For
		X: big.NewInt(0).SetBytes(rawKey[1:33]),
		Y: big.NewInt(0).SetBytes(rawKey[33:]),
	}, nil
}

func mapToInternalServerHoustonError(message string, errCause error) *houston.HoustonResponseError {
	return &houston.HoustonResponseError{
		DeveloperMessage: errCause.Error(),
		ErrorCode:        houston.ErrUnknown,
		Message:          message,
		RequestID:        0,
		Status:           houston.StatusServerFailure,
	}
}

func (m *SimulatedHoustonService) persistCardData() error {
	var items = make(map[string]any)

	if m.lastRandomPrivateKeyMetadata != nil {
		privKeyInHex := hex.EncodeToString(m.lastRandomPrivateKeyMetadata.privateKey.Bytes())
		items[storage.KeyLastRandomPrivKeyInHex] = privKeyInHex

		items[storage.KeyTimeSinceLastChallengeUnixMillis] =
			m.lastRandomPrivateKeyMetadata.timeStamp.Unix()

		items[storage.KeyLastChallengeReasonHashInHex] = hex.EncodeToString(
			m.lastRandomPrivateKeyMetadata.reasonHash,
		)
	}

	// Note: LibwalletStorage IntType maps to int32, so we cast to int32
	items[storage.KeySecurityCardCallCounter] = int32(m.callCounter)
	items[storage.KeySecurityCardPairingSlot] = int32(m.pairingSlot)

	secretCardInHex := hex.EncodeToString(m.secretCardBytes[:])
	items[storage.KeySecretCardBytesInHex] = secretCardInHex

	items[storage.KeySecurityCardCommitmentPubInHex] = hex.EncodeToString(m.commitmentPub)
	items[keySecurityCardShortDeviceIDInHex] = hex.EncodeToString(m.shortDeviceID)

	slog.Debug("simulated houston - stored data", "data", items)

	err := m.keyValueStorage.SaveBatch(items)
	if err != nil {
		return errors.Errorf("error saving simulated houston data: %w", err)
	}

	return nil
}

func (m *SimulatedHoustonService) loadCardData() error {
	var keys = []string{
		storage.KeyLastRandomPrivKeyInHex,
		storage.KeySecurityCardCallCounter,
		storage.KeySecurityCardPairingSlot,
		storage.KeySecretCardBytesInHex,
		storage.KeyTimeSinceLastChallengeUnixMillis,
		storage.KeySecurityCardCommitmentPubInHex,
		storage.KeyLastChallengeReasonHashInHex,
		keySecurityCardShortDeviceIDInHex,
	}

	keyValues, err := m.keyValueStorage.GetBatch(keys)
	if err != nil {
		return errors.Errorf("error loading simulated houston data: %w", err)
	}

	slog.Debug("simulated houston - loaded data", "data", keyValues)

	if keyValues[storage.KeyLastRandomPrivKeyInHex] != nil {
		privKeyBytes, err := hex.DecodeString(keyValues[storage.KeyLastRandomPrivKeyInHex].(string))
		if err != nil {
			return errors.Errorf("error decoding server private key: %w", err)
		}

		privKey, err := ecdh.P256().NewPrivateKey(privKeyBytes)
		if err != nil {
			return errors.Errorf("error initializing server private key: %w", err)
		}

		timeStamp := time.Unix(keyValues[storage.KeyTimeSinceLastChallengeUnixMillis].(int64), 0)

		var reasonHash []byte
		if keyValues[storage.KeyLastChallengeReasonHashInHex] != nil {
			reasonHash, err = hex.DecodeString(
				keyValues[storage.KeyLastChallengeReasonHashInHex].(string),
			)
			if err != nil {
				return errors.Errorf("error decoding challenge reason hash: %w", err)
			}
		}

		m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
			privateKey: privKey,
			timeStamp:  timeStamp,
			reasonHash: reasonHash,
		}
	}

	// Note: LibwalletStorage IntType maps to int32, so we cast to int32
	if keyValues[storage.KeySecurityCardPairingSlot] != nil {
		m.pairingSlot = uint16(keyValues[storage.KeySecurityCardPairingSlot].(int32))
	}

	if keyValues[storage.KeySecurityCardCallCounter] != nil {
		m.callCounter = uint32(keyValues[storage.KeySecurityCardCallCounter].(int32))
	}

	if keyValues[storage.KeySecretCardBytesInHex] != nil {
		secretCard, err := hex.DecodeString(keyValues[storage.KeySecretCardBytesInHex].(string))
		if err != nil {
			return errors.Errorf("error decoding secret card in hex: %w", err)
		}
		copy(m.secretCardBytes[:], secretCard)
	}

	if keyValues[storage.KeySecurityCardCommitmentPubInHex] != nil {
		commitmentPub, err := hex.DecodeString(
			keyValues[storage.KeySecurityCardCommitmentPubInHex].(string),
		)
		if err != nil {
			return errors.Errorf("error decoding card commitment pub key: %w", err)
		}
		m.commitmentPub = commitmentPub
	}

	if keyValues[keySecurityCardShortDeviceIDInHex] != nil {
		shortDeviceIDInHex := keyValues[keySecurityCardShortDeviceIDInHex].(string)
		shortDeviceID, err := hex.DecodeString(shortDeviceIDInHex)
		if err != nil {
			return errors.Errorf("error decoding card short device id: %w", err)
		}
		m.shortDeviceID = shortDeviceID
	}

	return nil
}
