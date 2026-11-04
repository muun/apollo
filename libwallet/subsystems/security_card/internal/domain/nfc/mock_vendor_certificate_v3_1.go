package nfc

import (
	"github.com/fxamacker/cbor/v2"
	"github.com/go-errors/errors"
)

// Encoder for the vendor attestation certificate a mock 3.1 card stands up,
// as the protocol spec lays it out: a COSE_Sign1 envelope over a CWT/EAT
// payload. The vendor signs once, over the root of the tree of the card's
// key agreement keys, and the card holds one certificate per key: the signed
// part is the same in all of them, and only the inclusion proof in the
// unprotected header differs. Nothing here signs it: the signature is a
// placeholder, since a mock cannot stand in for a vendor's attestation key,
// and nothing in libwallet verifies it.
//
// Houston's mock parses these on its own
// (base/data/houston/simulator/vendor_certificate_v3_1.go). The two deliberately
// share no code, so a disagreement on the wire format fails in a test rather
// than on a card.

// vendorCertificateMaxSizeV3_1 is what a card reserves for each certificate
// slot: the 384 bytes a slot holds for a 32-key tree, plus the 34 bytes (a
// 32-byte hash and its 2-byte CBOR header) of the level of inclusion proof the
// spec's 64 keys add. A certificate the encoder cannot fit in it is one no
// card could hold, so it is refused here rather than on the card.
const vendorCertificateMaxSizeV3_1 = 418

// muunOEMID is Muun's IANA private enterprise number, what the oemid
// claim of every certificate carries and the only value a verifier accepts.
const muunOEMID = 67_132

// mockIssuedAtV3_1 is the issue time a mock card's certificates carry, where
// a real one carries when the vendor signed it: any fixed one.
const mockIssuedAtV3_1 = 1_700_000_000

// COSE and CWT registry values the spec's certificate uses.
const (
	// coseHeaderAlgorithm is the protected header label of the signing
	// algorithm, from RFC 9052.
	coseHeaderAlgorithm = 1

	// The labels of a verifiable data structure and its proofs, from RFC
	// 9942, with the values for an RFC 9162 tree and an inclusion proof.
	coseHeaderVerifiableDataStructure = 395
	coseHeaderVerifiableDataProofs    = 396
	coseVerifiableDataRFC9162SHA256   = 1
	coseVerifiableDataInclusionProofs = -1

	// CWT claim keys, from RFC 8392 and the EAT registry.
	cwtClaimIssuedAt      = 6
	cwtClaimConfirmation  = 8
	cwtClaimUEID          = 256
	cwtClaimOEMID         = 258
	cwtClaimHardwareModel = 259

	// cnfCOSEKey is the key under which a cnf claim carries a COSE_Key, from
	// RFC 8747.
	cnfCOSEKey = 1

	// ueidTypeRandom is the EAT ueid type byte that prefixes the key
	// agreement root.
	ueidTypeRandom = 0x01
)

// buildVendorCertificateV3_1 encodes the certificate of key agreement key
// keyIndex of the tree: the CWT carries the commitment key, the tree's root
// as the ueid and the device model, and the unprotected header carries the
// key's inclusion proof as [tree_size, leaf_index, path].
//
// It encodes deterministically, per RFC 8949 section 4.2.1, as the spec
// requires and the vendor CLI does: the certificate is stored verbatim and
// verifiers bind a hash of these exact bytes.
func buildVendorCertificateV3_1(
	commitmentPub []byte,
	deviceModelID []byte,
	tree *KeyAgreementTreeV3_1,
	keyIndex int,
) ([]byte, error) {
	encoder, err := cbor.CoreDetEncOptions().EncMode()
	if err != nil {
		return nil, errors.Errorf("build deterministic encoder: %w", err)
	}

	commitmentKey, err := encoder.Marshal(buildCOSEKeyV3(commitmentPub, nil))
	if err != nil {
		return nil, errors.Errorf("encode commitment COSE_Key: %w", err)
	}

	path, err := tree.InclusionPath(keyIndex)
	if err != nil {
		return nil, err
	}
	inclusionProof, err := encoder.Marshal([]any{tree.Size(), keyIndex, path})
	if err != nil {
		return nil, errors.Errorf("encode inclusion proof: %w", err)
	}

	payload, err := encoder.Marshal(map[int]any{
		cwtClaimIssuedAt:      uint32(mockIssuedAtV3_1),
		cwtClaimConfirmation:  map[int]any{cnfCOSEKey: cbor.RawMessage(commitmentKey)},
		cwtClaimUEID:          append([]byte{ueidTypeRandom}, tree.Root()...),
		cwtClaimOEMID:         uint64(muunOEMID),
		cwtClaimHardwareModel: deviceModelID,
	})
	if err != nil {
		return nil, errors.Errorf("encode CWT: %w", err)
	}

	protectedHeader, err := encoder.Marshal(map[int]int{
		coseHeaderAlgorithm:               coseAlgorithmES256,
		coseHeaderVerifiableDataStructure: coseVerifiableDataRFC9162SHA256,
	})
	if err != nil {
		return nil, errors.Errorf("encode protected header: %w", err)
	}

	unprotectedHeader, err := encoder.Marshal(map[int]any{
		coseHeaderVerifiableDataProofs: map[int]any{
			coseVerifiableDataInclusionProofs: [][]byte{inclusionProof},
		},
	})
	if err != nil {
		return nil, errors.Errorf("encode unprotected header: %w", err)
	}

	certificate, err := encoder.Marshal([]any{
		protectedHeader,
		cbor.RawMessage(unprotectedHeader),
		payload,
		make([]byte, coseES256SignatureLen),
	})
	if err != nil {
		return nil, errors.Errorf("encode COSE_Sign1: %w", err)
	}
	if len(certificate) > vendorCertificateMaxSizeV3_1 {
		return nil, errors.Errorf(
			"certificate is %d bytes, over the %d a card can store",
			len(certificate), vendorCertificateMaxSizeV3_1,
		)
	}

	return certificate, nil
}
