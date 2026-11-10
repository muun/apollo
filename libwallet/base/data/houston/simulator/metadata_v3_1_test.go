package simulator

import (
	"bytes"
	"crypto/sha256"
	"encoding/binary"
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseMetadataV3_1_ErrorScenarios(t *testing.T) {

	tests := []struct {
		name        string
		data        []byte
		expectError string
	}{
		{
			name:        "empty data",
			data:        []byte{},
			expectError: "invalid metadata length: 0 (expected at least 44)",
		},
		{
			name:        "data one byte short of the no-certificate length",
			data:        make([]byte, metadataMinSizeV3_1-1),
			expectError: "invalid metadata length: 43 (expected at least 44)",
		},
		{
			name: "certificate longer than a card can store",
			data: func() []byte {
				// Declares one byte more than the slot, with the bytes
				// present so only the bound rejects it.
				certificate := randomBytes(t, vendorCertificateMaxSizeV3_1+1)
				return generateMetadataV3_1(t, certificate, testKeyAgreementPubV3_1(t))
			}(),
			expectError: "certificate length 419 exceeds the 418 a card can store",
		},
		{
			name: "certificate length larger than the bytes that follow",
			data: func() []byte {
				// Declares a 100-byte certificate and then ends.
				data := generateMetadataV3_1(t, nil, testKeyAgreementPubV3_1(t))
				binary.BigEndian.PutUint16(data[2*versionSizeV3_1:], 100)
				return data
			}(),
			expectError: "invalid metadata length: 44 (expected 144 for a 100-byte certificate)",
		},
		{
			name: "bytes past the device status",
			// The card stands behind its metadata and nothing appended to it,
			// so a trailing byte is refused rather than ignored.
			data: append(
				generateMetadataV3_1(t, nil, testKeyAgreementPubV3_1(t)), 0x00,
			),
			expectError: "invalid metadata length: 45 (expected 44 for a 0-byte certificate)",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := parseMetadataV3_1(tt.data)
			require.Error(t, err)
			require.Nil(t, result)
			assert.ErrorContains(t, err, tt.expectError)
		})
	}
}

func TestParseMetadataV3_1_Success_WithCert(t *testing.T) {
	certificate := randomBytes(t, 350)
	keyAgreementPub := testKeyAgreementPubV3_1(t)
	validData := generateMetadataV3_1(t, certificate, keyAgreementPub)

	result, err := parseMetadataV3_1(validData)
	require.NoError(t, err)

	require.Equal(t, testMetadataVersionV3_1, result.MetadataVersion)
	require.Equal(t, testFirmwareVersionV3_1, result.FirmwareVersion)
	require.Equal(t, testDeviceStatusReadyV3_1, result.DeviceStatus)

	// The certificate and the key travel as emitted, so they have to come
	// back byte for byte.
	require.Equal(t, certificate, result.Certificate)
	require.Equal(t, keyAgreementPub, result.KeyAgreementPub)
}

func TestParseMetadataV3_1_CertificateLengthBoundaries(t *testing.T) {
	// 0 is the pre-ceremony shape and 418 is the certificate slot, so these
	// are the two ends of what a card can hand back.
	for _, certificateLength := range []int{0, 1, vendorCertificateMaxSizeV3_1} {
		t.Run(fmt.Sprintf("certificate_length_%d", certificateLength), func(t *testing.T) {
			validData := generateMetadataV3_1(
				t, randomBytes(t, certificateLength), testKeyAgreementPubV3_1(t),
			)

			result, err := parseMetadataV3_1(validData)
			require.NoError(t, err)
			require.Len(t, result.Certificate, certificateLength)
		})
	}
}

