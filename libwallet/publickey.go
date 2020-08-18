package libwallet

import (
	"github.com/btcsuite/btcd/btcec"
	"github.com/pkg/errors"
)

type PublicKey struct {
	key *btcec.PublicKey
}

func NewPublicKeyFromBytes(bytes []byte) (*PublicKey, error) {
	key, err := btcec.ParsePubKey(bytes, btcec.S256())
	if err != nil {
		return nil, errors.Wrapf(err, "NewPublicKeyFromBytes: failed to parse pub key")
	}

	return &PublicKey{key}, nil
}
