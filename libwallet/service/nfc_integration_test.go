package service

import (
	"crypto/ecdh"
	"crypto/rand"
	"encoding/hex"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
	"path"
	"strings"
	"testing"
)

func TestMockCardPairCardSuccess(t *testing.T) {
	mockCard, err := nfc.NewMockMuunCardV2()
	if err != nil {
		t.Fatalf("NewMockMuunCardV2 failed: %v", err)
	}

	mockNfcBridge := nfc.NewMockJavaCard(mockCard)
	card := nfc.NewCardV2(mockNfcBridge)

	// Create a new empty DB providing a new dataFilePath
	dataFilePath := path.Join(t.TempDir(), "test.db")
	keyValueStorage := storage.NewKeyValueStorage(dataFilePath, buildStorageSchemaForTests())
	mockHouston := NewMockHoustonService(keyValueStorage)

	pairCardWithHouston(t, mockHouston, card)
}

// pairCardWithHouston performs a complete pairing flow and returns all necessary data
func pairCardWithHouston(
	t *testing.T,
	mockHouston *MockHoustonService,
	card *nfc.MuunCardV2,
) {

	challengePair, err := mockHouston.ChallengeSecurityCardPair()
	if err != nil {
		t.Fatalf("error requesting challenge to server: %v", err)
	}

	serverPublicKey, err := hex.DecodeString(challengePair.ServerPublicKeyInHex)
	if err != nil {
		t.Fatalf("error decoding server key: %v", err)
	}

	clientPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("error generating client private key: %v", err)
	}

	clientPublicKey := clientPrivateKey.PublicKey().Bytes()

	pairingResp, err := card.Pair(serverPublicKey, clientPublicKey)
	if err != nil {
		t.Fatalf("client pairing failed: %v", err)
	}

	// Verify basic response structure
	if len(pairingResp.CardPublicKey) != 65 {
		t.Fatalf("Expected CardPublicKey length 65, got %d", len(pairingResp.CardPublicKey))
	}

	if pairingResp.CardPublicKey[0] != 0x04 {
		t.Fatalf("Expected CardPublicKey to start with 0x04, got 0x%02x", pairingResp.CardPublicKey[0])
	}

	// Verify metadata in pairing response
	err = nfc.VerifyMetadata(pairingResp.Metadata)
	if err != nil {
		t.Fatalf("invalid metadata: %v", err)
	}

	registerPairingOnHouston(t, mockHouston, pairingResp, clientPublicKey)
}

func registerPairingOnHouston(
	t *testing.T,
	mockHouston HoustonService,
	pairingResp *nfc.PairingResponse,
	clientPubKey []byte,
) {

	registerSecurityCardJson, err := MapRegisterSecurityCardJson(pairingResp, clientPubKey)
	if err != nil {
		t.Fatalf("failed to map pairing response %v", err)
	}

	_, err = mockHouston.RegisterSecurityCard(*registerSecurityCardJson)
	if err != nil {
		t.Fatalf("failed to register security card in houston: %v", err)
	}
}

func TestMockCardSignChallenge(t *testing.T) {
	mockCard, err := nfc.NewMockMuunCardV2()
	if err != nil {
		t.Fatalf("NewMockMuunCardV2 failed: %v", err)
	}

	mockNfcBridge := nfc.NewMockJavaCard(mockCard)
	card := nfc.NewCardV2(mockNfcBridge)

	dataFilePath := path.Join(t.TempDir(), "test.db")
	keyValueStorage := storage.NewKeyValueStorage(dataFilePath, buildStorageSchemaForTests())
	mockHouston := NewMockHoustonService(keyValueStorage)

	// Pair card with Houston to enable challenge signing
	pairCardWithHouston(t, mockHouston, card)

	// Test single-chunk challenge
	reason := []byte("test challenge reason")

	testSignChallengeSuccess(t, mockHouston, card, reason)
	testSignChallengeInvalidCounter(t, mockHouston, card, reason)
	testSignChallengeInvalidSlot(t, mockHouston, card, reason)
	testSignChallengeInvalidMac(t, mockHouston, card, reason)
	testSignChallengeSecretUpdates(t, mockHouston, card, reason)
}

