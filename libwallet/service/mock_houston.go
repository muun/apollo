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
	"github.com/muun/libwallet/service/model"
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
	lastRandomPrivateKeyMetadata *RandomPrivateKeyMetadata
	securityCardPaired           *model.SecurityCardMetadataJson
	secretCardBytes              [32]byte
	pairingSlot                  int
}

var _ HoustonService = (*MockHoustonService)(nil)

const challengeTimeoutInSeconds = 90

func NewMockHoustonService() *MockHoustonService {
	return &MockHoustonService{}
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

	randomPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		return model.ChallengeSecurityCardPairJson{}, fmt.Errorf("error generating private key: %w", err)
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
		return model.RegisterSecurityCardOkJson{}, errors.New("challenge was already invalidated")
	}

	cardPublicKeyBytes, err := hex.DecodeString(req.CardPublicKeyInHex)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, fmt.Errorf("error decoding card pub key: %w", err)
	}

	clientPublicKeyBytes, err := hex.DecodeString(req.ClientPublicKeyInHex)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, fmt.Errorf("error decoding client pub key: %w", err)
	}

	sharedPoint, err := performECDH(m.lastRandomPrivateKeyMetadata.privateKey.Bytes(), cardPublicKeyBytes)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, fmt.Errorf("ECDH error: %w", err)
	}

	// Compute secret_card = sha256(shared_point)
	secretCard := sha256.Sum256(sharedPoint)
	macSecretCard := secretCard[:16]
	encSecretCard := secretCard[16:]

	slog.Debug("macSecretCard", slog.String("secret", hex.EncodeToString(macSecretCard)))
	slog.Debug("encSecretCard", slog.String("secret", hex.EncodeToString(encSecretCard)))

	metadataBytes, err := SecurityCardMetadataToBytes(req.Metadata)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, fmt.Errorf("error decoding card metadata: %w", err)
	}

	receivedMacBytes, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, fmt.Errorf("error decoding mac: %w", err)
	}

	// Verify MAC: mac = hmac(mac_secret_card, C || P || index || metadata || pub_client)
	err = verifyPairingMAC(
		cardPublicKeyBytes,
		IntTo2Bytes(req.PairingSlot),
		metadataBytes,
		m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes(),
		clientPublicKeyBytes,
		macSecretCard,
		receivedMacBytes,
	)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, fmt.Errorf("mac verify error: %w", err)
	}

	globalSignCardBytes, err := hex.DecodeString(req.GlobalSignCardInHex)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, fmt.Errorf("error decoding global sign card: %w", err)
	}

	globalPublicKeyBytes, err := hex.DecodeString(req.Metadata.GlobalPublicKeyInHex)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, fmt.Errorf("error decoding global public card: %w", err)
	}

	// Verify signed MAC with global public key
	isValidated, err := m.VerifySignature(globalPublicKeyBytes, receivedMacBytes, globalSignCardBytes)
	if err != nil {
		return model.RegisterSecurityCardOkJson{}, fmt.Errorf("error with mac sig verification: %w", err)
	}

	if !isValidated {
		return model.RegisterSecurityCardOkJson{}, errors.New("mac signature is wrong")
	}

	// Store card and secret data
	m.securityCardPaired = &req.Metadata
	m.secretCardBytes = secretCard
	m.pairingSlot = req.PairingSlot

	// TODO: Check if something should change on metadata returned
	enrichedMetadata := req.Metadata

	return model.RegisterSecurityCardOkJson{
		Metadata:          enrichedMetadata,
		IsKnownProvider:   true,
		IsCardAlreadyUsed: false,
	}, nil
}

func (m *MockHoustonService) ChallengeSecurityCardSign() (model.ChallengeSecurityCardSignJson, error) {
	randomPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		return model.ChallengeSecurityCardSignJson{}, fmt.Errorf("error generating private key: %w", err)
	}

	m.lastRandomPrivateKeyMetadata = &RandomPrivateKeyMetadata{
		privateKey: randomPrivateKey,
		timeStamp:  time.Now(),
	}

	m.securityCardPaired.UsageCount += 1

	randomPublicKey := randomPrivateKey.PublicKey().Bytes()

	challengeMac := m.makeChallengeSignMac(randomPublicKey)

	return model.ChallengeSecurityCardSignJson{
		ServerPublicKeyInHex: hex.EncodeToString(randomPublicKey),
		CardUsageCount:       m.securityCardPaired.UsageCount,
		MacInHex:             hex.EncodeToString(challengeMac),
	}, nil
}

func (m *MockHoustonService) SolveSecurityCardChallenge(req model.SolveSecurityCardChallengeJson) error {
	timeSinceLastChallenge := time.Since(m.lastRandomPrivateKeyMetadata.timeStamp).Seconds()
	if timeSinceLastChallenge > challengeTimeoutInSeconds {
		return errors.New("challenge was already invalidated")
	}

	serverPublicKeyBytes := m.lastRandomPrivateKeyMetadata.privateKey.PublicKey().Bytes()
	cardPublicKeyBytes, err := hex.DecodeString(req.PublicKeyInHex)
	if err != nil {
		return errors.New("error decoding card pub key")
	}

	receivedMac, err := hex.DecodeString(req.MacInHex)
	if err != nil {
		return errors.New("error decoding received mac")
	}

	err = m.verifySolveChallengeMac(receivedMac, serverPublicKeyBytes, cardPublicKeyBytes)
	if err != nil {
		return fmt.Errorf("mac verification error:%w", err)
	}

	// Calculate and store new secret card
	sharedPoint, err := performECDH(m.lastRandomPrivateKeyMetadata.privateKey.Bytes(), cardPublicKeyBytes)
	if err != nil {
		return fmt.Errorf("ECDH error: %w", err)
	}

	// Compute new_secret_card = sha256(shared_point)
	m.secretCardBytes = sha256.Sum256(sharedPoint)

	return nil
}

