package simulator

import (
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston/model"
)

// Wire sizes of the metadata as firmware 3.0 lays it out.
const (
	// vendorCertificateMaxSizeV3_0 is the largest blob a 3.0 card stores,
	// and so the largest one it can hand back.
	vendorCertificateMaxSizeV3_0 = 243

	certificateLengthSizeV3_0 = 2
	challengeCounterSizeV3_0  = 2
	// versionSizeV3_0 covers one version field, major(u16be) || minor(u16be).
	versionSizeV3_0      = 4
	deviceStatusSizeV3_0 = 1

	// metadataTrailerSizeV3_0 is everything after the certificate:
	// challenge_counter(2) || metadata_version(4) || firmware_version(4) ||
	// device_status(1).
	metadataTrailerSizeV3_0 = challengeCounterSizeV3_0 + 2*versionSizeV3_0 + deviceStatusSizeV3_0

	// metadataMinSizeV3_0 is the length before a certificate is stored, when
	// the card writes cert_len = 0.
	metadataMinSizeV3_0 = certificateLengthSizeV3_0 + metadataTrailerSizeV3_0

	// metadataAuthSizeV3_0 is the length of the authentication form, which
	// carries the certificate's hash instead of the certificate.
	metadataAuthSizeV3_0 = sha256.Size + metadataTrailerSizeV3_0
)

// metadataV3_0 is the metadata as firmware 3.0 lays it out: the card's
// certificate, lifecycle status and versions. The card returns it from
// GET_METADATA and embeds it in the pair response.
//
// Wire layout:
//
//	cert_len(2) || certificate(cert_len) || challenge_counter(2) ||
//	metadata_version(4) || firmware_version(4) || device_status(1)
//
// Total size is 13 + cert_len: 13 bytes before a certificate is stored, and
// at most 256 since a card stores at most 243 bytes of certificate. The
// certificate the ceremony issues today is 206 bytes, so 219 in practice.
//
// The card's identity keys are not here. They live inside the certificate,
// which the card stores opaquely and never parses.
type metadataV3_0 struct {
	// Certificate is the vendor's COSE_Sign1/CWT attestation blob, nil
	// until the initialization ceremony stores one.
	Certificate []byte

	// ChallengeCounter counts the challenges the card answered
	// successfully. A nonzero value on a card the user believes to be new
	// means it has already been in service.
	ChallengeCounter uint16

	MetadataVersion [versionSizeV3_0]byte // major(u16be) || minor(u16be) of the metadata applet
	FirmwareVersion [versionSizeV3_0]byte // major(u16be) || minor(u16be) of the firmware applet

	// DeviceStatus is where the card sits in its configuration lifecycle,
	// as the device_status byte the card reports. Only a READY card pairs.
	DeviceStatus byte
}

// newMetadataV3_0 builds the metadata from its fields. They come off the
// wire, so a violation returns an error rather than panicking: a certificate
// longer than a card can store cannot have come from one.
func newMetadataV3_0(
	certificate []byte,
	challengeCounter uint16,
	metadataVersion [versionSizeV3_0]byte,
	firmwareVersion [versionSizeV3_0]byte,
	deviceStatus byte,
) (*metadataV3_0, error) {
	if len(certificate) > vendorCertificateMaxSizeV3_0 {
		return nil, errors.Errorf(
			"certificate length %d exceeds the %d a card can store",
			len(certificate), vendorCertificateMaxSizeV3_0,
		)
	}

	return &metadataV3_0{
		Certificate:      certificate,
		ChallengeCounter: challengeCounter,
		MetadataVersion:  metadataVersion,
		FirmwareVersion:  firmwareVersion,
		DeviceStatus:     deviceStatus,
	}, nil
}

