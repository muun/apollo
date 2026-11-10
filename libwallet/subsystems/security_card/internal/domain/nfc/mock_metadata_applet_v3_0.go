package nfc

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography"
)

// mockMetadataVersionMajorV3_0 and mockMetadataVersionMinorV3_0 mirror
// MetadataApplet.METADATA_VERSION_MAJOR and METADATA_VERSION_MINOR.
const (
	mockMetadataVersionMajorV3_0 = 1
	mockMetadataVersionMinorV3_0 = 0
)

// Wire sizes of the metadata the applet writes. The transmission form is
// cert_len(2) || cert || trailer, and the authentication form is
// sha256(cert) || trailer, where the trailer is challenge_counter(2) ||
// metadata_version(4) || firmware_version(4) || device_status(1).
const (
	certificateLengthSizeV3_0 = 2
	metadataTrailerSizeV3_0   = 2 + 2*VersionSizeV3 + 1
	metadataAuthSizeV3_0      = sha256.Size + metadataTrailerSizeV3_0
)

// mockMetadataAppletV3_0 is the metadata applet of firmware 3.0: one key
// agreement key, one certificate carrying both of the card's keys, and the
// metadata laid out with the certificate first and the versions last.
//
// It covers what the pairing needs. libwallet never runs an initialization
// ceremony, so the constructor runs one of its own and leaves the applet
// where STORE_CERTIFICATE does: keys, certificate and short device id stored,
// READY.
type mockMetadataAppletV3_0 struct {
	deviceStatus DeviceStatusV3

	keyAgreementPriv []byte // 32 bytes
	commitmentPriv   []byte // 32 bytes

	certBlob []byte
	certHash [sha256.Size]byte

	// firmwareVersion is MuunCardV3's, pushed here on its first SIO connect
	// so the metadata reports it even after an upgrade replaces that applet.
	firmwareVersion [VersionSizeV3]byte

	// shortDeviceID is what the ceremony stores and MuunCardV3 reports at
	// SELECT, kept here so an upgrade of that applet does not lose it.
	shortDeviceID [ShortDeviceIDSizeV3]byte

	challengeCounter uint16
}

var _ mockMetadataAppletV3 = (*mockMetadataAppletV3_0)(nil)

// newMockMetadataAppletV3_0 runs the 3.0 ceremony: a key agreement and a
// commitment key, a certificate over both, and a random short device id.
//
// The certificate carries a placeholder signature. Nothing in libwallet
// verifies it, and a mock cannot stand in for a vendor's attestation key.
func newMockMetadataAppletV3_0() (mockMetadataAppletV3, error) {
	keyAgreement, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		return nil, errors.Errorf("generate key agreement keypair: %w", err)
	}

	commitment, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		return nil, errors.Errorf("generate commitment keypair: %w", err)
	}

	certBlob, err := buildVendorCertificateV3_0(keyAgreement.PublicKey, commitment.PublicKey)
	if err != nil {
		return nil, errors.Errorf("build certificate: %w", err)
	}

	var shortDeviceID [ShortDeviceIDSizeV3]byte
	if _, err := rand.Read(shortDeviceID[:]); err != nil {
		return nil, errors.Errorf("generate short device id: %w", err)
	}

	return &mockMetadataAppletV3_0{
		deviceStatus:     DeviceStatusReadyV3,
		keyAgreementPriv: keyAgreement.PrivateKey,
		commitmentPriv:   commitment.PrivateKey,
		certBlob:         certBlob,
		certHash:         sha256.Sum256(certBlob),
		shortDeviceID:    shortDeviceID,
	}, nil
}

func (a *mockMetadataAppletV3_0) getDeviceStatus() DeviceStatusV3 {
	return a.deviceStatus
}

func (a *mockMetadataAppletV3_0) setDeviceStatus(status DeviceStatusV3) {
	a.deviceStatus = status
}

// getKeyAgreementKeyBytes returns the one key agreement key this firmware
// holds, whichever index names it.
func (a *mockMetadataAppletV3_0) getKeyAgreementKeyBytes(_ int) []byte {
	return a.keyAgreementPriv
}

// getShortDeviceID mirrors the SIO call MuunCardV3 makes to answer SELECT.
func (a *mockMetadataAppletV3_0) getShortDeviceID() [ShortDeviceIDSizeV3]byte {
	return a.shortDeviceID
}

// getCommitmentKeyBytes mirrors the SIO call the card makes to sign an
// approval: the key lives here, the way the firmware keeps it in the
// MetadataApplet so a MuunCardV3 upgrade cannot lose it.
func (a *mockMetadataAppletV3_0) getCommitmentKeyBytes() []byte {
	return a.commitmentPriv
}

// incrementChallengeCounter mirrors the SIO call the card makes after
// answering a challenge, so the metadata reports how many it has answered.
func (a *mockMetadataAppletV3_0) incrementChallengeCounter() {
	a.challengeCounter++
}

// getMetadataForAuth returns the form the pairing MAC covers:
// cert_hash(32) || fields.
func (a *mockMetadataAppletV3_0) getMetadataForAuth(_ int) []byte {
	buf := make([]byte, 0, metadataAuthSizeV3_0)
	buf = append(buf, a.certHash[:]...)
	return a.writeMetadataFields(buf)
}

// getMetadataForTransmission returns the form GET_METADATA answers and the
// pair response embeds: cert_len(2) || cert || fields.
func (a *mockMetadataAppletV3_0) getMetadataForTransmission(_ int) []byte {
	certLen := len(a.certBlob)
	buf := make([]byte, 0, certificateLengthSizeV3_0+certLen+metadataTrailerSizeV3_0)
	buf = binary.BigEndian.AppendUint16(buf, uint16(certLen))
	buf = append(buf, a.certBlob...)
	return a.writeMetadataFields(buf)
}

// getCertificate returns the one certificate this firmware holds, whichever
// index names it.
func (a *mockMetadataAppletV3_0) getCertificate(_ int) []byte {
	return a.certBlob
}

// getPairingIndex returns -1, what a card that never paired holds: this
// firmware's PAIR_INDEX is a constant, so every pairing gets index 0.
func (a *mockMetadataAppletV3_0) getPairingIndex() int {
	return -1
}

// incrementPairingIndex does nothing: this firmware does not count pairings.
func (a *mockMetadataAppletV3_0) incrementPairingIndex() {}

func (a *mockMetadataAppletV3_0) setFirmwareVersion(version [VersionSizeV3]byte) {
	a.firmwareVersion = version
}

// writeMetadataFields appends what the two forms share:
// challenge_counter(2) || metadata_version(4) || firmware_version(4) ||
// device_status(1).
func (a *mockMetadataAppletV3_0) writeMetadataFields(buf []byte) []byte {
	buf = binary.BigEndian.AppendUint16(buf, a.challengeCounter)
	buf = binary.BigEndian.AppendUint16(buf, mockMetadataVersionMajorV3_0)
	buf = binary.BigEndian.AppendUint16(buf, mockMetadataVersionMinorV3_0)
	buf = append(buf, a.firmwareVersion[:]...)
	return append(buf, byte(a.deviceStatus))
}
