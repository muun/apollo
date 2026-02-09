package nfc

import (
	"fmt"
	"github.com/muun/libwallet/cryptography"
	"strings"
	"testing"
)

func TestParseMetadata_ErrorScenarios(t *testing.T) {

	tests := []struct {
		name        string
		data        []byte
		expectError string
	}{
		{
			name:        "empty data",
			data:        []byte{},
			expectError: "invalid metadata length: 0 (expected 75)",
		},
		{
			name:        "data too short - 74 bytes (one byte less than required)",
			data:        make([]byte, 74),
			expectError: "invalid metadata length: 74 (expected 75)",
		},
		{
			name: "invalid public key format - wrong prefix",
			data: func() []byte {
				data := make([]byte, MetadataSize)
				data[0] = 0x03 // Invalid prefix for uncompressed point (should be 0x04)
				return data
			}(),
			expectError: "invalid card global public key:",
		},
		{
			name: "invalid public key format - all zeros",
			data: func() []byte {
				data := make([]byte, MetadataSize)
				data[0] = 0x04 // Valid prefix for uncompressed point but all the rest are zeros
				return data
			}(),
			expectError: "invalid card global public key:",
		},
		{
			name:        "all zeros",
			data:        make([]byte, MetadataSize), // All zeros, including the public key
			expectError: "invalid card global public key:",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := parseMetadata(tt.data)

			if err == nil {
				t.Errorf("expected error but got nil")
				return
			}

			if result != nil {
				t.Errorf("expected nil result but got %v", result)
			}

			if !strings.Contains(err.Error(), tt.expectError) {
				t.Errorf("expected error containing '%s' but got '%s'", tt.expectError, err.Error())
			}
		})
	}
}

func TestParseMetadata_Success(t *testing.T) {

	// Create valid metadata with exactly MetadataSize bytes
	validData, err := generateValidMetadata(t)
	if err != nil {
		t.Fatalf("failed to generate valid metadata: %v", err)
	}

	result, err := parseMetadata(validData)

	if err != nil {
		t.Fatalf("expected no error but got: %v", err)
	}

	verifyTestMetadata(t, result)
}

func TestParseMetadata_MoreThanMetadataSize(t *testing.T) {

	// Test that more than MetadataSize bytes still works (we only read first MetadataSize bytes)
	// Exactly as success case but appending 10 extra random bytes
	validData, err := generateValidMetadata(t)
	if err != nil {
		t.Fatalf("failed to generate valid metadata: %v", err)
	}

	// 10 extra random bytes
	validData = append(validData, randomBytes(10)...)

	result, err := parseMetadata(validData)

	if err != nil {
		t.Fatalf("expected no error but got: %v", err)
	}

	verifyTestMetadata(t, result)
}

func TestParsePairingResponse_ErrorScenarios(t *testing.T) {

	tests := []struct {
		name        string
		data        func() []byte
		expectError string
	}{
		{
			name:        "empty data",
			data:        func() []byte { return []byte{} },
			expectError: "response too short: 0 bytes",
		},
		{
			name:        "data too short - exactly PairResponseSize minus 1",
			data:        func() []byte { return make([]byte, PairResponseSize-1) },
			expectError: "response too short:",
		},
		{
			name: "invalid card public key",
			data: func() []byte {
				data := make([]byte, PairResponseSize+70) // Add valid signature length
				// Leave card public key as all zeros (invalid)
				// Fill rest with valid data structure
				data[65] = 0x12 // Pairing slot
				data[66] = 0x34

				// Add valid metadata with valid global public key
				globalKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
				if err != nil {
					t.Fatalf("failed to generate global key pair: %v", err)
				}
				copy(data[67:67+65], globalKeyPair.PublicKey)

				// Add valid signature length (70 bytes) at the end
				copy(data[len(data)-70:], randomBytes(70))

				return data
			},
			expectError: "invalid card public key:",
		},
		{
			name: "invalid metadata - all zeros global public key",
			data: func() []byte {
				data := make([]byte, PairResponseSize+70) // Add valid signature length

				// Add valid card public key
				cardKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
				if err != nil {
					t.Fatalf("failed to generate card key pair: %v", err)
				}
				copy(data[0:65], cardKeyPair.PublicKey)

				// Pairing slot
				data[65] = 0x12
				data[66] = 0x34

				// Metadata with invalid global public key (all zeros)
				// Leave metadata area as zeros (invalid global public key)

				// Add valid signature length (70 bytes) at the end
				copy(data[len(data)-70:], randomBytes(70))

				return data
			},
			expectError: "failed to parse metadata:",
		},
		{
			name: "global signature too short - 69 bytes",
			data: func() []byte {
				validData := createValidPairingResponseData(t)
				// Remove last byte to make signature 69 bytes (invalid)
				return validData[:len(validData)-1]
			},
			expectError: "invalid global signature length",
		},
		{
			name: "global signature too long - 73 bytes",
			data: func() []byte {
				validData := createValidPairingResponseData(t)
				// Add 3 extra bytes to make signature 73 bytes (70 + 3 = 73, invalid)
				return append(validData, 0xFF, 0xFF, 0xFF)
			},
			expectError: "invalid global signature length",
		},
		{
			name: "exactly minimum response size but no signature",
			data: func() []byte {
				// Create data that's exactly PairResponseSize but all zeros (invalid metadata)
				data := make([]byte, PairResponseSize)
				// Add valid card public key to get past card key validation
				cardKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
				if err != nil {
					t.Fatalf("failed to generate card key pair: %v", err)
				}
				copy(data[0:65], cardKeyPair.PublicKey)
				// Leave rest as zeros - this will fail on metadata parsing before signature
				return data
			},
			expectError: "failed to parse metadata",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			data := tt.data()
			result, err := parsePairingResponse(data)

			if err == nil {
				t.Errorf("expected error but got nil")
				return
			}

			if result != nil {
				t.Errorf("expected nil result but got %v", result)
			}

			if !strings.Contains(err.Error(), tt.expectError) {
				t.Errorf("expected error containing '%s' but got '%s'", tt.expectError, err.Error())
			}
		})
	}
}

