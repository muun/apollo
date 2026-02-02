package nfc

import (
	"github.com/muun/libwallet/cryptography"
	"testing"
)

// TODO another reason why "reason" (yes pun intended) should be part of SignChallenge
type SignChallengeData struct {
	challenge     *SignChallenge
	reason        []byte
	hasMoreChunks byte
}

// TODO add TestMockCardPairCard_ErrorScenarios
// TODO add TestMockCardPairCard_Success

func TestMockCardSignChallengeSingle_ErrorScenarios(t *testing.T) {
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
		name string
		// For flexibility, you can use one of apduData or challengeMapper, leave the other undefined.
		apduData           func() []byte
		challengeMapper    func(challengeData *SignChallengeData) *SignChallengeData
		expectedStatusCode uint16
	}{
		{
			name:               "MockCardSignChallengeEmptyData",
			apduData:           func() []byte { return []byte{} },
			expectedStatusCode: swMuuncardV2WrongLength,
		},
		{
			// Test APDU too short
			// (minimum is 102 bytes: C(65) + count(2) + index(2) + has_more_chunks(1) + mac(32))
			name:               "MockCardSignChallengeWrongLength",
			apduData:           func() []byte { return make([]byte, 101) },
			expectedStatusCode: swMuuncardV2WrongLength,
		},
		{
			name: "MockCardSignChallengeInvalidPublicKey",
			challengeMapper: func(challengeData *SignChallengeData) *SignChallengeData {

				invalidPubKey := make([]byte, 65)
				copy(invalidPubKey, challengeKeyPair.PublicKey)
				invalidPubKey[0] = 0x03 // Compressed format (invalid)
				challengeData.challenge.ServerPublicKey = invalidPubKey
				return challengeData
			},
			expectedStatusCode: swMuuncardV2InvalidPubKey,
		},
		{
			name: "MockCardSignChallengeInvalidCounter",
			challengeMapper: func(challengeData *SignChallengeData) *SignChallengeData {

				// Use counter 0 (should be > current counter which starts at 0)
				challengeData.challenge.CardUsageCount = uint16(0)
				return challengeData
			},
			expectedStatusCode: swMuuncardV2InvalidCounter,
		},
		{
			name: "MockCardSignChallengeInvalidMac",
			challengeMapper: func(challengeData *SignChallengeData) *SignChallengeData {

				challengeData.challenge.Mac = make([]byte, 32) // All zeros - invalid MAC
				return challengeData
			},
			expectedStatusCode: swMuuncardV2InvalidMac,
		},
		{
			name: "MockCardSignChallengeInvalidHasMoreChunks",
			challengeMapper: func(challengeData *SignChallengeData) *SignChallengeData {

				challengeData.hasMoreChunks = byte(2) // Invalid value (should be 0 or 1)
				return challengeData
			},
			expectedStatusCode: swWrongData,
		},
		{
			name: "MockCardSignChallengeIncorrectHasMoreChunks",
			challengeMapper: func(challengeData *SignChallengeData) *SignChallengeData {

				// hasMoreChunks is true but reason is shorter
				challengeData.hasMoreChunks = byte(1)
				return challengeData
			},
			expectedStatusCode: swInsNotSupported,
		},
		{
			name: "MockCardSignChallengeIncorrectHasMoreChunks_2",
			challengeMapper: func(challengeData *SignChallengeData) *SignChallengeData {

				// hasMoreChunks is false but reason is greater than 1 chunk
				challengeData.hasMoreChunks = byte(0)
				// more than max apdu size, definitely should be split
				challengeData.reason = make([]byte, 256)
				return challengeData
			},
			// For reasons that doesn't fit in 1 chunk, we should use streaming apdus (and split in
			// chunks). Apdus have a 1-byte length field which allows max 255 bytes data. When
			// larger data array is passed, length gets truncated or encoded incorrectly, causing
			// WrongLength error.
			expectedStatusCode: swMuuncardV2WrongLength,
		},
		{
			name: "MockCardSignChallengeHasMoreChunks",
			challengeMapper: func(challengeData *SignChallengeData) *SignChallengeData {

				challengeData.hasMoreChunks = byte(1)
				// more than apdu size, definitely should be split
				challengeData.reason = make([]byte, 256)
				return challengeData
			},
			// For reasons that doesn't fit in 1 chunk, we should use streaming apdus (and split in
			// chunks). Apdus have a 1-byte length field which allows max 255 bytes data. When
			// larger data array is passed, length gets truncated or encoded incorrectly, causing
			// WrongLength error.
			expectedStatusCode: swMuuncardV2WrongLength},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {

			var apduData []byte
			if tt.apduData != nil {
				apduData = tt.apduData()
			} else if tt.challengeMapper != nil {

				ephemeralPublicKey := challengeKeyPair.PublicKey
				counter := uint16(1)
				index := uint16(0)
				hasMoreChunks := byte(0)
				reason := []byte("test reason")

				challengeData := &SignChallengeData{
					challenge: generateChallenge(
						serverKeyPair.PrivateKey,
						pairingResp.CardPublicKey,
						ephemeralPublicKey,
						reason,
						counter,
						index,
					),
					reason:        reason,
					hasMoreChunks: hasMoreChunks,
				}

				challengeData = tt.challengeMapper(challengeData)

				apduData = buildSignChallengeData(
					challengeData.challenge.ServerPublicKey, // C (65 bytes)
					challengeData.challenge.CardUsageCount,  // counter (2 bytes, big-endian)
					challengeData.challenge.PairingSlot,     // pairingSlot (2 bytes, big-endian)
					challengeData.hasMoreChunks,             // has_more_chunks = 0 (single chunk)
					challengeData.reason,                    // reason
					challengeData.challenge.Mac,             // mac (32 bytes)
				)

			} else {
				t.Fatalf("invalid input for test. Either apduData or challengeMapper must be defined")
			}

			response, err := card.rawCard.transmit(buildSignChallengeAPDU(apduData).serialize())

			if err != nil {
				t.Errorf("transmit failed: %v", err)
			}

			if response.StatusCode != tt.expectedStatusCode {
				t.Errorf("expected error %x, got %x", tt.expectedStatusCode, response.StatusCode)
			}
		})
	}
}

func TestMockCardSignChallengeSingle_Success(t *testing.T) {
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
	hasMoreChunks := byte(0)
	reason := []byte("test reason")

	challenge := generateChallenge(
		serverKeyPair.PrivateKey,
		pairingResp.CardPublicKey,
		ephemeralPublicKey,
		reason,
		counter,
		index,
	)

	apduData := buildSignChallengeData(
		challenge.ServerPublicKey, // C (65 bytes)
		challenge.CardUsageCount,  // counter (2 bytes, big-endian)
		challenge.PairingSlot,     // pairingSlot (2 bytes, big-endian)
		hasMoreChunks,             // has_more_chunks = 0 (single chunk)
		reason,                    // reason
		challenge.Mac,             // mac (32 bytes)
	)

	response, err := card.rawCard.transmit(buildSignChallengeAPDU(apduData).serialize())

	if err != nil {
		t.Errorf("transmit failed: %v", err)
	}

	if response.StatusCode != responseOk {
		t.Errorf("expected %x, got %x", responseOk, response.StatusCode)
	}
}

// TODO add TestMockCardSignChallengeStreaming_ErrorScenarios
// TODO add TestMockCardSignChallengeStreamingSuccess
