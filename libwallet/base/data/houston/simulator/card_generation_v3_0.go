package simulator

import "github.com/go-errors/errors"

// cardGenerationV3_0 is firmware 3.0.
// Its metadata carries the certificate first and the versions last, and
// both of the card's keys live inside the certificate. It keys the challenge
// response MAC with the card secret the challenge was authenticated under.
type cardGenerationV3_0 struct{}

var _ cardGenerationV3 = cardGenerationV3_0{}

func (cardGenerationV3_0) Name() string {
	return "3.0"
}

func (cardGenerationV3_0) ParseMetadata(metadataBytes []byte) (*metadataV3, error) {
	metadata, err := parseMetadataV3_0(metadataBytes)
	if err != nil {
		return nil, err
	}

	metadataForAuth, err := metadata.metadataForAuth()
	if err != nil {
		return nil, err
	}

	certificate, err := parseVendorCertificateV3_0(metadata.Certificate)
	if err != nil {
		return nil, errors.Errorf("parse vendor certificate: %w", err)
	}

	return &metadataV3{
		MetadataForAuth: metadataForAuth,
		KeyAgreementPub: certificate.KeyAgreementPub,
		CommitmentPub:   certificate.CommitmentPub,
		// 3.0 firmware reports no short device id at SELECT.
		ShortDeviceID: nil,
		JSON:          metadata.toJSON(),
	}, nil
}

// ChallengeResponseKey returns secret, the card secret the challenge was
// authenticated under.
func (cardGenerationV3_0) ChallengeResponseKey(secret, _ []byte) []byte {
	return secret
}
