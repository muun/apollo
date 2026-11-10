package simulator

import (
	"crypto/sha256"

	"github.com/fxamacker/cbor/v2"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography"
)

// Reader for the vendor attestation certificate as the protocol spec lays it
// out, which is what firmware 3.1 implements: a COSE_Sign1 envelope over a
// CWT/EAT payload, signed by the vendor with its attestation key. The vendor
// signs once, over the root of a tree whose leaves are the card's key
// agreement keys, and the card holds one certificate per key: the signed
// part is the same in all of them, and only the inclusion proof in the
// unprotected header differs.
//
// SECURITY: parseVendorCertificateV3_1 verifies neither the vendor's
// signature nor the inclusion proof, so every claim it returns is
// unauthenticated. The signature needs the attestation key registered for
// the device model, which nothing tracks yet. Reading the commitment key
// out unverified is still sound for the pairing itself: the pairing MAC
// covers the certificate's hash, so a certificate the card did not send
// fails the MAC. What the missing checks give up is the vendor's word that
// the card is genuine.

const (
	deviceModelIDSizeV3_1 = 8
	// shortDeviceIDRootSuffixSizeV3_1 is how much of the key agreement root
	// the short device id carries, taken from the end.
	shortDeviceIDRootSuffixSizeV3_1 = 4
	// ueidSizeV3_1 is the universal entity id: a type byte and the key
	// agreement root.
	ueidTypeV3_1 = 0x01
	ueidSizeV3_1 = 1 + sha256.Size

	// COSE header labels and values for a verifiable data structure, from
	// RFC 9942.
	coseHeaderVDS          = 395
	coseHeaderVDP          = 396
	coseVDSRFC9162SHA256   = 1
	coseVDPInclusionProofs = -1

	// cnfCOSEKey is the key under which a cnf claim carries a COSE_Key, from
	// RFC 8747.
	cnfCOSEKey = 1

	// muunOEMID is Muun's IANA private enterprise number, the only value the
	// oemid claim may carry: a certificate issued for anyone else is refused.
	muunOEMID = 67_132
)

// vendorCertificateV3_1 holds the claims of a parsed vendor attestation
// certificate, except the oemid, which the parser accepts only as Muun's
// enterprise number.
type vendorCertificateV3_1 struct {
	DeviceModelID    []byte // 8 bytes, the hwmodel claim
	IssuedAt         uint32 // unix epoch seconds, the iat claim
	KeyAgreementRoot []byte // 32 bytes, the ueid claim without its type byte
	CommitmentPub    []byte // 65 bytes, the cnf claim, verifies the card's action approvals
}

// newVendorCertificateV3_1 builds the certificate's claims. They come out of
// a blob the card handed back, so a wrong size or an off-curve key returns
// an error rather than panicking.
func newVendorCertificateV3_1(
	deviceModelID []byte,
	issuedAt uint32,
	keyAgreementRoot []byte,
	commitmentPub []byte,
) (*vendorCertificateV3_1, error) {
	if len(deviceModelID) != deviceModelIDSizeV3_1 {
		return nil, errors.Errorf(
			"device model id must be %d bytes, got %d", deviceModelIDSizeV3_1, len(deviceModelID),
		)
	}
	if len(keyAgreementRoot) != sha256.Size {
		return nil, errors.Errorf(
			"key agreement root must be %d bytes, got %d", sha256.Size, len(keyAgreementRoot),
		)
	}
	if err := cryptography.ValidateSecp256r1PublicKey(commitmentPub); err != nil {
		return nil, errors.Errorf("commitment key: %w", err)
	}

	return &vendorCertificateV3_1{
		DeviceModelID:    deviceModelID,
		IssuedAt:         issuedAt,
		KeyAgreementRoot: keyAgreementRoot,
		CommitmentPub:    commitmentPub,
	}, nil
}

type coseProtectedHeaderV3_1 struct {
	Algorithm               int `cbor:"1,keyasint"`
	VerifiableDataStructure int `cbor:"395,keyasint"`
}

// coseUnprotectedHeaderV3_1 carries the inclusion proofs. It is declared so
// a strict decode accepts the header, and not read: nothing here verifies a
// proof.
type coseUnprotectedHeaderV3_1 struct {
	Proofs map[int][]cbor.RawMessage `cbor:"396,keyasint"`
}

// cwtClaimsV3_1 is the decode-side view of the CWT payload. Pointer, map and
// slice fields let the parser tell an absent claim from one carrying a zero
// value.
type cwtClaimsV3_1 struct {
	IssuedAt      *uint32                 `cbor:"6,keyasint"`
	Confirm       map[int]cbor.RawMessage `cbor:"8,keyasint"`
	UEID          []byte                  `cbor:"256,keyasint"`
	OEMID         *uint64                 `cbor:"258,keyasint"`
	HardwareModel []byte                  `cbor:"259,keyasint"`
}

