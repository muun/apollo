package test

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

func TestParsePairingResponseV3_ErrorScenarios(t *testing.T) {

	// P(65) + index(2) + metadata(1) + MAC(32) = 100
	minSizeValid := nfc.Secp256r1PointSize + nfc.PairIndexSize +
		nfc.PairingMetadataMinSizeV3 + nfc.MACSize

	tests := []struct {
		name        string
		data        func() []byte
		expectError string
	}{
		{
			name:        "empty data",
			data:        func() []byte { return []byte{} },
			expectError: "invalid v3 pair response length: 0 (expected at least 100)",
		},
		{
			name: "only pair core, no metadata",
			data: func() []byte {
				return make([]byte, nfc.Secp256r1PointSize+nfc.PairIndexSize+nfc.MACSize)
			},
			expectError: "invalid v3 pair response length: 99 (expected at least 100)",
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
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := nfc.ParsePairingResponseV3(tt.data())
			require.Error(t, err)
			require.Nil(t, result)
			assert.ErrorContains(t, err, tt.expectError)
		})
	}
}

func TestParsePairingResponseV3_Success(t *testing.T) {

	metadata := nfc.RandomBytes(13)
	validData := createPairingResponseDataV3(t, metadata)

	result, err := nfc.ParsePairingResponseV3(validData)
	require.NoError(t, err)
	require.NotNil(t, result)

	// Verify structure sizes
	require.Len(t, result.CardPublicKey, nfc.Secp256r1PointSize)
	require.Len(t, result.MAC, nfc.MACSize)

	// Verify index content
	require.Equal(t, uint16(0x1234), result.Index)

	// Verify card public key is valid
	require.NoError(t, cryptography.ValidateSecp256r1PublicKey(result.CardPublicKey))

	// Metadata comes bundled in the pair response, as the card emitted it.
	require.Equal(t, metadata, result.Metadata)
	require.Equal(t, validData[len(validData)-nfc.MACSize:], result.MAC)
}

func TestParsePairingResponseV3_MetadataIsNotRead(t *testing.T) {
	// Under MAC-last framing (P || index || metadata || MAC) the metadata is
	// whatever sits between the index and the MAC. The parser hands it over
	// as is, whatever its length and content: reading it is the server's
	// job, which is what lets one client pair every firmware generation.
	for _, metadataLength := range []int{1, 13, 219, 400} {
		t.Run(fmt.Sprintf("%d bytes of metadata", metadataLength), func(t *testing.T) {
			metadata := nfc.RandomBytes(metadataLength)
			validData := createPairingResponseDataV3(t, metadata)

			result, err := nfc.ParsePairingResponseV3(validData)
			require.NoError(t, err)
			require.Equal(t, metadata, result.Metadata)
			require.Equal(t, validData[len(validData)-nfc.MACSize:], result.MAC)
		})
	}
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
			name:        "response one byte short of the header",
			data:        make([]byte, nfc.SignChallengeResponseHeaderSizeV3-1),
			expectError: "invalid v3 sign challenge response length: 98",
		},
		{
			name:        "response carrying no signature",
			data:        make([]byte, nfc.SignChallengeResponseHeaderSizeV3),
			expectError: "carries no commitment signature",
		},
		{
			name: "signature longer than DER can encode",
			data: func() []byte {
				data := make([]byte, nfc.SignChallengeResponseHeaderSizeV3+nfc.MaxSignatureSizeV3+1)
				binary.BigEndian.PutUint16(
					data[nfc.Secp256r1PointSize+nfc.MACSize:],
					nfc.MaxSignatureSizeV3+1,
				)
				return data
			}(),
			expectError: "signature length 73 exceeds the 72",
		},
		{
			name: "declared signature longer than the bytes that follow",
			data: func() []byte {
				data := make([]byte, nfc.SignChallengeResponseHeaderSizeV3)
				binary.BigEndian.PutUint16(data[nfc.Secp256r1PointSize+nfc.MACSize:], 10)
				return data
			}(),
			expectError: "expected 109 for a 10-byte signature",
		},
		{
			name:        "invalid card public key - all zeros",
			data:        createSignChallengeResponseDataV3(nfc.MaxSignatureSizeV3),
			expectError: "invalid card public key in v3 challenge response:",
		},
		{
			name: "invalid card public key - wrong prefix",
			data: func() []byte {
				data := createSignChallengeResponseDataV3(nfc.MaxSignatureSizeV3)
				data[0] = 0x03 // Invalid prefix for uncompressed point (should be 0x04)
				return data
			}(),
			expectError: "invalid card public key in v3 challenge response:",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := nfc.ParseSignChallengeResponseV3(tt.data)
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

	// P(65) || MAC(32) || sig_len(2) || commitment_sig
	testMAC := nfc.RandomBytes(nfc.MACSize)
	testSignature := nfc.RandomBytes(70) // a typical DER length

	responseData := append([]byte{}, cardKeyPair.PublicKey...)
	responseData = append(responseData, testMAC...)
	responseData = binary.BigEndian.AppendUint16(responseData, uint16(len(testSignature)))
	responseData = append(responseData, testSignature...)

	result, err := nfc.ParseSignChallengeResponseV3(responseData)
	require.NoError(t, err)
	require.NotNil(t, result)

	require.Equal(t, cardKeyPair.PublicKey, result.CardPublicKey)
	require.Equal(t, testMAC, result.MAC)
	require.Equal(t, testSignature, result.CommitmentSig)
}

