package simulator

import (
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston/model"
)

// Wire sizes of the metadata as the protocol spec lays it out, which is
// what firmware 3.1 implements.
const (
	// vendorCertificateMaxSizeV3_1 is what a card reserves for each
	// certificate slot, and so the largest one it can hand back: the 384
	// bytes a slot holds for a 32-key tree, plus the 34 bytes (a 32-byte hash
	// and its 2-byte CBOR header) of the level of inclusion proof the spec's
	// 64 keys add.
	vendorCertificateMaxSizeV3_1 = 418

	// versionSizeV3_1 covers one version field, major(u16be) || minor(u16be).
	versionSizeV3_1           = 4
	certificateLengthSizeV3_1 = 2
	// compressedPointSizeV3_1 is a SEC1 compressed point: parity prefix || x.
	compressedPointSizeV3_1 = 1 + p256CoordinateSize
	deviceStatusSizeV3_1    = 1

	// metadataMinSizeV3_1 is the length before a certificate is stored, when
	// the card writes len(vendor_certificate) = 0.
	metadataMinSizeV3_1 = 2*versionSizeV3_1 + certificateLengthSizeV3_1 +
		compressedPointSizeV3_1 + deviceStatusSizeV3_1

	// metadataAuthSizeV3_1 is the length of the authentication form, which
	// carries the certificate's hash in place of its length and bytes, and
	// no key agreement key: DH1 is what binds that one.
	metadataAuthSizeV3_1 = 2*versionSizeV3_1 + sha256.Size + deviceStatusSizeV3_1
)

// metadataV3_1 is the metadata as the protocol spec lays it out: the
// versions first, then the certificate of the key agreement key in use, the
// key itself, and the lifecycle status.
//
// Wire layout:
//
//	metadata_version(4) || firmware_version(4) || len(vendor_certificate)(2) ||
//	vendor_certificate || key_agreement_pub(33) || device_status(1)
//
// Total size is 44 + len(vendor_certificate). The key agreement key travels
// in the clear, point-compressed, because the certificate no longer carries
// it: it carries the root of a tree over all of the card's key agreement
// keys, and the proof that this key is a leaf of it.
type metadataV3_1 struct {
	MetadataVersion [versionSizeV3_1]byte // major(u16be) || minor(u16be) of the metadata applet
	FirmwareVersion [versionSizeV3_1]byte // major(u16be) || minor(u16be) of the firmware applet

	// Certificate is the vendor's COSE_Sign1/CWT attestation blob for the
	// key agreement key in use, nil until the ceremony stores one.
	Certificate []byte

	// KeyAgreementPub is the card's static key for this pairing, as emitted:
	// a SEC1 compressed point.
	KeyAgreementPub []byte

	// DeviceStatus is where the card sits in its configuration lifecycle,
	// as the device_status byte the card reports. Only a READY card pairs.
	DeviceStatus byte
}

// newMetadataV3_1 builds the metadata from its fields. They come off the
// wire, so a violation returns an error rather than panicking.
func newMetadataV3_1(
	metadataVersion [versionSizeV3_1]byte,
	firmwareVersion [versionSizeV3_1]byte,
	certificate []byte,
	keyAgreementPub []byte,
	deviceStatus byte,
) (*metadataV3_1, error) {
	if len(certificate) > vendorCertificateMaxSizeV3_1 {
		return nil, errors.Errorf(
			"certificate length %d exceeds the %d a card can store",
			len(certificate), vendorCertificateMaxSizeV3_1,
		)
	}
	if len(keyAgreementPub) != compressedPointSizeV3_1 {
		return nil, errors.Errorf(
			"key agreement key is %d bytes, expected %d",
			len(keyAgreementPub), compressedPointSizeV3_1,
		)
	}
	return &metadataV3_1{
		MetadataVersion: metadataVersion,
		FirmwareVersion: firmwareVersion,
		Certificate:     certificate,
		KeyAgreementPub: keyAgreementPub,
		DeviceStatus:    deviceStatus,
	}, nil
}

