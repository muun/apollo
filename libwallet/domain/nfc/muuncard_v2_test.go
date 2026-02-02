package nfc

import (
	"crypto/sha256"
	"github.com/muun/libwallet/cryptography"
	"github.com/muun/libwallet/domain/model/security_card"
	"strings"
	"testing"
)

type SignChallenge = security_card.SecurityCardSignChallenge

func TestGetVersion(t *testing.T) {
	mockCard, err := NewMockMuunCardV2()
	if err != nil {
		t.Fatalf("failed NewMockMuunCardV2: %v", err)
	}

	mockNfcBridge := NewMockJavaCard(mockCard)
	card := NewCardV2(mockNfcBridge)

	version, err := card.GetVersion()
	if err != nil {
		t.Fatalf("failed GetVersion: %v", err)
	}

	if version.Vendor != "MuunV2" {
		t.Fatalf("expected vendor 'MuunV2', got '%s'", version.Vendor)
	}

	if version.Major != 2 {
		t.Fatalf("expected major version 2, got %d", version.Major)
	}

	if version.Minor != 0 {
		t.Fatalf("expected minor version 0, got %d", version.Minor)
	}
}

func TestGetMetadata(t *testing.T) {
	mockCard, err := NewMockMuunCardV2()
	if err != nil {
		t.Fatalf("failed NewMockMuunCardV2: %v", err)
	}

	mockNfcBridge := NewMockJavaCard(mockCard)
	card := NewCardV2(mockNfcBridge)

	metadata, err := card.GetMetadata()
	if err != nil {
		t.Fatalf("failed GetMetadata: %v", err)
	}

	err = VerifyMetadata(metadata)
	if err != nil {
		t.Fatalf("invalid metadata: %v", err)
	}
}

func TestPairCard_ErrorScenarios(t *testing.T) {
	mockCard, err := NewMockMuunCardV2()
	if err != nil {
		t.Fatalf("failed NewMockMuunCardV2: %v", err)
	}

	mockNfcBridge := NewMockJavaCard(mockCard)
	card := NewCardV2(mockNfcBridge)

	serverKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}
	clientKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}

	pointAtInfinity := make([]byte, 65)
	pointAtInfinity[0] = 0x04 // Valid format but all zeros

	invalidFormat := make([]byte, 65)
	invalidFormat[0] = 0x03 // Compressed format (invalid for this applet)

	tests := []struct {
		name            string
		clientPublicKey []byte
		serverPublicKey []byte
	}{
		{
			name:            "PairWithPointAtInfinityForServerPublicKey",
			clientPublicKey: clientKeyPair.PublicKey,
			serverPublicKey: pointAtInfinity,
		},
		{
			name:            "PairWithPointAtInfinityForClientPublicKey",
			clientPublicKey: pointAtInfinity,
			serverPublicKey: serverKeyPair.PublicKey,
		},
		{
			name:            "testPairWithInvalidPubKeyForServerPublicKey",
			clientPublicKey: clientKeyPair.PublicKey,
			serverPublicKey: invalidFormat,
		},
		{
			name:            "testPairWithInvalidPubKeyForClientPublicKey",
			clientPublicKey: invalidFormat,
			serverPublicKey: serverKeyPair.PublicKey,
		},
		{
			name:            "PairWithWrongLengthForServerPublicKey",
			clientPublicKey: clientKeyPair.PublicKey,
			serverPublicKey: make([]byte, 64), // Too short
		},
		{
			name:            "PairWithWrongLengthForClientPublicKey",
			clientPublicKey: make([]byte, 64), // Too short
			serverPublicKey: serverKeyPair.PublicKey,
		},
		{
			name:            "PairWithEmptyDataForServerPublicKey",
			clientPublicKey: clientKeyPair.PublicKey,
			serverPublicKey: []byte{},
		},
		{
			name:            "PairWithEmptyDataForClientPublicKey",
			clientPublicKey: []byte{},
			serverPublicKey: serverKeyPair.PublicKey,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {

			_, err := card.Pair(tt.serverPublicKey, tt.clientPublicKey)
			if err == nil {
				t.Fatal("Expected error for failure scenario")
			}
		})
	}
}

