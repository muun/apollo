package simulator

import (
	"bytes"
	"crypto/sha256"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestCardGenerationV3_1_ParseMetadata(t *testing.T) {
	certificate := specCertificateV3_1(t, specCertificatePayloadV3_1(t))
	metadataBytes := generateMetadataV3_1(t, certificate, testKeyAgreementPubV3_1(t))

	metadata, err := cardGenerationV3_1{}.ParseMetadata(metadataBytes)
	require.NoError(t, err)

	// The authentication form carries the versions, the certificate's hash
	// and the status: what the card MACs.
	certificateHash := sha256.Sum256(certificate)
	expectedForAuth := append([]byte{}, testMetadataVersionV3_1[:]...)
	expectedForAuth = append(expectedForAuth, testFirmwareVersionV3_1[:]...)
	expectedForAuth = append(expectedForAuth, certificateHash[:]...)
	expectedForAuth = append(expectedForAuth, testDeviceStatusReadyV3_1)
	require.Equal(t, expectedForAuth, metadata.MetadataForAuth)

	// The key agreement key comes out of the metadata, decompressed, and
	// the commitment key out of the certificate.
	generator := decodeHex(t, "04"+p256GeneratorX+p256GeneratorY)
	require.Equal(t, generator, metadata.KeyAgreementPub)
	require.Equal(t, generator, metadata.CommitmentPub)

	// The short device id is the device model and the last 4 bytes of the
	// root.
	rootTail := testKeyAgreementRootV3_1[len(testKeyAgreementRootV3_1)-4:]
	expectedShortDeviceID := append([]byte{}, testDeviceModelIDV3_1...)
	expectedShortDeviceID = append(expectedShortDeviceID, rootTail...)
	require.Equal(t, expectedShortDeviceID, metadata.ShortDeviceID)

	require.Equal(t, uint16(1), metadata.JSON.MetadataVersionMajor)
	require.Equal(t, uint16(3), metadata.JSON.FirmwareVersionMajor)
	require.Equal(t, uint16(1), metadata.JSON.FirmwareVersionMinor)
	require.Equal(t, uint16(0), metadata.JSON.ChallengeCounter)
	require.Equal(t, testDeviceStatusReadyV3_1, metadata.JSON.DeviceStatus)
}

func TestCardGenerationV3_1_RefusesTheOtherGenerationsLayout(t *testing.T) {
	// A 3.0 metadata carries the certificate first, so under this layout its
	// len(vendor_certificate) lands on certificate bytes. Houston relies on a wrong reading
	// failing here or at the MAC, never on it passing by accident.
	metadataBytes := generateMetadataV3_0(t, bytes.Repeat([]byte{0xAA}, 200))

	_, err := cardGenerationV3_1{}.ParseMetadata(metadataBytes)
	require.Error(t, err)
}

func TestCardGenerationsV3_NewestFirst(t *testing.T) {
	// A pairing is attributed to the first generation that reads it, so the
	// order is part of the table.
	names := make([]string, 0, len(cardGenerationsV3))
	for _, generation := range cardGenerationsV3 {
		names = append(names, generation.Name())
	}
	require.Equal(t, []string{"3.1", "3.0"}, names)
}