func TestParsePairingResponse_Success(t *testing.T) {

	validData := createValidPairingResponseData(t)

	result, err := parsePairingResponse(validData)

	if err != nil {
		t.Fatalf("expected no error but got: %v", err)
	}

	if result == nil {
		t.Fatalf("expected non-nil result")
	}

	// Verify structure sizes
	if len(result.CardPublicKey) != Secp256R1PointSize {
		t.Fatalf(
			"CardPublicKey wrong size: got %d, want %d",
			len(result.CardPublicKey),
			Secp256R1PointSize,
		)
	}

	if len(result.PairingSlot) != PairingSlotSize {
		t.Fatalf(
			"PairingSlot wrong size: got %d, want %d", len(result.PairingSlot), PairingSlotSize,
		)
	}

	if result.Metadata == nil {
		t.Fatalf("Metadata should not be nil")
	}

	if len(result.MAC) != MacSize {
		t.Fatalf("MAC wrong size: got %d, want %d", len(result.MAC), MacSize)
	}

	if len(result.GlobalSignature) != 70 {
		t.Fatalf("GlobalSignature wrong size: got %d, want 70", len(result.GlobalSignature))
	}

	// Verify pairing slot content
	if result.PairingSlot[0] != 0x12 || result.PairingSlot[1] != 0x34 {
		t.Fatalf("PairingSlot not parsed correctly: got [%02x, %02x], want [0x12, 0x34]",
			result.PairingSlot[0], result.PairingSlot[1])
	}

	// Verify card public key is valid
	err = cryptography.ValidateSecp256r1PublicKey(result.CardPublicKey)
	if err != nil {
		t.Fatalf("CardPublicKey should be valid: %v", err)
	}

	// Verify metadata was parsed correctly
	verifyTestMetadata(t, result.Metadata)
}

func TestParsePairingResponse_ValidSignatureLengths(t *testing.T) {
	// Test all valid signature lengths (70, 71, 72 bytes)
	for sigLen := 70; sigLen <= 72; sigLen++ {
		t.Run(fmt.Sprintf("signature_length_%d", sigLen), func(t *testing.T) {
			// Create base data with 70-byte signature
			baseData := createValidPairingResponseData(t)

			// Adjust to desired signature length
			if sigLen > 70 {
				// Add extra bytes
				extraBytes := sigLen - 70
				baseData = append(baseData, randomBytes(extraBytes)...)
			} else if sigLen < 70 {
				// Remove bytes (this case won't execute since we start at 70)
				removeBytes := 70 - sigLen
				baseData = baseData[:len(baseData)-removeBytes]
			}

			result, err := parsePairingResponse(baseData)

			if err != nil {
				t.Errorf("signature length %d should be valid but got error: %v", sigLen, err)
				return
			}

			if result == nil {
				t.Errorf("expected non-nil result for signature length %d", sigLen)
				return
			}

			if len(result.GlobalSignature) != sigLen {
				t.Errorf(
					"GlobalSignature length: got %d, want %d", len(result.GlobalSignature), sigLen,
				)
			}
		})
	}
}

