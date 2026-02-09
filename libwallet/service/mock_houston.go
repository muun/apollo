package service

import (
	"crypto/ecdh"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"github.com/muun/libwallet/cryptography"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
	"log/slog"
	"math/big"
	"reflect"
	"time"
)

type RandomPrivateKeyMetadata struct {
	privateKey *ecdh.PrivateKey
	timeStamp  time.Time
}

type MockHoustonService struct {
	keyValueStorage              *storage.KeyValueStorage
	lastRandomPrivateKeyMetadata *RandomPrivateKeyMetadata
	secretCardBytes              [32]byte
	securityCardUsageCount       uint16
	pairingSlot                  uint16
}

var _ HoustonService = (*MockHoustonService)(nil)

const challengeTimeoutInSeconds = 90

const (
	ErrChallengeExpired = 2090
	ErrInvalidSignature = 2091
	ErrInvalidMac       = 2092
	ErrUnknown          = 100_000
)

const (
	StatusClientFailure = 400
	StatusServerFailure = 500
)

func NewMockHoustonService(storage *storage.KeyValueStorage) *MockHoustonService {
	return &MockHoustonService{keyValueStorage: storage}
}

func (m *MockHoustonService) HealthCheck() error {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) ChallengeKeySetupStart(req model.ChallengeSetupJson) (model.SetupChallengeResponseJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) ChallengeKeySetupFinish(req model.ChallengeSetupVerifyJson) error {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) ChallengeSetupFinishWithVerifiableMuunKey(req model.ChallengeSetupVerifyJson) (model.VerifiableMuunKeyJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) VerifiableMuunKey() (model.VerifiableMuunKeyJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) CreateFirstSession(createSessionJson model.CreateFirstSessionJson) (model.CreateFirstSessionOkJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) FetchFeeWindow() (model.FeeWindowJson, error) {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) SubmitDiagnosticsScanData(req model.DiagnosticScanDataJson) error {
	//TODO implement me
	panic("implement me")
}

func (m *MockHoustonService) ChallengeSecurityCardPair() (model.ChallengeSecurityCardPairJson, error) {
	err := m.loadCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error loading houston data", err)
		return model.ChallengeSecurityCardPairJson{}, houstonError
	}

	randomPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error generating private key", err)
		return model.ChallengeSecurityCardPairJson{}, houstonError
	}

	m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
		privateKey: randomPrivateKey,
		timeStamp:  time.Now(),
	}

	randomPublicKey := randomPrivateKey.PublicKey().Bytes()

	return model.ChallengeSecurityCardPairJson{
		ServerPublicKeyInHex: hex.EncodeToString(randomPublicKey),
	}, nil
}

