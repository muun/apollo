package nfc

import (
	"bytes"
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/cryptography"
)

func TestParseMetadataV3_ErrorScenarios(t *testing.T) {

	tests := []struct {
		name        string
		data        []byte
		expectError string
	}{
		{
			name:        "empty data",
			data:        []byte{},
			expectError: "invalid metadata length: 0 (expected at least 141)",
		},
		{
			name:        "data too short - 140 bytes (one byte less than required)",
			data:        make([]byte, MetadataMinSizeV3-1),
			expectError: "invalid metadata length: 140 (expected at least 141)",
		},
		{
			name: "invalid attestation pub - wrong prefix",
			data: func() []byte {
				data := make([]byte, MetadataMinSizeV3)
				data[0] = 0x03 // Invalid prefix for uncompressed point (should be 0x04)
				return data
			}(),
			expectError: "invalid attestation pub:",
		},
		{
			name: "invalid attestation pub - all zeros",
			data: func() []byte {
				data := make([]byte, MetadataMinSizeV3)
				data[0] = 0x04 // Valid prefix for uncompressed point but all the rest are zeros
				return data
			}(),
			expectError: "invalid attestation pub:",
		},
		{
			name:        "all zeros",
			data:        make([]byte, MetadataMinSizeV3), // All zeros
			expectError: "invalid attestation pub:",
		},
		{
			name: "provider sig length too large - 73 bytes",
			data: func() []byte {
				// 141 + 72 + 1 so the reader has bytes available, but sig_len exceeds MaxProviderSigSize.
				data := make([]byte, MetadataMinSizeV3+MaxProviderSigSize+1)
				pair, err := cryptography.GenerateSecp256r1PKeyPair()
				require.NoError(t, err)
				copy(data[0:65], pair.PublicKey)
				data[MetadataMinSizeV3-1] = 73 // sig_len is the last byte of the fixed prefix
				return data
			}(),
			expectError: "provider sig length 73 exceeds max 72",
		},
		{
			name: "sig_len greater than remaining bytes",
			data: func() []byte {
				// Declares sig_len = 10 but no signature bytes follow.
				data := make([]byte, MetadataMinSizeV3)
				pair, err := cryptography.GenerateSecp256r1PKeyPair()
				require.NoError(t, err)
				copy(data[0:65], pair.PublicKey)
				data[MetadataMinSizeV3-1] = 10
				return data
			}(),
			expectError: "read provider sig:",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := ParseMetadataV3(tt.data)
			require.Error(t, err)
			require.Nil(t, result)
			assert.ErrorContains(t, err, tt.expectError)
		})
	}
}

func TestParseMetadataV3_Success_NoCert(t *testing.T) {

	// Create valid metadata with exactly MetadataMinSizeV3 bytes (no certificate)
	validData := generateValidMetadataV3(t)

	result, err := ParseMetadataV3(validData)
	require.NoError(t, err)

	verifyTestMetadataV3(t, result)

	// Before STORE_CERTIFICATE: provider_pub is zeros, sig_len = 0, sig nil
	require.Zero(t, result.ProviderSigLen)
	require.Nil(t, result.ProviderSig)

	// RawBytes should snapshot the 141 bytes consumed
	require.Len(t, result.RawBytes, MetadataMinSizeV3)
}