func TestParsePairingResponse_LargerThanMinimumSize(t *testing.T) {
	// Test that data larger than minimum still works (the function reads the remaining bytes as signature)
	// Since parsePairingResponse reads "remaining bytes" as signature, extra data becomes part of signature
	// This should fail if signature becomes > 72 bytes
	baseData := createValidPairingResponseData(t) // This has 70-byte signature

	// Add 2 extra bytes to make signature 72 bytes (still valid)
	extraData := append(baseData, randomBytes(2)...)

	result, err := parsePairingResponse(extraData)

	if err != nil {
		t.Fatalf("expected no error for 72-byte signature but got: %v", err)
	}

	if result == nil {
		t.Fatalf("expected non-nil result")
	}

	// Should still parse correctly
	if result.PairingSlot[0] != 0x12 || result.PairingSlot[1] != 0x34 {
		t.Fatalf("PairingSlot not parsed correctly with extra data: got [%02x, %02x], want [0x12, 0x34]",
			result.PairingSlot[0], result.PairingSlot[1])
	}

	// Signature should now be 72 bytes
	if len(result.GlobalSignature) != 72 {
		t.Fatalf("GlobalSignature length: got %d, want 72", len(result.GlobalSignature))
	}
}

func TestParseSignChallengeResponse_ErrorScenarios(t *testing.T) {

	tests := []struct {
		name        string
		response    *CardResponse
		expectError string
	}{
		{
			name: "response too short - empty",
			response: &CardResponse{
				Response:   []byte{},
				StatusCode: responseOk,
			},
			expectError: "invalid sign challenge response length: 0",
		},
		{
			name: "response too short - 96 bytes (one less than required)",
			response: &CardResponse{
				Response:   make([]byte, SignChallengeResponseSize-1),
				StatusCode: responseOk,
			},
			expectError: "invalid sign challenge response length: 96",
		},
		{
			name: "response too long - 98 bytes",
			response: &CardResponse{
				Response:   make([]byte, SignChallengeResponseSize+1),
				StatusCode: responseOk,
			},
			expectError: "invalid sign challenge response length: 98",
		},
		{
			name: "invalid card public key - all zeros",
			response: &CardResponse{
				Response:   make([]byte, SignChallengeResponseSize), // All zeros
				StatusCode: responseOk,
			},
			expectError: "invalid card public key:",
		},
		{
			name: "invalid card public key - wrong prefix",
			response: func() *CardResponse {
				data := make([]byte, SignChallengeResponseSize)
				data[0] = 0x03 // Invalid prefix for uncompressed point (should be 0x04)
				return &CardResponse{
					Response:   data,
					StatusCode: responseOk,
				}
			}(),
			expectError: "invalid card public key:",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := parseSignChallengeResponse(tt.response)

			if err == nil {
				t.Errorf("expected error but got nil")
				return
			}

			if result != nil {
				t.Errorf("expected nil result but got %v", result)
			}

			if !strings.Contains(err.Error(), tt.expectError) {
				t.Errorf("expected error containing '%s' but got '%s'", tt.expectError, err.Error())
			}
		})
	}
}

func TestParseSignChallengeResponse_Success(t *testing.T) {

	// Create valid sign challenge response
	cardKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed to generate card key pair: %v", err)
	}

	// Create response data: card public key (65 bytes) + MAC (32 bytes)
	responseData := make([]byte, SignChallengeResponseSize)
	copy(responseData[0:65], cardKeyPair.PublicKey)

	// Fill MAC with test data
	testMAC := randomBytes(MacSize)
	copy(responseData[65:], testMAC)

	response := &CardResponse{
		Response:   responseData,
		StatusCode: responseOk,
	}

	result, err := parseSignChallengeResponse(response)

	if err != nil {
		t.Fatalf("expected no error but got: %v", err)
	}

	if result == nil {
		t.Fatalf("expected non-nil result")
	}

	// Verify structure sizes
	if len(result.CardPublicKey) != Secp256R1PointSize {
		t.Fatalf("CardPublicKey wrong size: got %d, want %d",
			len(result.CardPublicKey), Secp256R1PointSize)
	}

	if len(result.MAC) != MacSize {
		t.Fatalf("MAC wrong size: got %d, want %d", len(result.MAC), MacSize)
	}

	// Verify card public key is valid
	err = cryptography.ValidateSecp256r1PublicKey(result.CardPublicKey)
	if err != nil {
		t.Fatalf("CardPublicKey should be valid: %v", err)
	}

	// TODO: mac verification will tested in another test

	// Verify MAC content matches
	for i := 0; i < MacSize; i++ {
		if result.MAC[i] != testMAC[i] {
			t.Fatalf("MAC byte %d not parsed correctly: got 0x%02x, want 0x%02x",
				i, result.MAC[i], testMAC[i])
		}
	}

	// Verify the card public key matches what we put in
	for i := 0; i < Secp256R1PointSize; i++ {
		if result.CardPublicKey[i] != cardKeyPair.PublicKey[i] {
			t.Fatalf("CardPublicKey byte %d not parsed correctly: got 0x%02x, want 0x%02x",
				i, result.CardPublicKey[i], cardKeyPair.PublicKey[i])
		}
	}
}