// parseMetadataV3_1 decodes the metadata wire format. Layout:
//
//	metadata_version(4) || firmware_version(4) || len(vendor_certificate)(2) ||
//	vendor_certificate || key_agreement_pub(33) || device_status(1)
//
// The data must be exactly the metadata the card emitted: the MAC covers the
// values rather than the buffer, so bytes past device_status are refused
// rather than carried along.
func parseMetadataV3_1(data []byte) (*metadataV3_1, error) {
	if len(data) < metadataMinSizeV3_1 {
		return nil, errors.Errorf(
			"invalid metadata length: %d (expected at least %d)",
			len(data), metadataMinSizeV3_1,
		)
	}

	cursor := 0

	var metadataVersion, firmwareVersion [versionSizeV3_1]byte
	copy(metadataVersion[:], data[cursor:cursor+versionSizeV3_1])
	cursor += versionSizeV3_1
	copy(firmwareVersion[:], data[cursor:cursor+versionSizeV3_1])
	cursor += versionSizeV3_1

	certificateLength := int(binary.BigEndian.Uint16(data[cursor:]))
	cursor += certificateLengthSizeV3_1

	totalLength := metadataMinSizeV3_1 + certificateLength
	if len(data) != totalLength {
		return nil, errors.Errorf(
			"invalid metadata length: %d (expected %d for a %d-byte certificate)",
			len(data), totalLength, certificateLength,
		)
	}

	var certificate []byte
	if certificateLength > 0 {
		certificate = make([]byte, certificateLength)
		copy(certificate, data[cursor:cursor+certificateLength])
	}
	cursor += certificateLength

	keyAgreementPub := make([]byte, compressedPointSizeV3_1)
	copy(keyAgreementPub, data[cursor:cursor+compressedPointSizeV3_1])
	cursor += compressedPointSizeV3_1

	return newMetadataV3_1(
		metadataVersion,
		firmwareVersion,
		certificate,
		keyAgreementPub,
		data[cursor],
	)
}

// metadataForAuth returns the authentication form of the metadata, which is
// what the pairing MAC covers:
//
//	metadata_version(4) || firmware_version(4) || vendor_certificate_hash(32) ||
//	device_status(1)
//
// The card hashes the certificate once, when the ceremony stores it, so
// every later pairing authenticates these fixed 41 bytes instead of a whole
// certificate. The key agreement key is not here: a wrong one yields a DH1
// the card cannot match, so the MAC binds it on its own.
//
// A card with no certificate has no authentication form, so hashing an
// absent one here would invent bytes no card would produce.
func (m *metadataV3_1) metadataForAuth() ([]byte, error) {
	if len(m.Certificate) == 0 {
		return nil, errors.New("metadata carries no certificate to authenticate")
	}

	certificateHash := sha256.Sum256(m.Certificate)

	block := make([]byte, 0, metadataAuthSizeV3_1)
	block = append(block, m.MetadataVersion[:]...)
	block = append(block, m.FirmwareVersion[:]...)
	block = append(block, certificateHash[:]...)
	block = append(block, m.DeviceStatus)
	return block, nil
}

// keyAgreementPubUncompressed returns the key agreement key in the 65-byte
// uncompressed SEC1 encoding the rest of the protocol speaks, checking on
// the way that it is a point on P-256.
func (m *metadataV3_1) keyAgreementPubUncompressed() ([]byte, error) {
	uncompressed, err := decompressP256(m.KeyAgreementPub)
	if err != nil {
		return nil, errors.Errorf("key agreement key: %w", err)
	}
	return uncompressed, nil
}

// toJSON lays the metadata out as Houston reports it back after a pairing.
// This layout carries no challenge counter, so it reports zero.
func (m *metadataV3_1) toJSON() model.SecurityCardV3MetadataJSON {
	return model.SecurityCardV3MetadataJSON{
		VendorCertificateInHex: hex.EncodeToString(m.Certificate),
		ChallengeCounter:       0,
		MetadataVersionMajor:   binary.BigEndian.Uint16(m.MetadataVersion[:2]),
		MetadataVersionMinor:   binary.BigEndian.Uint16(m.MetadataVersion[2:]),
		FirmwareVersionMajor:   binary.BigEndian.Uint16(m.FirmwareVersion[:2]),
		FirmwareVersionMinor:   binary.BigEndian.Uint16(m.FirmwareVersion[2:]),
		DeviceStatus:           m.DeviceStatus,
	}
}