func TestPairCard_Success(t *testing.T) {

	mockCard, err := NewMockMuunCardV2()
	if err != nil {
		t.Fatalf("failed NewMockMuunCardV2: %v", err)
	}

	mockNfcBridge := NewMockJavaCard(mockCard)
	card := NewCardV2(mockNfcBridge)

	serverKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}
	clientKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}

	pairingResp, err := card.Pair(serverKeyPair.PublicKey, clientKeyPair.PublicKey)
	if err != nil {
		t.Fatalf("client pairing failed: %v", err)
	}

	// Verify basic response structure
	err = cryptography.ValidateSecp256r1PublicKey(pairingResp.CardPublicKey)
	if err != nil {
		t.Fatalf("failed public key validation, got %v", err)
	}

	// Verify metadata in pairing response
	err = VerifyMetadata(pairingResp.Metadata)
	if err != nil {
		t.Fatalf("invalid metadata: %v", err)
	}

	// Signature and mac verification is tested in integration test with MockHouston.
	// See nfc_integration_test.go.
}

func TestSignChallenge_ErrorScenarios(t *testing.T) {
	mockCard, err := NewMockMuunCardV2()
	if err != nil {
		t.Fatalf("failed NewMockMuunCardV2: %v", err)
	}

	mockNfcBridge := NewMockJavaCard(mockCard)
	card := NewCardV2(mockNfcBridge)

	// First, pair the card to establish a slot
	serverKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}
	clientKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}

	pairingResp, err := card.Pair(serverKeyPair.PublicKey, clientKeyPair.PublicKey)
	if err != nil {
		t.Fatalf("card pairing failed: %v", err)
	}

	challengeKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed to generate challenge key pair: %v", err)
	}

	tests := []struct {
		name            string
		challengeMapper func(challenge *SignChallenge) *SignChallenge
		expectedError   string
	}{
		{
			name: "SignChallengeInvalidPublicKey",
			challengeMapper: func(challenge *SignChallenge) *SignChallenge {

				invalidPubKey := make([]byte, 65)
				copy(invalidPubKey, challengeKeyPair.PublicKey)
				invalidPubKey[0] = 0x03 // Compressed format (invalid)
				challenge.ServerPublicKey = invalidPubKey
				return challenge
			},
			expectedError: "rejected public key: invalid format",
		},
		{
			name: "SignChallengeInvalidCounter",
			challengeMapper: func(challenge *SignChallenge) *SignChallenge {

				// Use counter 0 (should be > current counter which starts at 0)
				challenge.CardUsageCount = uint16(0)
				return challenge
			},
			expectedError: "invalid counter",
		},
		{
			name: "SignChallengeInvalidMac",
			challengeMapper: func(challenge *SignChallenge) *SignChallenge {

				challenge.Mac = make([]byte, 32) // All zeros - invalid MAC
				return challenge
			},
			expectedError: "invalid MAC",
		},
		// TODO add reason to SignChallenge and test for "reason too long"
		// e.g SignChallengeStreaming not supported yet
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {

			ephemeralPublicKey := challengeKeyPair.PublicKey
			counter := uint16(1)
			index := uint16(0)
			reason := []byte("test reason 1")

			challenge := generateChallenge(
				serverKeyPair.PrivateKey,
				pairingResp.CardPublicKey,
				ephemeralPublicKey,
				reason,
				counter,
				index,
			)

			challenge = tt.challengeMapper(challenge)

			response, err := card.SignChallenge(challenge, reason)
			if err == nil {
				t.Fatal("Expected error for failure scenario")
			}

			if response != nil {
				t.Errorf("expected nil response but got %v", response)
			}

			if !strings.Contains(err.Error(), tt.expectedError) {
				t.Errorf("expected error containing '%s' but got '%s'", tt.expectedError, err.Error())
			}
		})
	}
}