func (m *MockHoustonService) RegisterSecurityCard(
	req model.RegisterSecurityCardJson,
) (model.RegisterSecurityCardOkJson, error) {
	timeSinceLastChallenge := time.Since(m.lastRandomPrivateKeyMetadata.timeStamp).Seconds()
	if timeSinceLastChallenge > challengeTimeoutInSeconds {
		houstonError := &HoustonResponseError{
			DeveloperMessage: "challenge has expired",
			ErrorCode:        ErrChallengeExpired,
			Message:          "challenge has expired",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	cardPublicKeyBytes, err := hex.DecodeString(req.CardPublicKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding card pub key", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	clientPublicKeyBytes, err := hex.DecodeString(req.ClientPublicKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding client pub key", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	sharedPoint, err := cryptography.ECDH(
		m.lastRandomPrivateKeyMetadata.privateKey.Bytes(),
		cardPublicKeyBytes,
	)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("ecdh error", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// Compute secret_card = sha256(shared_point)
	secretCard := sha256.Sum256(sharedPoint)
	macSecretCard := secretCard[:16]
	encSecretCard := secretCard[16:]

	slog.Debug("macSecretCard", slog.String("secret", hex.EncodeToString(macSecretCard)))
	slog.Debug("encSecretCard", slog.String("secret", hex.EncodeToString(encSecretCard)))

	metadataBytes, err := SecurityCardMetadataToBytes(req.Metadata)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding card metadata", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	receivedMacBytes, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding mac", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// Verify MAC: mac = hmac(mac_secret_card, C || P || index || metadata || pub_client)
	err = verifyPairingMAC(
		cardPublicKeyBytes,
		nfc.IntTo2Bytes(req.PairingSlot),
		metadataBytes,
		m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes(),
		clientPublicKeyBytes,
		macSecretCard,
		receivedMacBytes,
	)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, &HoustonResponseError{
			DeveloperMessage: err.Error(),
			ErrorCode:        ErrInvalidMac,
			Message:          "mac verification failed: the message data has been tampered with or corrupted.",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	globalSignCardBytes, err := hex.DecodeString(req.GlobalSignCardInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding global sign card", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	globalPublicKeyBytes, err := hex.DecodeString(req.Metadata.GlobalPublicKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding global public card", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// Verify signed MAC with global public key
	isValidated, err := m.verifySignature(globalPublicKeyBytes, receivedMacBytes, globalSignCardBytes)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error with mac sig verification", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	if !isValidated {
		houstonError := &HoustonResponseError{
			DeveloperMessage: "signature could not be verified. Signed content was altered or signed with invalid/incorrect key",
			ErrorCode:        ErrInvalidSignature,
			Message:          "invalid signature",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// Store card and secret data
	m.securityCardUsageCount = req.Metadata.UsageCount
	m.secretCardBytes = secretCard
	m.pairingSlot = req.PairingSlot

	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return model.RegisterSecurityCardOkJson{}, houstonError
	}

	// TODO: Check if something should change on metadata returned
	enrichedMetadata := req.Metadata

	return model.RegisterSecurityCardOkJson{
		Metadata:          enrichedMetadata,
		IsKnownProvider:   true,
		IsCardAlreadyUsed: false,
	}, nil
}

func (m *MockHoustonService) ChallengeSecurityCardSign(
	req model.ChallengeSecurityCardSignJson,
) (model.ChallengeSecurityCardSignResponseJson, error) {
	err := m.loadCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error loading houston data", err)
		return model.ChallengeSecurityCardSignResponseJson{}, houstonError
	}

	randomPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error generating private key", err)
		return model.ChallengeSecurityCardSignResponseJson{}, houstonError
	}

	m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
		privateKey: randomPrivateKey,
		timeStamp:  time.Now(),
	}

	m.securityCardUsageCount += 1

	randomPublicKey := randomPrivateKey.PublicKey().Bytes()

	reasonBytes, err := hex.DecodeString(req.ReasonInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding reason", err)
		return model.ChallengeSecurityCardSignResponseJson{}, houstonError
	}

	challengeMac := nfc.MakeChallengeSignMac(
		m.secretCardBytes[:16],
		randomPublicKey,
		reasonBytes,
		m.securityCardUsageCount,
		m.pairingSlot,
	)

	return model.ChallengeSecurityCardSignResponseJson{
		ServerPublicKeyInHex: hex.EncodeToString(randomPublicKey),
		CardUsageCount:       m.securityCardUsageCount,
		MacInHex:             hex.EncodeToString(challengeMac),
		PairingSlot:          m.pairingSlot,
	}, nil
}

func (m *MockHoustonService) SolveSecurityCardChallenge(req model.SolveSecurityCardChallengeJson) error {
	timeSinceLastChallenge := time.Since(m.lastRandomPrivateKeyMetadata.timeStamp).Seconds()
	if timeSinceLastChallenge > challengeTimeoutInSeconds {
		return &HoustonResponseError{
			DeveloperMessage: "challenge has expired",
			ErrorCode:        ErrChallengeExpired,
			Message:          "challenge has expired",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	serverPublicKeyBytes := m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes()
	cardPublicKeyBytes, err := hex.DecodeString(req.PublicKeyInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding card pub key", err)
		return houstonError
	}

	receivedMac, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error decoding received mac", err)
		return houstonError
	}

	err = m.verifySolveChallengeMac(receivedMac, serverPublicKeyBytes, cardPublicKeyBytes)
	if err != nil {
		return &HoustonResponseError{
			DeveloperMessage: err.Error(),
			ErrorCode:        ErrInvalidMac,
			Message:          "mac verification failed: the message data has been tampered with or corrupted.",
			RequestId:        0,
			Status:           StatusClientFailure,
		}
	}

	// Calculate and store new secret card
	sharedPoint, err := cryptography.ECDH(
		m.lastRandomPrivateKeyMetadata.privateKey.Bytes(),
		cardPublicKeyBytes,
	)
	if err != nil {
		houstonError := mapToInternalServerHoustonError("ecdh error", err)
		return houstonError
	}

	// Update secret for forward secrecy: new_secret = HMAC(currentSecretCardBytes, sharedPoint)
	newSecretCardBytes := nfc.ComputeHMACSHA256(m.secretCardBytes[:], sharedPoint)
	copy(m.secretCardBytes[:], newSecretCardBytes)

	err = m.persistCardData()
	if err != nil {
		houstonError := mapToInternalServerHoustonError("error persisting houston data", err)
		return houstonError
	}

	return nil
}

func (m *MockHoustonService) verifySolveChallengeMac(
	receivedMac,
	serverPublicKeyBytes,
	cardPublicKeyBytes []byte,
) error {
	// Construct MAC input with: C || P
	macInput := make([]byte, 0, 130)
	macInput = append(macInput, serverPublicKeyBytes...) // C (server ephemeral pub key 65 bytes)
	macInput = append(macInput, cardPublicKeyBytes...)   // P (card ephemeral pub key 65 bytes)

	// Compute expected MAC using mac_secret_card (secret_card[:16])
	expectedMAC := nfc.ComputeHMACSHA256(m.secretCardBytes[:16], macInput)

	// Compare MACs
	if !reflect.DeepEqual(receivedMac, expectedMAC) {
		return fmt.Errorf("MAC mismatch - expected: %x, got: %x", expectedMAC, receivedMac)
	}
	return nil
}

func verifyPairingMAC(cardPublicKey,
	pairingSlot,
	metadata,
	serverRandomPubKey,
	clientPubKey,
	macSecretCard,
	receivedMac []byte,
) error {
	// Construct MAC input with: C || P || index || metadata || pub_client
	macInput := make([]byte, 0, 272)
	macInput = append(macInput, serverRandomPubKey...) // C (server random key, 65 bytes)
	macInput = append(macInput, cardPublicKey...)      // P (card public key, 65 bytes)
	macInput = append(macInput, pairingSlot...)        // index (2 bytes)
	macInput = append(macInput, metadata...)           // metadata (75 bytes)
	macInput = append(macInput, clientPubKey...)       // pub_client (65 bytes)

	// Compute expected MAC using mac_secret_card (secret_card[16:])
	expectedMAC := nfc.ComputeHMACSHA256(macSecretCard, macInput)

	// Compare MACs
	if !reflect.DeepEqual(receivedMac, expectedMAC) {
		return fmt.Errorf("mac mismatch - expected: %x, got: %x", expectedMAC, receivedMac)
	}

	return nil
}

func SecurityCardMetadataToBytes(m model.SecurityCardMetadataJson) ([]byte, error) {
	handleError := func(err error) error {
		return fmt.Errorf("error decoding metadata: %w", err)
	}
	const MetadataSize = 75
	buf := make([]byte, 0, MetadataSize) // 75 bytes

	// Global public key (65 bytes)
	globalPublicKeyBytes, err := hex.DecodeString(m.GlobalPublicKeyInHex)
	if err != nil {
		return nil, handleError(err)
	}
	buf = append(buf, globalPublicKeyBytes...)

	// Card vendor (2 bytes)
	cardVendorBytes, err := hex.DecodeString(m.CardVendorInHex)
	if err != nil {
		return nil, handleError(err)
	}
	buf = append(buf, cardVendorBytes...)

	// Card model (2 bytes)
	cardModelBytes, err := hex.DecodeString(m.CardModelInHex)
	if err != nil {
		return nil, handleError(err)
	}
	buf = append(buf, cardModelBytes...)

	// Firmware version (2 bytes)
	buf = append(buf, nfc.IntTo2Bytes(m.FirmwareVersion)...)

	// Usage count (2 bytes, big-endian)
	buf = append(buf, nfc.IntTo2Bytes(m.UsageCount)...)

	// Language code (2 bytes)
	languageCodeBytes, err := hex.DecodeString(m.LanguageCodeInHex)
	if err != nil {
		return nil, handleError(err)
	}
	buf = append(buf, languageCodeBytes...)

	return buf, nil
}

// verifySignature verifies a signature from a muuncard.
func (m *MockHoustonService) verifySignature(publicKeyBytes, messageBytes, signedMessageBytes []byte) (bool, error) {

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

func mapToInternalServerHoustonError(message string, errCause error) *HoustonResponseError {
	return &HoustonResponseError{
		DeveloperMessage: errCause.Error(),
		ErrorCode:        ErrUnknown,
		Message:          message,
		RequestId:        0,
		Status:           StatusServerFailure,
	}
}

func (m *MockHoustonService) persistCardData() error {
	var items = make(map[string]any)

	if m.lastRandomPrivateKeyMetadata != nil {
		privKeyInHex := hex.EncodeToString(m.lastRandomPrivateKeyMetadata.privateKey.Bytes())
		items[storage.KeyLastRandomPrivKeyInHex] = privKeyInHex

		items[storage.KeyTimeSinceLastChallengeUnixMillis] = m.lastRandomPrivateKeyMetadata.timeStamp.Unix()
	}

	// Note: LibwalletStorage IntType maps to int32, so we cast to int32
	items[storage.KeySecurityCardUsageCount] = int32(m.securityCardUsageCount)
	items[storage.KeySecurityCardPairingSlot] = int32(m.pairingSlot)

	secretCardInHex := hex.EncodeToString(m.secretCardBytes[:])
	items[storage.KeySecretCardBytesInHex] = secretCardInHex

	slog.Debug("mockHouston - stored data", "data", items)

	err := m.keyValueStorage.SaveBatch(items)
	if err != nil {
		return fmt.Errorf("error saving mock houston data: %w", err)
	}

	return nil
}

func (m *MockHoustonService) loadCardData() error {
	var keys = []string{
		storage.KeyLastRandomPrivKeyInHex,
		storage.KeySecurityCardUsageCount,
		storage.KeySecurityCardPairingSlot,
		storage.KeySecretCardBytesInHex,
		storage.KeyTimeSinceLastChallengeUnixMillis,
	}

	keyValues, err := m.keyValueStorage.GetBatch(keys)
	if err != nil {
		return fmt.Errorf("error loading mock houston data: %w", err)
	}

	slog.Debug("mock houston - loaded data", "data", keyValues)

	if keyValues[storage.KeyLastRandomPrivKeyInHex] != nil {
		privKeyBytes, err := hex.DecodeString(keyValues[storage.KeyLastRandomPrivKeyInHex].(string))
		if err != nil {
			return fmt.Errorf("error decoding server private key: %w", err)
		}

		privKey, err := ecdh.P256().NewPrivateKey(privKeyBytes)
		if err != nil {
			return fmt.Errorf("error initializing server private key: %w", err)
		}

		timeStamp := time.Unix(keyValues[storage.KeyTimeSinceLastChallengeUnixMillis].(int64), 0)

		m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
			privateKey: privKey,
			timeStamp:  timeStamp,
		}
	}

	// Note: LibwalletStorage IntType maps to int32, so we cast to int32
	if keyValues[storage.KeySecurityCardPairingSlot] != nil {
		m.pairingSlot = uint16(keyValues[storage.KeySecurityCardPairingSlot].(int32))
	}

	if keyValues[storage.KeySecurityCardUsageCount] != nil {
		m.securityCardUsageCount = uint16(keyValues[storage.KeySecurityCardUsageCount].(int32))
	}

	if keyValues[storage.KeySecretCardBytesInHex] != nil {
		secretCard, err := hex.DecodeString(keyValues[storage.KeySecretCardBytesInHex].(string))
		if err != nil {
			return fmt.Errorf("error decoding secret card in hex: %w", err)
		}
		copy(m.secretCardBytes[:], secretCard)
	}

	return nil
}