func TestMetadataV3_1_MetadataForAuth_Layout(t *testing.T) {
	// A distinct byte pattern per field, so any mis-offset shows up. The key
	// agreement key is on the wire and not in the authentication form.
	certificate := bytes.Repeat([]byte{0xAA}, 200)
	metadata, err := newMetadataV3_1(
		[4]byte{0x03, 0x04, 0x05, 0x06},
		[4]byte{0x07, 0x08, 0x09, 0x0A},
		certificate,
		testKeyAgreementPubV3_1(t),
		testDeviceStatusReadyV3_1,
	)
	require.NoError(t, err)

	certificateHash := sha256.Sum256(certificate)

	expected := []byte{0x03, 0x04, 0x05, 0x06}          // metadata_version
	expected = append(expected, 0x07, 0x08, 0x09, 0x0A) // firmware_version
	expected = append(expected, certificateHash[:]...)
	expected = append(expected, 0x04) // device_status

	forAuth, err := metadata.metadataForAuth()
	require.NoError(t, err)
	require.Equal(t, expected, forAuth)
	require.Len(t, expected, metadataAuthSizeV3_1)
}

func TestMetadataV3_1_MetadataForAuth_NoCertificate(t *testing.T) {
	// The card refuses this too, so producing a hash of nothing here would
	// invent an authentication form no card would ever stand behind.
	metadata, err := parseMetadataV3_1(generateMetadataV3_1(t, nil, testKeyAgreementPubV3_1(t)))
	require.NoError(t, err)
	require.Nil(t, metadata.Certificate)

	forAuth, err := metadata.metadataForAuth()
	require.Error(t, err)
	require.Nil(t, forAuth)
	assert.ErrorContains(t, err, "no certificate to authenticate")
}

func TestMetadataV3_1_KeyAgreementPubUncompressed(t *testing.T) {
	// The key travels compressed, as the generator with its odd y, so
	// decompression has to land on exactly 0x04 || Gx || Gy.
	metadata, err := parseMetadataV3_1(
		generateMetadataV3_1(t, randomBytes(t, 200), testKeyAgreementPubV3_1(t)),
	)
	require.NoError(t, err)

	uncompressed, err := metadata.keyAgreementPubUncompressed()
	require.NoError(t, err)
	require.Equal(t, decodeHex(t, "04"+p256GeneratorX+p256GeneratorY), uncompressed)
}

func TestMetadataV3_1_KeyAgreementPubUncompressed_RefusesAPointOffTheCurve(t *testing.T) {
	offCurve := append([]byte{0x02}, bytes.Repeat([]byte{0xFF}, p256CoordinateSize)...)
	metadata, err := parseMetadataV3_1(generateMetadataV3_1(t, randomBytes(t, 200), offCurve))
	require.NoError(t, err)

	uncompressed, err := metadata.keyAgreementPubUncompressed()
	require.Error(t, err)
	require.Nil(t, uncompressed)
	assert.ErrorContains(t, err, "key agreement key:")
}

//==========================
// Private, helper functions
//==========================

// The values the metadata builders write. Kept next to the verifier so the
// two cannot drift apart.
var (
	testMetadataVersionV3_1 = [4]byte{0x00, 0x01, 0x00, 0x00} // 1.0
	testFirmwareVersionV3_1 = [4]byte{0x00, 0x03, 0x00, 0x01} // 3.1
)

// testDeviceStatusReadyV3_1 is READY, the only status in which a card pairs.
const testDeviceStatusReadyV3_1 = byte(0x04)

// testKeyAgreementPubV3_1 is P-256's generator, compressed: its y is odd.
func testKeyAgreementPubV3_1(t *testing.T) []byte {
	t.Helper()
	return decodeHex(t, "03"+p256GeneratorX)
}

// generateMetadataV3_1 builds a metadata block carrying the given
// certificate and key agreement key:
//
//	metadata_version(4) || firmware_version(4) || len(vendor_certificate)(2) ||
//	certificate || key_agreement_pub(33) || device_status(1)
//
// A nil certificate produces the pre-ceremony shape, where the card writes
// len(vendor_certificate) = 0.
func generateMetadataV3_1(t *testing.T, certificate []byte, keyAgreementPub []byte) []byte {
	t.Helper()

	data := make([]byte, 0, metadataMinSizeV3_1+len(certificate))
	data = append(data, testMetadataVersionV3_1[:]...)
	data = append(data, testFirmwareVersionV3_1[:]...)
	data = binary.BigEndian.AppendUint16(data, uint16(len(certificate)))
	data = append(data, certificate...)
	data = append(data, keyAgreementPub...)
	data = append(data, testDeviceStatusReadyV3_1)
	return data
}
