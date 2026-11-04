package nfc

// The COSE pieces every generation's mock certificate encoder is built on: the
// EC2 COSE_Key a certificate carries a key in, and the registry values around
// it.

const (
	p256CoordinateSize    = 32
	coseES256SignatureLen = 64

	// COSE registry values, from RFC 8152.
	coseAlgorithmES256 = -7
	coseKeyTypeEC2     = 2
	coseCurveP256      = 1
)

// buildCOSEKeyV3 lays a public key out as an EC2 COSE_Key, which stores x and
// the parity of y rather than an uncompressed point.
func buildCOSEKeyV3(publicKey []byte, keyOps []int) map[int]any {
	key := map[int]any{
		1:  coseKeyTypeEC2,
		-1: coseCurveP256,
		-2: publicKey[1 : 1+p256CoordinateSize],
		-3: publicKey[len(publicKey)-1]&1 == 1,
	}
	if keyOps != nil {
		key[4] = keyOps
	}
	return key
}