// testSignChallengeSuccess performs a complete sign challenge flow
func testSignChallengeSuccess(
	t *testing.T,
	mockHouston HoustonService,
	card *nfc.MuunCardV2,
	reason []byte,
) {

	challengeResponse, err := mockHouston.ChallengeSecurityCardSign(model.ChallengeSecurityCardSignJson{
		ReasonInHex: hex.EncodeToString(reason),
	})
	if err != nil {
		t.Fatalf("error requesting a challenge from houston: %v", err)
	}

	challenge, err := MapSecurityCardSignChallengeResponse(challengeResponse)
	if err != nil {
		t.Fatalf("fail to parse sign challenge response from houston: %v", err)
	}

	signChallengeResponse, err := card.SignChallenge(challenge, reason)
	if err != nil {
		t.Fatalf("error signing challenge: %v", err)
	}

	cardPublicKeyInHex := hex.EncodeToString(signChallengeResponse.CardPublicKey)
	macInHex := hex.EncodeToString(signChallengeResponse.MAC)
	securityCardChallengeJson := model.SolveSecurityCardChallengeJson{
		PublicKeyInHex: cardPublicKeyInHex,
		MacInHex:       macInHex,
	}

	err = mockHouston.SolveSecurityCardChallenge(securityCardChallengeJson)
	if err != nil {
		t.Fatalf("error solving challenge: %v", err)
	}
}

func testSignChallengeInvalidCounter(
	t *testing.T,
	mockHouston *MockHoustonService,
	card *nfc.MuunCardV2,
	reason []byte,
) {
	challengeResponse, err := mockHouston.ChallengeSecurityCardSign(model.ChallengeSecurityCardSignJson{
		ReasonInHex: hex.EncodeToString(reason),
	})
	if err != nil {
		t.Fatalf("error requesting a challenge from houston: %v", err)
	}

	challenge, err := MapSecurityCardSignChallengeResponse(challengeResponse)
	if err != nil {
		t.Fatalf("fail to parse sign challenge response from houston: %v", err)
	}

	challenge.CardUsageCount -= 1 // Try to go backwards (should fail since it's < correct)

	_, err = card.SignChallenge(challenge, reason)

	if err == nil || !strings.Contains(err.Error(), "invalid counter") {
		t.Fatal("should reject invalid card counter")
	}

	// Check that with correct counter, we actually succeed
	challenge.CardUsageCount += 1

	signChallengeResponse, err := card.SignChallenge(challenge, reason)

	if err != nil {
		t.Fatalf("should succeed with correct card counter, got: %v", err)
	}

	cardPublicKeyInHex := hex.EncodeToString(signChallengeResponse.CardPublicKey)
	macInHex := hex.EncodeToString(signChallengeResponse.MAC)
	securityCardChallengeJson := model.SolveSecurityCardChallengeJson{
		PublicKeyInHex: cardPublicKeyInHex,
		MacInHex:       macInHex,
	}

	err = mockHouston.SolveSecurityCardChallenge(securityCardChallengeJson)
	if err != nil {
		t.Fatalf("error solving challenge: %v", err)
	}
}

