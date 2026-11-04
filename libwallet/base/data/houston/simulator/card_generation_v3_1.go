package simulator

import "github.com/go-errors/errors"

// cardGenerationV3_1 is firmware 3.1, the first to lay the metadata out as
// the protocol spec does: the versions first, the key agreement key in the
// clear, and a certificate over the root of a tree of all the card's key
// agreement keys rather than over the one key. The commitment key still
// lives inside the certificate. It is also the first to key the challenge
// response MAC as the spec does, with the secret the ratchet derives.
type cardGenerationV3_1 struct{}

var _ cardGenerationV3 = cardGenerationV3_1{}

func (cardGenerationV3_1) Name() string {
	return "3.1"
}

func (cardGenerationV3_1) ParseMetadata(metadataBytes []byte) (*metadataV3, error) {
	metadata, err := parseMetadataV3_1(metadataBytes)
	if err != nil {
		return nil, err
	}

	metadataForAuth, err := metadata.metadataForAuth()
	if err != nil {
		return nil, err
	}

	keyAgreementPub, err := metadata.keyAgreementPubUncompressed()
	if err != nil {
		return nil, err
	}

	certificate, err := parseVendorCertificateV3_1(metadata.Certificate)
	if err != nil {
		return nil, errors.Errorf("parse vendor certificate: %w", err)
	}

	return &metadataV3{
		MetadataForAuth: metadataForAuth,
		KeyAgreementPub: keyAgreementPub,
		CommitmentPub:   certificate.CommitmentPub,
		ShortDeviceID:   certificate.shortDeviceID(),
		JSON:            metadata.toJSON(),
	}, nil
}

// ChallengeResponseKey returns nextSecret, the secret the card ratchets to,
// so a MAC that verifies proves the card holds it.
func (cardGenerationV3_1) ChallengeResponseKey(_, nextSecret []byte) []byte {
	return nextSecret
}