func TestSignChallenge_Success(t *testing.T) {

	mockCard, err := NewMockMuunCardV2()
	if err != nil {
		t.Fatalf("failed NewMockMuunCardV2: %v", err)
	}

	mockNfcBridge := NewMockJavaCard(mockCard)
	card := NewCardV2(mockNfcBridge)

	// First, pair the card to establish a slot
	serverKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}
	clientKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}

	pairingResp, err := card.Pair(serverKeyPair.PublicKey, clientKeyPair.PublicKey)
	if err != nil {
		t.Fatalf("card pairing failed: %v", err)
	}

	challengeKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed to generate challenge key pair: %v", err)
	}

	ephemeralPublicKey := challengeKeyPair.PublicKey
	counter := uint16(1)
	index := uint16(0)
	reason := []byte("test reason 1")

	challenge := generateChallenge(
		serverKeyPair.PrivateKey,
		pairingResp.CardPublicKey,
		ephemeralPublicKey,
		reason,
		counter,
		index,
	)
	response, err := card.SignChallenge(challenge, reason)
	if err != nil {
		t.Fatalf("signChallenge failed: %v", err)
	}

	// Verify basic response structure
	err = cryptography.ValidateSecp256r1PublicKey(response.CardPublicKey)
	if err != nil {
		t.Fatalf("failed public key validation, got %v", err)
	}

	// Sign challenge mac response validation is tested in a full integration test with a mock
	// client that  performs the full protocol. See nfc_integration_test.go.
}

func TestSignChallenge_SecretValidForRetries(t *testing.T) {

	// challenge 1 should work with initial secret
	// Signing process never finishes, shared secret still valid.
	// challenge 2 should work with initial secret

	mockCard, err := NewMockMuunCardV2()
	if err != nil {
		t.Fatalf("failed NewMockMuunCardV2: %v", err)
	}

	mockNfcBridge := NewMockJavaCard(mockCard)
	card := NewCardV2(mockNfcBridge)

	// First, pair the card to establish a slot
	serverKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}
	clientKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}

	pairingResp, err := card.Pair(serverKeyPair.PublicKey, clientKeyPair.PublicKey)
	if err != nil {
		t.Fatalf("card pairing failed: %v", err)
	}

	challengeKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed to generate challenge key pair: %v", err)
	}

	ephemeralPublicKey := challengeKeyPair.PublicKey
	counter := uint16(1)
	index := uint16(0)
	reason := []byte("test reason 1")

	challenge := generateChallenge(
		serverKeyPair.PrivateKey,
		pairingResp.CardPublicKey,
		ephemeralPublicKey,
		reason,
		counter,
		index,
	)
	response, err := card.SignChallenge(challenge, reason)
	if err != nil {
		t.Fatalf("signChallenge failed: %v", err)
	}

	// Verify basic response structure
	err = cryptography.ValidateSecp256r1PublicKey(response.CardPublicKey)
	if err != nil {
		t.Fatalf("failed public key validation, got %v", err)
	}

	// Signing process never finishes, shared secret still valid.

	// challenge 2: Use same initial MAC secret
	counter += 1
	reason2 := []byte("test reason 2")

	challenge2 := generateChallenge(
		serverKeyPair.PrivateKey,
		pairingResp.CardPublicKey,
		ephemeralPublicKey,
		reason2,
		counter,
		index,
	)
	response2, err := card.SignChallenge(challenge2, reason2)
	if err != nil {
		t.Fatalf("signChallenge failed: %v", err)
	}

	// Verify basic response structure
	err = cryptography.ValidateSecp256r1PublicKey(response2.CardPublicKey)
	if err != nil {
		t.Fatalf("failed public key validation, got %v", err)
	}
}

//==========================
// Private, helper functions
//==========================

func generateChallenge(
	serverPrivateKey []byte,
	cardPublicKey []byte,
	ephemeralPublicKey []byte,
	reason []byte,
	counter uint16,
	index uint16,
) *SignChallenge {

	// derive initial shared secret
	sharedPoint, err := cryptography.ECDH(serverPrivateKey, cardPublicKey)

	// knowingly panicking, instead of ignoring error ;), to simplify code. Probably a programmer
	// error when choosing test inputs.
	if err != nil {
		panic(err)
	}

	// Derive secret_card = SHA256(shared_x)
	sharedSecret := sha256.Sum256(sharedPoint)

	mac := MakeChallengeSignMac(sharedSecret[:], ephemeralPublicKey, reason, counter, index)

	return &SignChallenge{
		ServerPublicKey: ephemeralPublicKey,
		Mac:             mac,
		PairingSlot:     index,
		CardUsageCount:  counter,
	}
}
