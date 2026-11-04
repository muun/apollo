package simulator

import (
	"bytes"

	"github.com/fxamacker/cbor/v2"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography"
)

// Reader for the vendor attestation certificate as firmware 3.0 carries it:
// a COSE_Sign1 envelope over a CWT payload, signed by the vendor with its
// attestation key. The card stores the blob opaquely and never parses it.
//
// Mirrors cards/vendor-cli/muuncardv3/cert.go, which builds and verifies
// certificates for the ceremony. Houston only parses them.
//
// SECURITY: parseVendorCertificateV3_0 does NOT verify the vendor's
// signature, so every claim it returns is unauthenticated. That check needs
// the attestation key registered for the device model, which nothing tracks
// yet. Reading the key agreement key out unverified is still sound for the
// pairing itself: a forged key yields a DH1 the card cannot match, so the
// MAC fails.

const (
	deviceModelUUIDSizeV3_0 = 16
	serialNumberSizeV3_0    = 16

	// cborEmptyMap is the one valid encoding of {}, so comparing the byte is
	// enough to reject a non-empty unprotected header.
	cborEmptyMap = 0xA0
)

// vendorCertificateV3_0 holds the claims of a parsed vendor attestation
// certificate.
//
// Both public keys come back in the 65-byte uncompressed SEC1 encoding the
// rest of the protocol speaks; the certificate stores them point-compressed.
type vendorCertificateV3_0 struct {
	DeviceModelUUID []byte // 16 bytes, the CWT iss claim
	SerialNumber    []byte // 16 bytes, the CWT sub claim
	IssuedAt        uint32 // unix epoch seconds, the CWT iat claim
	KeyAgreementPub []byte // 65 bytes, the card's static key for the pairing ECDH
	CommitmentPub   []byte // 65 bytes, verifies the card's action approvals
}

// newVendorCertificateV3_0 builds the certificate's claims. They come out of
// a blob the card handed back, so a wrong size or an off-curve key returns
// an error rather than panicking.
func newVendorCertificateV3_0(
	deviceModelUUID []byte,
	serialNumber []byte,
	issuedAt uint32,
	keyAgreementPub []byte,
	commitmentPub []byte,
) (*vendorCertificateV3_0, error) {
	if len(deviceModelUUID) != deviceModelUUIDSizeV3_0 {
		return nil, errors.Errorf(
			"device model UUID must be %d bytes, got %d",
			deviceModelUUIDSizeV3_0, len(deviceModelUUID),
		)
	}
	if len(serialNumber) != serialNumberSizeV3_0 {
		return nil, errors.Errorf(
			"serial number must be %d bytes, got %d", serialNumberSizeV3_0, len(serialNumber),
		)
	}
	if err := cryptography.ValidateSecp256r1PublicKey(keyAgreementPub); err != nil {
		return nil, errors.Errorf("key agreement key: %w", err)
	}
	if err := cryptography.ValidateSecp256r1PublicKey(commitmentPub); err != nil {
		return nil, errors.Errorf("commitment key: %w", err)
	}

	return &vendorCertificateV3_0{
		DeviceModelUUID: deviceModelUUID,
		SerialNumber:    serialNumber,
		IssuedAt:        issuedAt,
		KeyAgreementPub: keyAgreementPub,
		CommitmentPub:   commitmentPub,
	}, nil
}

type coseProtectedHeader struct {
	Algorithm int `cbor:"1,keyasint"`
}

// cwtClaims is the decode-side view of the CWT payload. Pointer and raw
// fields let the parser tell an absent claim from one carrying a zero value.
type cwtClaims struct {
	Issuer     []byte          `cbor:"1,keyasint"`
	Subject    []byte          `cbor:"2,keyasint"`
	IssuedAt   *uint32         `cbor:"6,keyasint"`
	Confirm    cbor.RawMessage `cbor:"8,keyasint"`
	Commitment cbor.RawMessage `cbor:"-1,keyasint"`
}

// parseVendorCertificateV3_0 decodes a vendor attestation certificate blob
// and returns its claims.
//
// It checks the envelope's shape: the pinned ES256 algorithm, empty
// unprotected headers, an ES256-sized signature, every required claim present
// and correctly sized, and both public keys decompressing to points on P-256.
// It does NOT check the vendor's signature, so the result is not attested;
// see the SECURITY note at the top of this file.
func parseVendorCertificateV3_0(blob []byte) (*vendorCertificateV3_0, error) {
	if len(blob) == 0 {
		return nil, errors.New("vendor certificate is empty")
	}
	if len(blob) > vendorCertificateMaxSizeV3_0 {
		return nil, errors.Errorf(
			"vendor certificate is %d bytes, over the %d a card can store",
			len(blob), vendorCertificateMaxSizeV3_0,
		)
	}

	var envelope coseSign1
	if err := strictCBOR.Unmarshal(blob, &envelope); err != nil {
		return nil, errors.Errorf("decode COSE_Sign1: %w", err)
	}
	if !bytes.Equal(envelope.UnprotectedHeader, []byte{cborEmptyMap}) {
		return nil, errors.New("COSE_Sign1 unprotected headers must be empty")
	}
	if len(envelope.Signature) != coseES256SignatureLen {
		return nil, errors.Errorf(
			"ES256 signature must be %d bytes, got %d",
			coseES256SignatureLen, len(envelope.Signature),
		)
	}
	if err := checkCOSEProtectedHeader(envelope.ProtectedHeader); err != nil {
		return nil, err
	}

	return parseCWTClaims(envelope.Payload)
}

func checkCOSEProtectedHeader(header []byte) error {
	var decoded coseProtectedHeader
	if err := strictCBOR.Unmarshal(header, &decoded); err != nil {
		return errors.Errorf("decode COSE protected header: %w", err)
	}
	if decoded.Algorithm != coseAlgorithmES256 {
		return errors.Errorf(
			"expected algorithm ES256(%d), got %d",
			coseAlgorithmES256, decoded.Algorithm,
		)
	}
	return nil
}

func parseCWTClaims(payload []byte) (*vendorCertificateV3_0, error) {
	var claims cwtClaims
	if err := strictCBOR.Unmarshal(payload, &claims); err != nil {
		return nil, errors.Errorf("decode CWT: %w", err)
	}

	if claims.IssuedAt == nil {
		return nil, errors.New("CWT is missing the iat claim")
	}
	if claims.Confirm == nil {
		return nil, errors.New("CWT is missing the cnf claim")
	}
	if claims.Commitment == nil {
		return nil, errors.New("CWT is missing the commitment claim")
	}
	keyAgreementPub, err := parseCOSEKey(claims.Confirm)
	if err != nil {
		return nil, errors.Errorf("cnf: %w", err)
	}

	commitmentPub, err := parseCOSEKey(claims.Commitment)
	if err != nil {
		return nil, errors.Errorf("commitment: %w", err)
	}

	return newVendorCertificateV3_0(
		claims.Issuer,
		claims.Subject,
		*claims.IssuedAt,
		keyAgreementPub,
		commitmentPub,
	)
}