func testSignChallengeInvalidSlot(
	t *testing.T,
	mockHouston *MockHoustonService,
	card *nfc.MuunCardV2,
	reason []byte,
) {
	challengeResponse, err := mockHouston.ChallengeSecurityCardSign(model.ChallengeSecurityCardSignJson{
		ReasonInHex: hex.EncodeToString(reason),
	})
	if err != nil {
		t.Fatalf("error requesting a challenge from houston: %v", err)
	}

	challenge, err := MapSecurityCardSignChallengeResponse(challengeResponse)
	if err != nil {
		t.Fatalf("fail to parse sign challenge response from houston: %v", err)
	}

	originalPairingSlot := challenge.PairingSlot
	challenge.PairingSlot -= 999 // Invalid Slot

	_, err = card.SignChallenge(challenge, reason)

	if err == nil || !strings.Contains(err.Error(), "not paired") {
		t.Fatalf("should reject invalid slot index")
	}

	// Check that with correct pairing slot, we actually succeed
	challenge.PairingSlot = originalPairingSlot

	signChallengeResponse, err := card.SignChallenge(challenge, reason)
	if err != nil {
		t.Fatalf("should succeed with correct slot, got: %v", err)
	}

	cardPublicKeyInHex := hex.EncodeToString(signChallengeResponse.CardPublicKey)
	macInHex := hex.EncodeToString(signChallengeResponse.MAC)
	securityCardChallengeJson := model.SolveSecurityCardChallengeJson{
		PublicKeyInHex: cardPublicKeyInHex,
		MacInHex:       macInHex,
	}

	err = mockHouston.SolveSecurityCardChallenge(securityCardChallengeJson)
	if err != nil {
		t.Fatalf("error solving challenge: %v", err)
	}
}

func testSignChallengeInvalidMac(
	t *testing.T,
	mockHouston *MockHoustonService,
	card *nfc.MuunCardV2,
	reason []byte,
) {
	challengeResponse, err := mockHouston.ChallengeSecurityCardSign(model.ChallengeSecurityCardSignJson{
		ReasonInHex: hex.EncodeToString(reason),
	})
	if err != nil {
		t.Fatalf("error requesting a challenge from houston: %v", err)
	}

	challenge, err := MapSecurityCardSignChallengeResponse(challengeResponse)
	if err != nil {
		t.Fatalf("fail to parse sign challenge response from houston: %v", err)
	}

	challenge.Mac[0] = challenge.Mac[0] ^ 0xFF // Invert the first byte, making it invalid

	_, err = card.SignChallenge(challenge, reason)
	if err == nil || !strings.Contains(err.Error(), "invalid MAC") {
		t.Fatalf("should reject invalid mac")
	}

	// Check that with correct mac, we actually succeed
	challenge.Mac[0] = challenge.Mac[0] ^ 0xFF // Invert the first byte again, making it valid

	signChallengeResponse, err := card.SignChallenge(challenge, reason)

	if err != nil {
		t.Fatalf("should succeed with correct slot, got: %v", err)
	}

	cardPublicKeyInHex := hex.EncodeToString(signChallengeResponse.CardPublicKey)
	macInHex := hex.EncodeToString(signChallengeResponse.MAC)
	securityCardChallengeJson := model.SolveSecurityCardChallengeJson{
		PublicKeyInHex: cardPublicKeyInHex,
		MacInHex:       macInHex,
	}

	err = mockHouston.SolveSecurityCardChallenge(securityCardChallengeJson)
	if err != nil {
		t.Fatalf("error solving challenge: %v", err)
	}
}

func testSignChallengeSecretUpdates(
	t *testing.T,
	houston *MockHoustonService,
	card *nfc.MuunCardV2,
	reason1 []byte,
) {

	// challenge 1 should work with initial secret
	// Signing process never finishes, shared secret still valid.
	// challenge 2 should work with initial secret
	// finish challenge process and update secret
	// challenge 3 should work with updated secret

	// TODO: implement

}

func buildStorageSchemaForTests() map[string]storage.Classification {
	return map[string]storage.Classification{
		storage.KeyLastRandomPrivKeyInHex: {
			BackupType:       storage.AsyncAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.StringType{},
		},
		storage.KeySecurityCardUsageCount: {
			BackupType:       storage.AsyncAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.IntType{},
		},
		storage.KeySecretCardBytesInHex: {
			BackupType:       storage.AsyncAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.StringType{},
		},
		storage.KeySecurityCardPairingSlot: {
			BackupType:       storage.AsyncAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.IntType{},
		},
		storage.KeyTimeSinceLastChallengeUnixMillis: {
			BackupType:       storage.AsyncAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.LongType{},
		},
	}
}