func TestParseMetadataV3_Success_WithCert(t *testing.T) {

	// Create valid metadata with a 70-byte signature (typical DER length)
	sigLen := byte(70)
	validData := generateValidMetadataV3WithCert(t, sigLen)

	result, err := ParseMetadataV3(validData)
	require.NoError(t, err)

	verifyTestMetadataV3(t, result)

	require.Equal(t, sigLen, result.ProviderSigLen)
	require.Len(t, result.ProviderSig, int(sigLen))

	// ProviderPub should not be all zeros after STORE_CERTIFICATE
	require.NoError(
		t,
		cryptography.ValidateSecp256r1PublicKey(result.ProviderPub[:]),
		"ProviderPub should be a valid point after STORE_CERTIFICATE",
	)

	// RawBytes snapshots 141 + sig_len bytes
	expectedRawLen := MetadataMinSizeV3 + int(sigLen)
	require.Len(t, result.RawBytes, expectedRawLen)

	// Verify ProviderSig was copied byte-for-byte from the input.
	expectedSig := validData[MetadataMinSizeV3 : MetadataMinSizeV3+int(sigLen)]
	require.Equal(t, expectedSig, result.ProviderSig)

	// Verify RawBytes matches the input prefix exactly. The pairing MAC
	// is computed over RawBytes, so any drift here would break it.
	require.Equal(t, validData[:expectedRawLen], result.RawBytes)
}

func TestParseMetadataV3_ValidSignatureLengths(t *testing.T) {
	// Test all valid signature lengths (0 = no cert, then 1..MaxProviderSigSize)
	for sigLen := 0; sigLen <= MaxProviderSigSize; sigLen++ {
		t.Run(fmt.Sprintf("signature_length_%d", sigLen), func(t *testing.T) {
			var validData []byte
			if sigLen == 0 {
				validData = generateValidMetadataV3(t)
			} else {
				validData = generateValidMetadataV3WithCert(t, byte(sigLen))
			}

			result, err := ParseMetadataV3(validData)
			require.NoError(t, err)
			require.NotNil(t, result)
			require.Equal(t, sigLen, int(result.ProviderSigLen))
		})
	}
}

func TestParseMetadataV3_MoreThanMetadataSize(t *testing.T) {

	// Test that more than the consumed length still works (trailing bytes ignored,
	// and not included in RawBytes since the MAC only binds the metadata).
	validData := generateValidMetadataV3(t)

	// 10 extra random bytes
	validData = append(validData, randomBytes(10)...)

	result, err := ParseMetadataV3(validData)
	require.NoError(t, err)

	verifyTestMetadataV3(t, result)

	// Trailing bytes must NOT bleed into RawBytes
	require.Len(t, result.RawBytes, MetadataMinSizeV3)
}

func TestParsePairingResponseV3_ErrorScenarios(t *testing.T) {

	minSizeValid := PairResponseSizeV3 + MetadataMinSizeV3 // 99 + 141 = 240

	tests := []struct {
		name        string
		data        func() []byte
		expectError string
	}{
		{
			name:        "empty data",
			data:        func() []byte { return []byte{} },
			expectError: "invalid v3 pair response length: 0 (expected at least 240)",
		},
		{
			name:        "only pair core, no metadata",
			data:        func() []byte { return make([]byte, PairResponseSizeV3) },
			expectError: "invalid v3 pair response length: 99 (expected at least 240)",
		},
		{
			name:        "data one byte short of minimum",
			data:        func() []byte { return make([]byte, minSizeValid-1) },
			expectError: "invalid v3 pair response length: 239 (expected at least 240)",
		},
		{
			name: "invalid card public key - all zeros",
			data: func() []byte {
				return make([]byte, minSizeValid)
			},
			expectError: "invalid card public key in v3 pair response:",
		},
		{
			name: "invalid card public key - wrong prefix",
			data: func() []byte {
				data := make([]byte, minSizeValid)
				data[0] = 0x03 // Invalid prefix for uncompressed point
				return data
			},
			expectError: "invalid card public key in v3 pair response:",
		},
		{
			name: "valid pair core but invalid trailing metadata",
			data: func() []byte {
				cardKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
				require.NoError(t, err)
				data := make([]byte, minSizeValid)
				copy(data, cardKeyPair.PublicKey)
				// Metadata bytes stay zero-filled → attestation pub is
				// all zeros, which parseMetadataV3 rejects.
				return data
			},
			expectError: "parse metadata in v3 pair response:",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := parsePairingResponseV3(tt.data())
			require.Error(t, err)
			require.Nil(t, result)
			assert.ErrorContains(t, err, tt.expectError)
		})
	}
}

