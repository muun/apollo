package simulator

import (
	"crypto/elliptic"
	"math/big"

	"github.com/fxamacker/cbor/v2"
	"github.com/go-errors/errors"
)

// The COSE and P-256 pieces every generation's certificate reader is built
// on: the strict CBOR decoder, the COSE_Sign1 envelope, the EC2 COSE_Key and
// SEC1 point decompression.

const (
	p256CoordinateSize    = 32
	secp256r1PointSize    = 1 + 2*p256CoordinateSize // uncompressed SEC1
	coseES256SignatureLen = 64

	// COSE registry values, from RFC 8152.
	coseAlgorithmES256 = -7
	coseKeyTypeEC2     = 2
	coseCurveP256      = 1

	// SEC1 point prefixes. A COSE_Key gives y's parity instead of a prefix
	// byte, so rebuilding one lets the standard library decompress.
	sec1UncompressedPrefix = 0x04
	sec1EvenYPrefix        = 0x02
	sec1OddYPrefix         = 0x03
)

// strictCBOR rejects anything but the exact wire format. The default DecMode
// tolerates duplicate keys, unknown fields and indefinite-length items, which
// would let a malformed certificate through carrying claims its issuer never
// wrote.
var strictCBOR = mustBuildStrictCBORDecoder()

func mustBuildStrictCBORDecoder() cbor.DecMode {
	decoder, err := cbor.DecOptions{
		DupMapKey:         cbor.DupMapKeyEnforcedAPF,
		IndefLength:       cbor.IndefLengthForbidden,
		ExtraReturnErrors: cbor.ExtraDecErrorUnknownField,
	}.DecMode()
	if err != nil {
		panic(errors.Errorf("build strict cbor decoder: %w", err))
	}
	return decoder
}

// coseSign1 is the COSE_Sign1 envelope, a four-element array.
type coseSign1 struct {
	_                 struct{} `cbor:",toarray"`
	ProtectedHeader   []byte
	UnprotectedHeader cbor.RawMessage
	Payload           []byte
	Signature         []byte
}

// coseKey is an EC2 COSE_Key. key_ops is optional: the spec carries it only
// on the commitment key, so the field has to decode either way. The parity
// of y is a pointer so that an absent one is told from an even y: a key
// without it cannot be decompressed.
type coseKey struct {
	KeyType int             `cbor:"1,keyasint"`
	KeyOps  cbor.RawMessage `cbor:"4,keyasint,omitempty"`
	Curve   int             `cbor:"-1,keyasint"`
	X       []byte          `cbor:"-2,keyasint"`
	YIsOdd  *bool           `cbor:"-3,keyasint"`
}

// parseCOSEKey decodes an EC2 COSE_Key and returns its point in uncompressed
// SEC1 form.
func parseCOSEKey(data []byte) ([]byte, error) {
	var key coseKey
	if err := strictCBOR.Unmarshal(data, &key); err != nil {
		return nil, errors.Errorf("decode COSE_Key: %w", err)
	}
	if key.KeyType != coseKeyTypeEC2 {
		return nil, errors.Errorf("kty must be EC2(%d), got %d", coseKeyTypeEC2, key.KeyType)
	}
	if key.Curve != coseCurveP256 {
		return nil, errors.Errorf("crv must be P-256(%d), got %d", coseCurveP256, key.Curve)
	}
	if len(key.X) != p256CoordinateSize {
		return nil, errors.Errorf("x must be %d bytes, got %d", p256CoordinateSize, len(key.X))
	}
	if key.YIsOdd == nil {
		return nil, errors.New("y parity is missing")
	}

	compressed := make([]byte, 1+p256CoordinateSize)
	if *key.YIsOdd {
		compressed[0] = sec1OddYPrefix
	} else {
		compressed[0] = sec1EvenYPrefix
	}
	copy(compressed[1:], key.X)

	return decompressP256(compressed)
}

// decompressP256 turns a SEC1 compressed point, parity prefix || x, into
// the uncompressed form the rest of the protocol speaks.
func decompressP256(compressed []byte) ([]byte, error) {
	// UnmarshalCompressed does the decompression along with the on-curve and
	// canonical-coordinate checks, and returns a nil x on any of them.
	x, y := elliptic.UnmarshalCompressed(elliptic.P256(), compressed)
	if x == nil {
		return nil, errors.New("x does not correspond to a point on P-256")
	}

	return marshalUncompressedP256(x, y), nil
}

func marshalUncompressedP256(x, y *big.Int) []byte {
	point := make([]byte, secp256r1PointSize)
	point[0] = sec1UncompressedPrefix
	x.FillBytes(point[1 : 1+p256CoordinateSize])
	y.FillBytes(point[1+p256CoordinateSize:])
	return point
}
