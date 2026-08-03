package musig

import (
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/go-errors/errors"
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

	sig, err := schnorr.ParseSignature(signature)
	if err != nil {
		return false, err
	}

	return sig.Verify(data, pubKey), nil
}

// SignSchnorr produces a BIP340 single-key Schnorr signature over the 32-byte data using the
// given 32-byte private key. Used for tapscript (BIP342) CHECKSIG spends, where each key signs
// on its own rather than as part of a MuSig aggregate.
func SignSchnorr(privateKeyBytes []byte, data []byte) ([]byte, error) {
	privateKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)

	sig, err := schnorr.Sign(privateKey, data)
	if err != nil {
		return nil, errors.Errorf("schnorr sign: %w", err)
	}

	return sig.Serialize(), nil
}
