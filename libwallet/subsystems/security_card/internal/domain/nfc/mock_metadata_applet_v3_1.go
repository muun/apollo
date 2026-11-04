package nfc

import (
	"crypto/sha256"
	"encoding/binary"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography"
)

// mockMetadataVersionMajorV3_1 and mockMetadataVersionMinorV3_1 mirror
// MetadataApplet.METADATA_VERSION_MAJOR and METADATA_VERSION_MINOR.
const (
	mockMetadataVersionMajorV3_1 = 1
	mockMetadataVersionMinorV3_1 = 0
)

// Wire sizes of the metadata as the spec lays it out. The transmission form
// is metadata_version(4) || firmware_version(4) || len(vendor_certificate)(2)
// || vendor_certificate || key_agreement_pub(33) || device_status(1), and the
// authentication form is metadata_version(4) || firmware_version(4) ||
// vendor_certificate_hash(32) || device_status(1).
const (
	certificateLengthSizeV3_1 = 2
	metadataFixedSizeV3_1     = 2*VersionSizeV3 + certificateLengthSizeV3_1 +
		compressedPointSize + 1
	metadataAuthSizeV3_1 = 2*VersionSizeV3 + sha256.Size + 1
)

// The short device id is device_model_id || key_agreement_root[-4:]:
// deviceModelIDSizeV3_1 is the hwmodel claim, and
// shortDeviceIDRootSuffixSizeV3_1 is how much of the root's tail follows it.
const (
	deviceModelIDSizeV3_1           = 8
	shortDeviceIDRootSuffixSizeV3_1 = 4
)

// mockDeviceModelIDV3_1 stands in for the device model id a vendor assigns.
var mockDeviceModelIDV3_1 = [deviceModelIDSizeV3_1]byte{'m', 'u', 'u', 'n', 'c', 'a', 'r', 'd'}

// mockMetadataAppletV3_1 is the metadata applet of firmware 3.1, as the
// protocol spec lays it out: 64 key agreement keys under one tree whose root
// the vendor certifies, one certificate per key carrying that key's
// inclusion proof, and the metadata with the versions first and the key
// agreement key in use in the clear.
//
// It covers what the pairing needs. libwallet never runs an initialization
// ceremony, so the constructor runs one of its own and leaves the applet
// where STORE_CERTIFICATE does: keys, certificates and root stored, READY.
type mockMetadataAppletV3_1 struct {
	deviceStatus DeviceStatusV3

	// One key agreement key per slot, the public half compressed: that is
	// what the metadata carries and what the tree hashes.
	keyAgreementPrivs [][]byte // 32 bytes each
	keyAgreementPubs  [][]byte // compressedPointSize bytes each
	commitmentPriv    []byte   // 32 bytes

	// certBlobs[i] attests key agreement key i. The certificates share
	// their signed part; only the inclusion proof differs.
	certBlobs        [][]byte
	keyAgreementRoot []byte

	// lastPairingIndex is the spec's last_pairing_index: -1 until the first
	// pairing, then the index of the last one the card handed out. It lives
	// here, next to the keys it protects, so a firmware upgrade does not
	// reset it. Which key a pairing uses, and when the card stops pairing,
	// are MuunCardV3's to derive from it.
	lastPairingIndex int

	// firmwareVersion is MuunCardV3's, pushed here on its first SIO connect
	// so the metadata reports it even after an upgrade replaces that applet.
	firmwareVersion [VersionSizeV3]byte
}

var _ mockMetadataAppletV3 = (*mockMetadataAppletV3_1)(nil)

// newMockMetadataAppletV3_1 runs the 3.1 ceremony: the key agreement keys,
// the commitment key, the tree over the former, and a certificate per key.
//
// The certificates carry a placeholder signature. Nothing in libwallet
// verifies it, and a mock cannot stand in for a vendor's attestation key.
func newMockMetadataAppletV3_1() (mockMetadataAppletV3, error) {
	keyAgreementPrivs := make([][]byte, 0, KeyAgreementKeyCountV3_1)
	keyAgreementPubs := make([][]byte, 0, KeyAgreementKeyCountV3_1)
	for range KeyAgreementKeyCountV3_1 {
		keyAgreement, err := cryptography.GenerateSecp256r1PKeyPair()
		if err != nil {
			return nil, errors.Errorf("generate key agreement keypair: %w", err)
		}
		keyAgreementPrivs = append(keyAgreementPrivs, keyAgreement.PrivateKey)
		keyAgreementPubs = append(keyAgreementPubs, compressSecp256r1Point(keyAgreement.PublicKey))
	}

	commitment, err := cryptography.GenerateSecp256r1PKeyPair()
	if err != nil {
		return nil, errors.Errorf("generate commitment keypair: %w", err)
	}

	tree, err := NewKeyAgreementTreeV3_1(keyAgreementPubs)
	if err != nil {
		return nil, err
	}

	certBlobs := make([][]byte, 0, KeyAgreementKeyCountV3_1)
	for keyIndex := range KeyAgreementKeyCountV3_1 {
		certBlob, err := buildVendorCertificateV3_1(
			commitment.PublicKey, mockDeviceModelIDV3_1[:], tree, keyIndex,
		)
		if err != nil {
			return nil, errors.Errorf("build certificate %d: %w", keyIndex, err)
		}
		certBlobs = append(certBlobs, certBlob)
	}

	return &mockMetadataAppletV3_1{
		deviceStatus:      DeviceStatusReadyV3,
		keyAgreementPrivs: keyAgreementPrivs,
		keyAgreementPubs:  keyAgreementPubs,
		commitmentPriv:    commitment.PrivateKey,
		certBlobs:         certBlobs,
		keyAgreementRoot:  tree.Root(),
		lastPairingIndex:  -1,
	}, nil
}

