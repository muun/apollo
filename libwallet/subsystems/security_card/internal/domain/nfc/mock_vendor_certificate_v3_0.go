package nfc

import (
	"github.com/fxamacker/cbor/v2"
	"github.com/go-errors/errors"
)

// Encoder for the vendor attestation certificate a mock 3.0 card stands up:
// a COSE_Sign1 envelope over a CWT payload, in the shape the vendor CLI
// issues in its ceremony (cards/vendor-cli/muuncardv3/cert.go). Nothing here signs
// it: the signature is a placeholder, since a mock cannot stand in for a
// vendor's attestation key, and nothing in libwallet verifies it.
//
// Houston's mock parses these on its own. The two deliberately share no
// code, so a disagreement on the wire format fails in a test rather than on
// a card.

const (
	deviceModelUUIDSizeV3_0 = 16
	serialNumberSizeV3_0    = 16
)

// buildVendorCertificateV3_0 encodes a COSE_Sign1 over a CWT carrying both
// public keys.
//
// It uses the CTAP2 canonical encoder for the same reason the CLI does: the
// certificate is stored verbatim and verifiers bind a hash of these exact
// bytes.
func buildVendorCertificateV3_0(keyAgreementPub, commitmentPub []byte) ([]byte, error) {
	encoder, err := cbor.CTAP2EncOptions().EncMode()
	if err != nil {
		return nil, errors.Errorf("build ctap2 encoder: %w", err)
	}

	keyAgreementKey, err := encoder.Marshal(buildCOSEKeyV3(keyAgreementPub, nil))
	if err != nil {
		return nil, errors.Errorf("encode key agreement COSE_Key: %w", err)
	}

	commitmentKey, err := encoder.Marshal(buildCOSEKeyV3(commitmentPub, []int{2}))
	if err != nil {
		return nil, errors.Errorf("encode commitment COSE_Key: %w", err)
	}

	payload, err := encoder.Marshal(map[int]any{
		1:  make([]byte, deviceModelUUIDSizeV3_0),
		2:  make([]byte, serialNumberSizeV3_0),
		6:  uint32(1_700_000_000),
		8:  cbor.RawMessage(keyAgreementKey),
		-1: cbor.RawMessage(commitmentKey),
	})
	if err != nil {
		return nil, errors.Errorf("encode CWT: %w", err)
	}

	protectedHeader, err := encoder.Marshal(map[int]int{1: coseAlgorithmES256})
	if err != nil {
		return nil, errors.Errorf("encode protected header: %w", err)
	}

	emptyHeaders, err := encoder.Marshal(map[int]any{})
	if err != nil {
		return nil, errors.Errorf("encode unprotected headers: %w", err)
	}

	return encoder.Marshal([]any{
		protectedHeader,
		cbor.RawMessage(emptyHeaders),
		payload,
		make([]byte, coseES256SignatureLen),
	})
}