func TestBuildSignChallengeDataV3_Layout(t *testing.T) {
	// Distinct, non-overlapping byte patterns so any mis-offset shows up.
	serverC := bytes.Repeat([]byte{0xAA}, nfc.Secp256r1PointSize) // 65 × 0xAA
	reason := []byte{0xCC, 0xDD, 0xEE}
	mac := bytes.Repeat([]byte{0xBB}, nfc.MACSize) // 32 × 0xBB

	data := nfc.BuildSignChallengeDataV3(
		serverC,
		0x01020304, // call_counter
		0x0506,     // index
		len(reason),
		reason,
		mac,
	)

	// Wire format: C(65) || call_counter(4) || index(2) || reason_len(2) || reason || MAC(32)
	expected := []byte{}
	expected = append(expected, serverC...)
	expected = append(expected, 0x01, 0x02, 0x03, 0x04) // call_counter big-endian
	expected = append(expected, 0x05, 0x06)             // index big-endian
	expected = append(expected, 0x00, 0x03)             // reason_len big-endian
	expected = append(expected, reason...)
	expected = append(expected, mac...)

	require.Equal(t, expected, data)
	require.Len(t, data, nfc.SignChallengeHeaderSizeV3+len(reason)+nfc.MACSize)
}

func TestBuildSignChallengeDataV3_EmptyReason(t *testing.T) {
	// With an empty reason the header declares zero and the MAC follows it
	// directly, which is the shape every challenge has today.
	serverC := bytes.Repeat([]byte{0xAA}, nfc.Secp256r1PointSize)
	mac := bytes.Repeat([]byte{0xBB}, nfc.MACSize)

	data := nfc.BuildSignChallengeDataV3(serverC, 0x01020304, 0x0506, 0, nil, mac)

	require.Len(t, data, nfc.SignChallengeHeaderSizeV3+nfc.MACSize)

	reasonLengthAt := nfc.Secp256r1PointSize + nfc.CallCounterSizeV3 + nfc.PairIndexSize
	require.Equal(t, []byte{0x00, 0x00}, data[reasonLengthAt:nfc.SignChallengeHeaderSizeV3])
	require.Equal(t, byte(0xBB), data[nfc.SignChallengeHeaderSizeV3])
}

func TestBuildSignChallengeDataV3_LongestSingleShotReason(t *testing.T) {
	// 150 bytes is what fits alongside the header and the MAC in one short
	// APDU, so it is the longest reason this form can carry.
	serverC := bytes.Repeat([]byte{0xAA}, nfc.Secp256r1PointSize)
	reason := bytes.Repeat([]byte{0xCC}, nfc.MaxReasonChunkSizeV3)
	mac := bytes.Repeat([]byte{0xBB}, nfc.MACSize)

	data := nfc.BuildSignChallengeDataV3(serverC, 0, 0, len(reason), reason, mac)

	require.Len(t, data, nfc.MaxShortAPDUDataSize)
	require.Equal(
		t,
		reason,
		data[nfc.SignChallengeHeaderSizeV3:nfc.SignChallengeHeaderSizeV3+len(reason)],
	)

	// reason_len declares the reason's own length, which is what lets the
	// card find the MAC behind it.
	reasonLengthAt := nfc.Secp256r1PointSize + nfc.CallCounterSizeV3 + nfc.PairIndexSize
	require.Equal(
		t,
		uint16(nfc.MaxReasonChunkSizeV3),
		binary.BigEndian.Uint16(data[reasonLengthAt:nfc.SignChallengeHeaderSizeV3]),
	)
}

// TODO add tests for derivePairingSecretV3 and the V3 MAC builders when the
// mock exposes them in the next PR

//==========================
// Private, helper functions
//==========================

// createPairingResponseDataV3 builds a pair response with the given
// metadata block, laid out as P || index || metadata || MAC (MAC last).
func createPairingResponseDataV3(t *testing.T, metadata []byte) []byte {
	t.Helper()
	cardKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)

	data := make([]byte, 0, nfc.Secp256r1PointSize+nfc.PairIndexSize+len(metadata)+nfc.MACSize)
	data = append(data, cardKeyPair.PublicKey...)        // P (65 bytes)
	data = append(data, 0x12, 0x34)                      // index big-endian
	data = append(data, metadata...)                     // metadata (variable)
	data = append(data, nfc.RandomBytes(nfc.MACSize)...) // MAC (32 bytes, last)
	return data
}

// createSignChallengeResponseDataV3 builds a response envelope declaring a
// signature of the given length, for the cases that have to reach the checks
// past the length ones.
func createSignChallengeResponseDataV3(signatureLength int) []byte {
	data := make([]byte, nfc.SignChallengeResponseHeaderSizeV3+signatureLength)
	binary.BigEndian.PutUint16(data[nfc.Secp256r1PointSize+nfc.MACSize:], uint16(signatureLength))
	return data
}