// shortDeviceID derives device_model_id || key_agreement_root[-4:], the id
// the card reports at SELECT before anything is authenticated, out of the
// certificate that does authenticate it.
func (c *vendorCertificateV3_1) shortDeviceID() []byte {
	rootSuffix := c.KeyAgreementRoot[len(c.KeyAgreementRoot)-shortDeviceIDRootSuffixSizeV3_1:]

	shortDeviceID := make([]byte, 0, deviceModelIDSizeV3_1+shortDeviceIDRootSuffixSizeV3_1)
	shortDeviceID = append(shortDeviceID, c.DeviceModelID...)
	return append(shortDeviceID, rootSuffix...)
}

// parseVendorCertificateV3_1 decodes a vendor attestation certificate blob
// and returns its claims.
//
// It checks the envelope's shape: the pinned ES256 algorithm and RFC 9162
// data structure, an unprotected header carrying one inclusion proof, an
// ES256-sized signature, every required claim present and correctly sized,
// the oemid claim carrying Muun's enterprise number, and the commitment key
// decompressing to a point on P-256. It does NOT check the vendor's
// signature nor the proof; see the SECURITY note at the top of this file.
func parseVendorCertificateV3_1(blob []byte) (*vendorCertificateV3_1, error) {
	if len(blob) == 0 {
		return nil, errors.New("vendor certificate is empty")
	}
	if len(blob) > vendorCertificateMaxSizeV3_1 {
		return nil, errors.Errorf(
			"vendor certificate is %d bytes, over the %d a card can store",
			len(blob), vendorCertificateMaxSizeV3_1,
		)
	}

	var envelope coseSign1
	if err := strictCBOR.Unmarshal(blob, &envelope); err != nil {
		return nil, errors.Errorf("decode COSE_Sign1: %w", err)
	}
	if len(envelope.Signature) != coseES256SignatureLen {
		return nil, errors.Errorf(
			"ES256 signature must be %d bytes, got %d",
			coseES256SignatureLen, len(envelope.Signature),
		)
	}
	if err := checkCOSEProtectedHeaderV3_1(envelope.ProtectedHeader); err != nil {
		return nil, err
	}
	if err := checkCOSEUnprotectedHeaderV3_1(envelope.UnprotectedHeader); err != nil {
		return nil, err
	}

	return parseCWTClaimsV3_1(envelope.Payload)
}

func checkCOSEProtectedHeaderV3_1(header []byte) error {
	var decoded coseProtectedHeaderV3_1
	if err := strictCBOR.Unmarshal(header, &decoded); err != nil {
		return errors.Errorf("decode COSE protected header: %w", err)
	}
	if decoded.Algorithm != coseAlgorithmES256 {
		return errors.Errorf(
			"expected algorithm ES256(%d), got %d", coseAlgorithmES256, decoded.Algorithm,
		)
	}
	if decoded.VerifiableDataStructure != coseVDSRFC9162SHA256 {
		return errors.Errorf(
			"expected data structure RFC9162_SHA256(%d), got %d",
			coseVDSRFC9162SHA256, decoded.VerifiableDataStructure,
		)
	}
	return nil
}

func checkCOSEUnprotectedHeaderV3_1(header []byte) error {
	var decoded coseUnprotectedHeaderV3_1
	if err := strictCBOR.Unmarshal(header, &decoded); err != nil {
		return errors.Errorf("decode COSE unprotected header: %w", err)
	}
	proofs, ok := decoded.Proofs[coseVDPInclusionProofs]
	if !ok {
		return errors.New("COSE_Sign1 unprotected header carries no inclusion proof")
	}
	if len(proofs) != 1 {
		return errors.Errorf("expected one inclusion proof, got %d", len(proofs))
	}
	return nil
}

func parseCWTClaimsV3_1(payload []byte) (*vendorCertificateV3_1, error) {
	var claims cwtClaimsV3_1
	if err := strictCBOR.Unmarshal(payload, &claims); err != nil {
		return nil, errors.Errorf("decode CWT: %w", err)
	}

	if claims.IssuedAt == nil {
		return nil, errors.New("CWT is missing the iat claim")
	}
	if claims.OEMID == nil {
		return nil, errors.New("CWT is missing the oemid claim")
	}
	if *claims.OEMID != muunOEMID {
		return nil, errors.Errorf("oemid must be Muun's %d, got %d", muunOEMID, *claims.OEMID)
	}
	if len(claims.UEID) != ueidSizeV3_1 {
		return nil, errors.Errorf("ueid must be %d bytes, got %d", ueidSizeV3_1, len(claims.UEID))
	}
	if claims.UEID[0] != ueidTypeV3_1 {
		return nil, errors.Errorf(
			"ueid type must be 0x%02X, got 0x%02X", ueidTypeV3_1, claims.UEID[0],
		)
	}
	confirmKey, ok := claims.Confirm[cnfCOSEKey]
	if !ok {
		return nil, errors.New("CWT is missing the cnf claim's COSE_Key")
	}
	commitmentPub, err := parseCOSEKey(confirmKey)
	if err != nil {
		return nil, errors.Errorf("cnf: %w", err)
	}

	return newVendorCertificateV3_1(
		claims.HardwareModel,
		*claims.IssuedAt,
		claims.UEID[1:],
		commitmentPub,
	)
}