func TestParsePairingResponseV3_Success(t *testing.T) {

	validData := createValidPairingResponseDataV3(t)

	result, err := parsePairingResponseV3(validData)
	require.NoError(t, err)
	require.NotNil(t, result)

	// Verify structure sizes
	require.Len(t, result.CardPublicKey, Secp256R1PointSize)
	require.Len(t, result.MAC, MacSize)

	// Verify index content
	require.Equal(t, uint16(0x1234), result.Index)

	// Verify card public key is valid
	require.NoError(t, cryptography.ValidateSecp256r1PublicKey(result.CardPublicKey))

	// Metadata now comes bundled in the pair response.
	verifyTestMetadataV3(t, &result.Metadata)
}

func TestParsePairingResponseV3_Success_WithCert(t *testing.T) {
	// Ensures the parser handles the variable-length metadata tail
	// correctly when a provider certificate is present.
	sigLen := byte(70)
	metadata := generateValidMetadataV3WithCert(t, sigLen)
	validData := createPairingResponseDataV3(t, metadata)

	result, err := parsePairingResponseV3(validData)
	require.NoError(t, err)
	require.NotNil(t, result)

	verifyTestMetadataV3(t, &result.Metadata)
	require.Equal(t, sigLen, result.Metadata.ProviderSigLen)
	require.Len(t, result.Metadata.ProviderSig, int(sigLen))

	// RawBytes must capture the full metadata block: base + sig.
	require.Len(t, result.Metadata.RawBytes, MetadataMinSizeV3+int(sigLen))
	require.Equal(t, metadata, result.Metadata.RawBytes)
}

func TestParsePairingResponseV3_TrailingBytes(t *testing.T) {
	// Under MAC-last framing (P || index || metadata || MAC) the MAC is
	// positioned by the metadata length, so the response must be exactly
	// sized. Trailing bytes would shift what gets read as the MAC and
	// corrupt verification, so the parser must reject them. The card never
	// pads, so this only guards against malformed input.
	validData := createValidPairingResponseDataV3(t)
	dataWithTrailing := append(validData, randomBytes(10)...)

	_, err := parsePairingResponseV3(dataWithTrailing)
	require.Error(t, err)
}

func TestParseSignChallengeResponseV3_ErrorScenarios(t *testing.T) {

	tests := []struct {
		name        string
		data        []byte
		expectError string
	}{
		{
			name:        "response too short - empty",
			data:        []byte{},
			expectError: "invalid v3 sign challenge response length: 0",
		},
		{
			name:        "response too short - 96 bytes (one less than required)",
			data:        make([]byte, SignChallengeResponseSize-1),
			expectError: "invalid v3 sign challenge response length: 96",
		},
		{
			name:        "response too long - 98 bytes",
			data:        make([]byte, SignChallengeResponseSize+1),
			expectError: "invalid v3 sign challenge response length: 98",
		},
		{
			name:        "invalid card public key - all zeros",
			data:        make([]byte, SignChallengeResponseSize),
			expectError: "invalid card public key in v3 challenge response:",
		},
		{
			name: "invalid card public key - wrong prefix",
			data: func() []byte {
				data := make([]byte, SignChallengeResponseSize)
				data[0] = 0x03 // Invalid prefix for uncompressed point (should be 0x04)
				return data
			}(),
			expectError: "invalid card public key in v3 challenge response:",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := parseSignChallengeResponseV3(tt.data)
			require.Error(t, err)
			require.Nil(t, result)
			assert.ErrorContains(t, err, tt.expectError)
		})
	}
}

