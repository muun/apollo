package model

// SecurityCardV3MetadataJSON exposes the parsed V3 metadata. Field names
// mirror the firmware spec so the same vocabulary travels from firmware →
// libwallet → Houston.
//
// The card's identity keys are not here: they live inside the vendor
// certificate, which the card stores opaquely and never parses. Whoever
// needs them reads them out of the certificate after verifying its
// signature.
//
// Versions arrive on the wire as major(u16be) || minor(u16be) and are split
// here so a consumer can compare them without decoding a buffer.
type SecurityCardV3MetadataJSON struct {
	VendorCertificateInHex string `json:"vendorCertificateInHex"`
	ChallengeCounter       uint16 `json:"challengeCounter"`
	MetadataVersionMajor   uint16 `json:"metadataVersionMajor"`
	MetadataVersionMinor   uint16 `json:"metadataVersionMinor"`
	FirmwareVersionMajor   uint16 `json:"firmwareVersionMajor"`
	FirmwareVersionMinor   uint16 `json:"firmwareVersionMinor"`

	// DeviceStatus is where the card sits in its configuration lifecycle:
	// 0x01 UNINITIALIZED, 0x02 MISSING_KEYS, 0x03 MISSING_CERT,
	// 0x04 READY, 0x05 NUKED. Only a READY card will pair.
	DeviceStatus byte `json:"deviceStatus"`
}