// metadataForAuth returns the authentication form of the metadata, which is
// what the pairing MAC covers:
//
//	HASH(certificate)(32) || challenge_counter(2) || metadata_version(4) ||
//	firmware_version(4) || device_status(1)
//
// It differs from the transmission form only in its first field. The card
// hashes the certificate once, when the ceremony stores it, so every later
// pairing authenticates these fixed 43 bytes instead of a whole certificate.
//
// A card with no certificate has no authentication form: MetadataApplet's
// getMetadataForAuth throws SW_NOT_READY while none is stored, so hashing an
// absent one here would invent bytes no card would produce.
func (m *metadataV3_0) metadataForAuth() ([]byte, error) {
	if len(m.Certificate) == 0 {
		return nil, errors.New("metadata carries no certificate to authenticate")
	}

	certificateHash := sha256.Sum256(m.Certificate)

	block := make([]byte, 0, metadataAuthSizeV3_0)
	block = append(block, certificateHash[:]...)
	block = binary.BigEndian.AppendUint16(block, m.ChallengeCounter)
	block = append(block, m.MetadataVersion[:]...)
	block = append(block, m.FirmwareVersion[:]...)
	block = append(block, m.DeviceStatus)
	return block, nil
}

// toJSON lays the metadata out as Houston reports it back after a pairing.
func (m *metadataV3_0) toJSON() model.SecurityCardV3MetadataJSON {
	return model.SecurityCardV3MetadataJSON{
		VendorCertificateInHex: hex.EncodeToString(m.Certificate),
		ChallengeCounter:       m.ChallengeCounter,
		MetadataVersionMajor:   binary.BigEndian.Uint16(m.MetadataVersion[:2]),
		MetadataVersionMinor:   binary.BigEndian.Uint16(m.MetadataVersion[2:]),
		FirmwareVersionMajor:   binary.BigEndian.Uint16(m.FirmwareVersion[:2]),
		FirmwareVersionMinor:   binary.BigEndian.Uint16(m.FirmwareVersion[2:]),
		DeviceStatus:           m.DeviceStatus,
	}
}

// parseMetadataV3_0 decodes the metadata wire format, as returned by
// GET_METADATA and embedded in the pair response. Layout:
//
//	cert_len (2) || certificate (cert_len) || challenge_counter (2) ||
//	metadata_version (4) || firmware_version (4) || device_status (1)
//
// A card carries no certificate until the initialization ceremony stores
// one, in which case it writes cert_len = 0.
//
// The data must be exactly the metadata the card emitted: the MAC covers the
// values rather than the buffer, so bytes past device_status are refused
// rather than carried along.
func parseMetadataV3_0(data []byte) (*metadataV3_0, error) {
	if len(data) < metadataMinSizeV3_0 {
		return nil, errors.Errorf(
			"invalid metadata length: %d (expected at least %d)",
			len(data), metadataMinSizeV3_0,
		)
	}

	certificateLength := int(binary.BigEndian.Uint16(data))

	totalLength := metadataMinSizeV3_0 + certificateLength
	if len(data) != totalLength {
		return nil, errors.Errorf(
			"invalid metadata length: %d (expected %d for a %d-byte certificate)",
			len(data), totalLength, certificateLength,
		)
	}

	cursor := certificateLengthSizeV3_0

	var certificate []byte
	if certificateLength > 0 {
		certificate = make([]byte, certificateLength)
		copy(certificate, data[cursor:cursor+certificateLength])
	}
	cursor += certificateLength

	challengeCounter := binary.BigEndian.Uint16(data[cursor:])
	cursor += challengeCounterSizeV3_0

	var metadataVersion, firmwareVersion [versionSizeV3_0]byte
	copy(metadataVersion[:], data[cursor:cursor+versionSizeV3_0])
	cursor += versionSizeV3_0
	copy(firmwareVersion[:], data[cursor:cursor+versionSizeV3_0])
	cursor += versionSizeV3_0

	return newMetadataV3_0(
		certificate,
		challengeCounter,
		metadataVersion,
		firmwareVersion,
		data[cursor],
	)
}