// TODO add tests for MakeChallengeSignMac (test vectors and expected Macs, error scenarios)
// TODO add tests for ComputeHMACSHA256 (test vectors and expected Macs, error scenarios)

//==========================
// Private, helper functions
//==========================

func generateValidMetadata(t *testing.T) ([]byte, error) {
	validData := make([]byte, MetadataSize)

	// Fill with a valid uncompressed secp256r1 point format
	pair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed key pair generation: %v", err)
	}

	copy(validData[0:65], pair.PublicKey)

	// Card Vendor (2 bytes)
	validData[65] = 0x12
	validData[66] = 0x34

	// Card Model (2 bytes)
	validData[67] = 0x56
	validData[68] = 0x78

	// Firmware Version (2 bytes)
	validData[69] = 0x01
	validData[70] = 0x02

	// Usage Count (2 bytes, big-endian) - e.g., 0x0304 = 772
	validData[71] = 0x03
	validData[72] = 0x04

	// Language Code (2 bytes)
	validData[73] = 0xAB
	validData[74] = 0xCD
	return validData, nil
}

func verifyTestMetadata(t *testing.T, result *CardMetadata) {
	// If it passes validation, verify parsing correctness
	if result == nil {
		t.Fatalf("expected non-nil result")
	}

	err := cryptography.ValidateSecp256r1PublicKey(result.GlobalPubCard[:])
	if err != nil {
		t.Fatalf("expected valid GlobalPubCard, got 0x%02x", result.GlobalPubCard[0])
	}

	// Verify parsing correctness
	if result.CardVendor[0] != 0x12 || result.CardVendor[1] != 0x34 {
		t.Fatalf("CardVendor not parsed correctly: got [%02x, %02x], want [0x12, 0x34]",
			result.CardVendor[0], result.CardVendor[1])
	}

	if result.CardModel[0] != 0x56 || result.CardModel[1] != 0x78 {
		t.Fatalf("CardModel not parsed correctly: got [%02x, %02x], want [0x56, 0x78]",
			result.CardModel[0], result.CardModel[1])
	}

	if result.FirmwareVersion[0] != 0x01 || result.FirmwareVersion[1] != 0x02 {
		t.Fatalf("FirmwareVersion not parsed correctly: got [%02x, %02x], want [0x01, 0x02]",
			result.FirmwareVersion[0], result.FirmwareVersion[1])
	}

	expectedUsageCount := uint16(0x0304) // 772
	if result.UsageCount != expectedUsageCount {
		t.Fatalf("UsageCount not parsed correctly: got %d, want %d",
			result.UsageCount, expectedUsageCount)
	}

	if result.LanguageCode[0] != 0xAB || result.LanguageCode[1] != 0xCD {
		t.Fatalf("LanguageCode not parsed correctly: got [%02x, %02x], want [0xAB, 0xCD]",
			result.LanguageCode[0], result.LanguageCode[1])
	}
}

// Helper function to create a valid pairing response for testing
func createValidPairingResponseData(t *testing.T) []byte {
	// Generate valid card public key
	cardKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		t.Fatalf("failed to generate card key pair: %v", err)
	}

	data := make([]byte, 0, PairResponseSize+72) // 72 is max signature size

	// Card Public Key (65 bytes)
	data = append(data, cardKeyPair.PublicKey...)

	// Pairing Slot (2 bytes)
	data = append(data, 0x12, 0x34)

	// Metadata (75 bytes)
	metadata, err := generateValidMetadata(t)
	if err != nil {
		t.Fatalf("failed to generate metadata: %v", err)
	}
	data = append(data, metadata...)

	// MAC (32 bytes)
	data = append(data, randomBytes(MacSize)...)

	// Global Signature (70 bytes - valid length)
	data = append(data, randomBytes(70)...)

	return data
}
