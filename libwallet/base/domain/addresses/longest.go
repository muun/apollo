package addresses

import (
	"github.com/btcsuite/btcd/chaincfg"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/platform/preconditions"
)

// estimationKeyPath is the address path the canned estimation key is labeled at.
const estimationKeyPath = hd_keys.SchemaBasePath + "/external:1/0"

// longestHDPublicKey returns a sample public key for size estimation: key
// sizes are constant, so any key yields the exact sizes. The same key as the
// backend's TransactionScheme.LONGEST_PUBLIC_KEY.
func longestHDPublicKey() *hd_keys.HDPublicKey {
	key, err := hd_keys.NewHDPublicKeyFromString(
		"xpub6BpNGuKmmJnFBcqr9aWG5fVuD9qrb6RpLhbWARcvxQoVLoBbXnvq3t3NRcPMLkp7B2GZ5a8dhi3"+
			"XzEvQHwEQVLnANBuDRr3NzbAPhjrtVNK",
		estimationKeyPath,
		&chaincfg.MainNetParams,
	)
	preconditions.CheckStatef(err == nil, "parse longest public key: %v", err)
	return key
}

// longestPublicKeyTriple returns a triple of the canned estimation key.
func longestPublicKeyTriple() *hd_keys.PublicKeyTriple {
	key := longestHDPublicKey()
	return hd_keys.NewPublicKeyTriple(key, key, key)
}

// longestStandardECDSASignature returns a placeholder sized as the longest
// standard ECDSA signature: 71 DER bytes plus 1 sighash byte. The 72-byte
// total matches the backend's TransactionScheme.LONGEST_STANDARD_ECDSA_SIGNATURE,
// which encodes R = 2^256 - 1 (33 bytes once 0x00-padded) and the largest
// low S = 2^255 - 1 (32 bytes) and appends the sighash byte.
func longestStandardECDSASignature() []byte {
	return make([]byte, 72)
}