func TestParseSignChallengeResponseV3_Success(t *testing.T) {

	// Create valid sign challenge response
	cardKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)

	// Create response data: card public key (65 bytes) + MAC (32 bytes)
	responseData := make([]byte, SignChallengeResponseSize)
	copy(responseData[0:65], cardKeyPair.PublicKey)

	// Fill MAC with test data
	testMAC := randomBytes(MacSize)
	copy(responseData[65:], testMAC)

	result, err := parseSignChallengeResponseV3(responseData)
	require.NoError(t, err)
	require.NotNil(t, result)

	// Verify structure sizes
	require.Len(t, result.CardPublicKey, Secp256R1PointSize)
	require.Len(t, result.MAC, MacSize)

	// Verify card public key is valid
	require.NoError(t, cryptography.ValidateSecp256r1PublicKey(result.CardPublicKey))

	// Please note: MAC verification will be tested in another test.
	require.Equal(t, testMAC, result.MAC)
	require.Equal(t, cardKeyPair.PublicKey, result.CardPublicKey)
}

func TestBuildSignChallengeDataV3_Layout(t *testing.T) {
	// Distinct, non-overlapping byte patterns so any mis-offset shows up.
	serverC := bytes.Repeat([]byte{0xAA}, Secp256R1PointSize) // 65 × 0xAA
	reason := []byte{0xCC, 0xDD, 0xEE}
	mac := bytes.Repeat([]byte{0xBB}, MacSize) // 32 × 0xBB

	data := buildSignChallengeDataV3(
		serverC,
		0x0102, // counter
		0x0304, // index
		reason,
		mac,
	)

	// Wire format: C(65) || counter(2) || index(2) || has_more(1) || reason || MAC(32)
	expected := []byte{}
	expected = append(expected, serverC...)
	expected = append(expected, 0x01, 0x02) // counter big-endian
	expected = append(expected, 0x03, 0x04) // index big-endian
	expected = append(expected, 0x00)       // has_more = 0
	expected = append(expected, reason...)
	expected = append(expected, mac...)

	require.Equal(t, expected, data)
	require.Len(t, data, Secp256R1PointSize+CounterSize+PairIndexSize+1+len(reason)+MacSize)
}

func TestBuildSignChallengeDataV3_EmptyReason(t *testing.T) {
	// With an empty reason, the has_more byte sits directly between the
	// index and the MAC.
	serverC := bytes.Repeat([]byte{0xAA}, Secp256R1PointSize)
	mac := bytes.Repeat([]byte{0xBB}, MacSize)

	data := buildSignChallengeDataV3(serverC, 0x0102, 0x0304, nil, mac)

	require.Len(t, data, Secp256R1PointSize+CounterSize+PairIndexSize+1+MacSize)
	// Byte right after index (offset 65+2+2=69) is has_more = 0; the MAC
	// follows it.
	require.Equal(t, byte(0x00), data[Secp256R1PointSize+CounterSize+PairIndexSize])
	require.Equal(t, byte(0xBB), data[Secp256R1PointSize+CounterSize+PairIndexSize+1])
}

func TestBuildSignChallengeDataV3_LargeReason(t *testing.T) {
	// Extended APDUs remove the old 153-byte cap. A 500-byte reason must
	// be embedded verbatim, without truncation or wrapping.
	serverC := bytes.Repeat([]byte{0xAA}, Secp256R1PointSize)
	reason := bytes.Repeat([]byte{0xCC}, 500)
	mac := bytes.Repeat([]byte{0xBB}, MacSize)

	data := buildSignChallengeDataV3(serverC, 0, 0, reason, mac)

	require.Len(t, data, Secp256R1PointSize+CounterSize+PairIndexSize+1+500+MacSize)
	// Reason should appear verbatim between the has_more byte (offset 70)
	// and the MAC.
	reasonStart := Secp256R1PointSize + CounterSize + PairIndexSize + 1
	require.Equal(t, reason, data[reasonStart:reasonStart+500])
}

// TODO add tests for derivePairingSecretV3 and the V3 MAC builders when the
// mock exposes them in the next PR

//==========================
// Private, helper functions
//==========================

