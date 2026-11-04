package simulator

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseMetadataV3_0_ErrorScenarios(t *testing.T) {

	tests := []struct {
		name        string
		data        []byte
		expectError string
	}{
		{
			name:        "empty data",
			data:        []byte{},
			expectError: "invalid metadata length: 0 (expected at least 13)",
		},
		{
			name:        "data one byte short of the no-certificate length",
			data:        make([]byte, metadataMinSizeV3_0-1),
			expectError: "invalid metadata length: 12 (expected at least 13)",
		},
		{
			name: "certificate longer than a card can store",
			data: func() []byte {
				// Declares one byte more than MAX_CERT_SIZE, with the
				// bytes present so only the bound rejects it.
				certificate := randomBytes(t, vendorCertificateMaxSizeV3_0+1)
				return generateMetadataV3_0(t, certificate)
			}(),
			expectError: "certificate length 244 exceeds the 243 a card can store",
		},
		{
			name: "certificate length larger than the bytes that follow",
			data: func() []byte {
				// Declares a 100-byte certificate and then ends.
				data := generateMetadataV3_0(t, nil)
				binary.BigEndian.PutUint16(data, 100)
				return data
			}(),
			expectError: "invalid metadata length: 13 (expected 113 for a 100-byte certificate)",
		},
		{
			name: "bytes past the device status",
			// The card stands behind its metadata and nothing appended to it,
			// so a trailing byte is refused rather than ignored.
			data:        append(generateMetadataV3_0(t, nil), 0x00),
			expectError: "invalid metadata length: 14 (expected 13 for a 0-byte certificate)",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := parseMetadataV3_0(tt.data)
			require.Error(t, err)
			require.Nil(t, result)
			assert.ErrorContains(t, err, tt.expectError)
		})
	}
}

func TestParseMetadataV3_0_Success_NoCert(t *testing.T) {

	validData := generateValidMetadataV3_0(t)

	result, err := parseMetadataV3_0(validData)
	require.NoError(t, err)

	verifyTestMetadataV3_0(t, result)

	// Before STORE_CERTIFICATE the card writes cert_len = 0, so there is
	// no certificate to report.
	require.Nil(t, result.Certificate)
}

func TestParseMetadataV3_0_Success_WithCert(t *testing.T) {

	certificate := randomBytes(t, 200)
	validData := generateMetadataV3_0(t, certificate)

	result, err := parseMetadataV3_0(validData)
	require.NoError(t, err)

	verifyTestMetadataV3_0(t, result)

	// The certificate travels as an opaque blob, so it has to come back
	// byte for byte.
	require.Equal(t, certificate, result.Certificate)
}

func TestParseMetadataV3_0_CertificateLengthBoundaries(t *testing.T) {
	// 0 is the pre-ceremony shape and 243 is MetadataApplet.MAX_CERT_SIZE,
	// so these are the two ends of what a card can hand back.
	for _, certificateLength := range []int{0, 1, vendorCertificateMaxSizeV3_0} {
		t.Run(fmt.Sprintf("certificate_length_%d", certificateLength), func(t *testing.T) {
			validData := generateMetadataV3_0(t, randomBytes(t, certificateLength))

			result, err := parseMetadataV3_0(validData)
			require.NoError(t, err)
			require.NotNil(t, result)
			require.Len(t, result.Certificate, certificateLength)
		})
	}
}

func TestNewMetadataV3_0_RejectsCertificateLongerThanACardStores(t *testing.T) {
	certificate := randomBytes(t, vendorCertificateMaxSizeV3_0+1)
	metadata, err := newMetadataV3_0(
		certificate,
		testChallengeCounterV3_0,
		testMetadataVersionV3_0,
		testFirmwareVersionV3_0,
		testDeviceStatusReadyV3_0,
	)
	require.Error(t, err)
	require.Nil(t, metadata)
	assert.ErrorContains(t, err, "certificate length 244 exceeds the 243 a card can store")
}

