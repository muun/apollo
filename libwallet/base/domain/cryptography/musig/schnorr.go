package musig

import (
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/musig/internal/libsecp256k1"
)

// VerifySignature checks that signature is a valid schnorr signature of data
// by publicKey.
func VerifySignature(
	musigVersion MusigVersion,
	data []byte,
	publicKey []byte,
	signature []byte,
) (bool, error) {

	pubKey, err := ParsePubKey(musigVersion, publicKey)
	if err != nil {
		return false, err
	}

	err = libsecp256k1.VerifySignature(signature, data, schnorr.SerializePubKey(pubKey))
	if errors.Is(err, libsecp256k1.ErrInvalidSignature) {
		return false, nil
	}
	if err != nil {
		return false, err
	}

	return true, nil
}

// SignSchnorr produces a BIP340 single-key Schnorr signature over the 32-byte data using the
// given 32-byte private key. Used for tapscript (BIP342) CHECKSIG spends, where each key signs
// on its own rather than as part of a MuSig aggregate.
func SignSchnorr(privateKeyBytes []byte, data []byte) ([]byte, error) {
	return libsecp256k1.SignSchnorr(data, privateKeyBytes)
}