func generateValidMetadataV3(t *testing.T) []byte {
	t.Helper()
	// No-cert layout: 141 bytes, with provider_pub left as zeros and sig_len = 0.
	validData := make([]byte, MetadataMinSizeV3)

	// Attestation pub (65 bytes)
	pair, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)
	copy(validData[0:65], pair.PublicKey)

	// Card Vendor (2 bytes)
	validData[65] = 0x12
	validData[66] = 0x34

	// Card Model (2 bytes)
	validData[67] = 0x56
	validData[68] = 0x78

	// Firmware Version (2 bytes)
	validData[69] = 0x03
	validData[70] = 0x00

	// Capabilities (2 bytes)
	validData[71] = 0xAB
	validData[72] = 0xCD

	// Operation Count (2 bytes, big-endian) - e.g., 0x0304 = 772
	validData[73] = 0x03
	validData[74] = 0x04

	// Provider Pub (65 bytes) and sig_len (1 byte) are left as zeros (no cert).

	return validData
}

// providerPubOffsetV3 is where provider_pub starts inside the V3
// metadata layout: attestation_pub(65) + vendor(2) + model(2) +
// firmware(2) + capabilities(2) + op_counter(2) = 75.
const providerPubOffsetV3 = Secp256R1PointSize + 2 + 2 + 2 + 2 + 2

// sigLenOffsetV3 is where the 1-byte sig_len field sits.
const sigLenOffsetV3 = providerPubOffsetV3 + Secp256R1PointSize

func generateValidMetadataV3WithCert(t *testing.T, sigLen byte) []byte {
	t.Helper()
	base := generateValidMetadataV3(t)

	// Provider Pub (65 bytes) - replace zeros with a valid point
	providerKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)
	copy(base[providerPubOffsetV3:sigLenOffsetV3], providerKeyPair.PublicKey)

	// sig_len (1 byte)
	base[sigLenOffsetV3] = sigLen

	// signature (variable length, 0..72 bytes) at the end
	if sigLen > 0 {
		base = append(base, randomBytes(int(sigLen))...)
	}
	return base
}

func verifyTestMetadataV3(t *testing.T, result *CardMetadataV3) {
	t.Helper()
	require.NotNil(t, result)

	require.NoError(
		t,
		cryptography.ValidateSecp256r1PublicKey(result.AttestationPub[:]),
		"expected valid AttestationPub, first byte was 0x%02x", result.AttestationPub[0],
	)

	require.Equal(t, [2]byte{0x12, 0x34}, result.CardVendor)
	require.Equal(t, [2]byte{0x56, 0x78}, result.CardModel)
	require.Equal(t, [2]byte{0x03, 0x00}, result.FirmwareVersion)
	require.Equal(t, [2]byte{0xAB, 0xCD}, result.Capabilities)
	require.Equal(t, uint16(0x0304), result.OperationCount)
}

// createValidPairingResponseDataV3 builds a full V3 pairing response as it
// arrives on the wire once the firmware speaks extended APDUs:
// P(65) || index(2) || metadata(141) || MAC(32) = 240 bytes.
func createValidPairingResponseDataV3(t *testing.T) []byte {
	t.Helper()
	return createPairingResponseDataV3(t, generateValidMetadataV3(t))
}

// createPairingResponseDataV3 builds a pair response with the given
// metadata block, laid out as P || index || metadata || MAC (MAC last).
// Callers pick between the no-cert metadata (141 bytes) and the with-cert
// metadata (141 + sigLen bytes).
func createPairingResponseDataV3(t *testing.T, metadata []byte) []byte {
	t.Helper()
	cardKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)

	data := make([]byte, 0, Secp256R1PointSize+PairIndexSize+len(metadata)+MacSize)
	data = append(data, cardKeyPair.PublicKey...) // P (65 bytes)
	data = append(data, 0x12, 0x34)               // index big-endian
	data = append(data, metadata...)              // metadata (variable)
	data = append(data, randomBytes(MacSize)...)  // MAC (32 bytes, last)
	return data
}