func TestMetadataV3_0_MetadataForAuth_Layout(t *testing.T) {
	// A distinct byte pattern per field, so any mis-offset shows up.
	certificate := bytes.Repeat([]byte{0xAA}, 200)
	metadata, err := newMetadataV3_0(
		certificate,
		0x0102,
		[4]byte{0x03, 0x04, 0x05, 0x06},
		[4]byte{0x07, 0x08, 0x09, 0x0A},
		testDeviceStatusReadyV3_0,
	)
	require.NoError(t, err)

	certificateHash := sha256.Sum256(certificate)

	expected := []byte{}
	expected = append(expected, certificateHash[:]...)
	expected = append(expected, 0x01, 0x02)             // challenge_counter, big-endian
	expected = append(expected, 0x03, 0x04, 0x05, 0x06) // metadata_version
	expected = append(expected, 0x07, 0x08, 0x09, 0x0A) // firmware_version
	expected = append(expected, 0x04)                   // device_status

	forAuth, err := metadata.metadataForAuth()
	require.NoError(t, err)
	require.Equal(t, expected, forAuth)
	require.Len(t, expected, metadataAuthSizeV3_0)
}

func TestMetadataV3_0_MetadataForAuth_MatchesTransmissionTrailer(t *testing.T) {
	// The two forms differ only in their first field, so everything past the
	// certificate has to come out byte-identical. Drift there is what would
	// silently break the pairing MAC.
	certificate := randomBytes(t, 200)
	wire := generateMetadataV3_0(t, certificate)
	metadata, err := parseMetadataV3_0(wire)
	require.NoError(t, err)

	forAuth, err := metadata.metadataForAuth()
	require.NoError(t, err)
	require.Len(t, forAuth, metadataAuthSizeV3_0)

	certificateHash := sha256.Sum256(certificate)
	require.Equal(t, certificateHash[:], forAuth[:sha256.Size])

	require.Equal(
		t,
		wire[len(wire)-metadataTrailerSizeV3_0:],
		forAuth[len(forAuth)-metadataTrailerSizeV3_0:],
	)
}

func TestMetadataV3_0_MetadataForAuth_NoCertificate(t *testing.T) {
	// The card refuses this too, so producing a hash of nothing here would
	// invent an authentication form no card would ever stand behind.
	metadata, err := parseMetadataV3_0(generateValidMetadataV3_0(t))
	require.NoError(t, err)
	require.Nil(t, metadata.Certificate)

	forAuth, err := metadata.metadataForAuth()
	require.Error(t, err)
	require.Nil(t, forAuth)
	assert.ErrorContains(t, err, "no certificate to authenticate")
}

//==========================
// Private, helper functions
//==========================

// The trailer values the metadata builders write. Kept next to the verifier
// so the two cannot drift apart.
var (
	testMetadataVersionV3_0 = [4]byte{0x00, 0x01, 0x00, 0x00} // 1.0
	testFirmwareVersionV3_0 = [4]byte{0x00, 0x03, 0x00, 0x00} // 3.0
)

const (
	testChallengeCounterV3_0 = uint16(0x0304)

	// testDeviceStatusReadyV3_0 is MetadataApplet's STATUS_READY, the only
	// status in which a card pairs.
	testDeviceStatusReadyV3_0 = byte(0x04)
)

// generateMetadataV3_0 builds a metadata block carrying the given
// certificate:
//
//	cert_len(2) || certificate || challenge_counter(2) ||
//	metadata_version(4) || firmware_version(4) || device_status(1)
//
// A nil certificate produces the pre-ceremony shape, where the card writes
// cert_len = 0.
func generateMetadataV3_0(t *testing.T, certificate []byte) []byte {
	t.Helper()

	data := make([]byte, 0, metadataMinSizeV3_0+len(certificate))
	data = binary.BigEndian.AppendUint16(data, uint16(len(certificate)))
	data = append(data, certificate...)
	data = binary.BigEndian.AppendUint16(data, testChallengeCounterV3_0)
	data = append(data, testMetadataVersionV3_0[:]...)
	data = append(data, testFirmwareVersionV3_0[:]...)
	data = append(data, testDeviceStatusReadyV3_0)
	return data
}

func generateValidMetadataV3_0(t *testing.T) []byte {
	t.Helper()
	return generateMetadataV3_0(t, nil)
}

func verifyTestMetadataV3_0(t *testing.T, result *metadataV3_0) {
	t.Helper()
	require.NotNil(t, result)

	require.Equal(t, testChallengeCounterV3_0, result.ChallengeCounter)
	require.Equal(t, testMetadataVersionV3_0, result.MetadataVersion)
	require.Equal(t, testFirmwareVersionV3_0, result.FirmwareVersion)
	require.Equal(t, testDeviceStatusReadyV3_0, result.DeviceStatus)
}

// randomBytes returns count cryptographically random bytes.
func randomBytes(t *testing.T, count int) []byte {
	t.Helper()
	data := make([]byte, count)
	_, err := rand.Read(data)
	require.NoError(t, err)
	return data
}