func (m *MockHoustonService) verifySolveChallengeMac(
	receivedMac,
	serverPublicKeyBytes,
	cardPublicKeyBytes []byte,
) error {
	// Construct MAC input with: secret_card || C || P
	macInput := make([]byte, 0, 162)
	macInput = append(macInput, m.secretCardBytes[:]...) // secret_card (32 bytes)
	macInput = append(macInput, serverPublicKeyBytes...) // C (server ephemeral pub key 65 bytes)
	macInput = append(macInput, cardPublicKeyBytes...)   // P (card ephemeral pub key 65 bytes)

	// Compute expected MAC using mac_secret_card (secret_card[16:])
	expectedMAC := computeHMACSHA256(m.secretCardBytes[16:], macInput)

	fmt.Printf("Expected MAC: %x", expectedMAC)
	fmt.Printf("Received MAC: %x", receivedMac)

	// Compare MACs
	if !reflect.DeepEqual(receivedMac, expectedMAC) {
		return fmt.Errorf("MAC mismatch - expected: %x, got: %x", expectedMAC, receivedMac)
	}
	return nil
}

func (m *MockHoustonService) makeChallengeSignMac(randomPublicKey []byte) []byte {
	// Construct MAC input with: secretCard || C || UsageCount || PairingSlot
	macInput := make([]byte, 0, 101)
	macInput = append(macInput, m.secretCardBytes[:]...)                         // last secret card, 32 bytes
	macInput = append(macInput, randomPublicKey...)                              // P (card public key, 65 bytes)
	macInput = append(macInput, IntTo2Bytes(m.securityCardPaired.UsageCount)...) // usage count (2 bytes)
	macInput = append(macInput, IntTo2Bytes(m.pairingSlot)...)                   // pairing slot (2 bytes)

	// Compute expected MAC using mac_secret_card (secret_card[16:])
	challengeMac := computeHMACSHA256(m.secretCardBytes[16:], macInput)
	return challengeMac
}

// performECDH performs ECDH key agreement
func performECDH(privateKeyBytes, publicKeyBytes []byte) ([]byte, error) {
	curve := elliptic.P256()

	// Parse public key
	if len(publicKeyBytes) != 65 || publicKeyBytes[0] != 0x04 {
		return nil, errors.New("invalid public key format")
	}

	x := new(big.Int).SetBytes(publicKeyBytes[1:33])
	y := new(big.Int).SetBytes(publicKeyBytes[33:65])

	// Verify the public key is on the curve
	if !curve.IsOnCurve(x, y) {
		return nil, errors.New("public key not on curve")
	}

	// Perform scalar multiplication
	sharedX, sharedY := curve.ScalarMult(x, y, privateKeyBytes)

	// Convert shared point to uncompressed format
	sharedSecret := make([]byte, 65)
	sharedSecret[0] = 0x04

	xBytes := sharedX.Bytes()
	yBytes := sharedY.Bytes()

	copy(sharedSecret[1+32-len(xBytes):33], xBytes)
	copy(sharedSecret[33+32-len(yBytes):65], yBytes)

	return sharedSecret, nil
}

// computeHMACSHA256 computes HMAC-SHA256
func computeHMACSHA256(key, data []byte) []byte {
	const blockSize = 64 // SHA-256 block size

	// Key preprocessing
	if len(key) > blockSize {
		hash := sha256.Sum256(key)
		key = hash[:]
	}

	// Pad key to block size
	paddedKey := make([]byte, blockSize)
	copy(paddedKey, key)

	// Create inner and outer padding
	innerPad := make([]byte, blockSize)
	outerPad := make([]byte, blockSize)

	for i := 0; i < blockSize; i++ {
		innerPad[i] = paddedKey[i] ^ 0x36
		outerPad[i] = paddedKey[i] ^ 0x5c
	}

	// Inner hash
	innerHash := sha256.New()
	innerHash.Write(innerPad)
	innerHash.Write(data)
	innerResult := innerHash.Sum(nil)

	// Outer hash
	outerHash := sha256.New()
	outerHash.Write(outerPad)
	outerHash.Write(innerResult)

	return outerHash.Sum(nil)
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
	expectedMAC := computeHMACSHA256(macSecretCard, macInput)

	fmt.Printf("Expected MAC: %x", expectedMAC)
	fmt.Printf("Received MAC: %x", receivedMac)

	// Compare MACs
	if !reflect.DeepEqual(receivedMac, expectedMAC) {
		return fmt.Errorf("MAC mismatch - expected: %x, got: %x", expectedMAC, receivedMac)
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
	buf = append(buf, IntTo2Bytes(m.FirmwareVersion)...)

	// Usage count (2 bytes, big-endian)
	buf = append(buf, IntTo2Bytes(m.UsageCount)...)

	// Language code (2 bytes)
	languageCodeBytes, err := hex.DecodeString(m.LanguageCodeInHex)
	if err != nil {
		return nil, handleError(err)
	}
	buf = append(buf, languageCodeBytes...)

	return buf, nil
}

func IntTo2Bytes(n int) []byte {
	hi := byte(n >> 8)
	lo := byte(n & 0xFF)
	return []byte{hi, lo}
}

// VerifySignature verifies a signature from a muuncard.
func (m *MockHoustonService) VerifySignature(publicKeyBytes, messageBytes, signedMessageBytes []byte) (bool, error) {

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
