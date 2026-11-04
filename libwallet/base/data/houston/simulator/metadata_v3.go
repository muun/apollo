package simulator

import "github.com/muun/libwallet/base/data/houston/model"

// metadataV3 is what Houston takes out of a card's pairing metadata,
// whichever generation laid it out.
type metadataV3 struct {
	// MetadataForAuth is the form of the metadata the pairing MAC covers.
	MetadataForAuth []byte

	// KeyAgreementPub is the card's static key, the DH1 input of the pairing.
	// 65-byte uncompressed SEC1.
	KeyAgreementPub []byte

	// CommitmentPub verifies the card's action approvals. 65-byte
	// uncompressed SEC1.
	CommitmentPub []byte

	// ShortDeviceID is the id the card reports at SELECT, as its certificate
	// attests it: what a client matches the card on the reader against. Nil
	// for a generation whose firmware reports none.
	ShortDeviceID []byte

	// JSON is the metadata as Houston reports it back once the card paired.
	JSON model.SecurityCardV3MetadataJSON
}