func (a *mockMetadataAppletV3_1) getDeviceStatus() DeviceStatusV3 {
	return a.deviceStatus
}

func (a *mockMetadataAppletV3_1) setDeviceStatus(status DeviceStatusV3) {
	a.deviceStatus = status
}

func (a *mockMetadataAppletV3_1) getKeyAgreementKeyBytes(keyIndex int) []byte {
	return a.keyAgreementPrivs[keyIndex]
}

func (a *mockMetadataAppletV3_1) getCommitmentKeyBytes() []byte {
	return a.commitmentPriv
}

// getShortDeviceID derives device_model_id || key_agreement_root[-4:], the
// id SELECT reports before anything is authenticated and the certificate
// lets a client confirm afterwards.
func (a *mockMetadataAppletV3_1) getShortDeviceID() [ShortDeviceIDSizeV3]byte {
	var shortDeviceID [ShortDeviceIDSizeV3]byte
	copy(shortDeviceID[:], mockDeviceModelIDV3_1[:])
	copy(
		shortDeviceID[deviceModelIDSizeV3_1:],
		a.keyAgreementRoot[len(a.keyAgreementRoot)-shortDeviceIDRootSuffixSizeV3_1:],
	)
	return shortDeviceID
}

// getMetadataForAuth returns the form the pairing MAC covers:
// metadata_version(4) || firmware_version(4) || vendor_certificate_hash(32)
// || device_status(1). The key agreement key is not here: DH1 is what binds
// it.
func (a *mockMetadataAppletV3_1) getMetadataForAuth(keyIndex int) []byte {
	certHash := sha256.Sum256(a.certBlobs[keyIndex])

	buf := make([]byte, 0, metadataAuthSizeV3_1)
	buf = a.writeVersions(buf)
	buf = append(buf, certHash[:]...)
	return append(buf, byte(a.deviceStatus))
}

// getMetadataForTransmission returns the form GET_METADATA answers and the
// pair response embeds: metadata_version(4) || firmware_version(4) ||
// len(vendor_certificate)(2) || vendor_certificate || key_agreement_pub(33)
// || device_status(1).
func (a *mockMetadataAppletV3_1) getMetadataForTransmission(keyIndex int) []byte {
	certBlob := a.certBlobs[keyIndex]

	buf := make([]byte, 0, metadataFixedSizeV3_1+len(certBlob))
	buf = a.writeVersions(buf)
	buf = binary.BigEndian.AppendUint16(buf, uint16(len(certBlob)))
	buf = append(buf, certBlob...)
	buf = append(buf, a.keyAgreementPubs[keyIndex]...)
	return append(buf, byte(a.deviceStatus))
}

func (a *mockMetadataAppletV3_1) getCertificate(keyIndex int) []byte {
	return a.certBlobs[keyIndex]
}

// incrementChallengeCounter does nothing: this layout carries no challenge
// counter, so the applet has nothing to report.
func (a *mockMetadataAppletV3_1) incrementChallengeCounter() {}

func (a *mockMetadataAppletV3_1) getPairingIndex() int {
	return a.lastPairingIndex
}

func (a *mockMetadataAppletV3_1) incrementPairingIndex() {
	a.lastPairingIndex++
}

func (a *mockMetadataAppletV3_1) setFirmwareVersion(version [VersionSizeV3]byte) {
	a.firmwareVersion = version
}

// writeVersions appends what the two forms open with:
// metadata_version(4) || firmware_version(4).
func (a *mockMetadataAppletV3_1) writeVersions(buf []byte) []byte {
	buf = binary.BigEndian.AppendUint16(buf, mockMetadataVersionMajorV3_1)
	buf = binary.BigEndian.AppendUint16(buf, mockMetadataVersionMinorV3_1)
	return append(buf, a.firmwareVersion[:]...)
}

// compressSecp256r1Point turns an uncompressed SEC1 point, 0x04 || x || y,
// into its compressed form, the parity of y as the prefix and then x.
func compressSecp256r1Point(point []byte) []byte {
	compressed := make([]byte, 0, compressedPointSize)
	if point[len(point)-1]&1 == 1 {
		compressed = append(compressed, sec1OddYPrefix)
	} else {
		compressed = append(compressed, sec1EvenYPrefix)
	}
	return append(compressed, point[1:1+p256CoordinateSize]...)
}
