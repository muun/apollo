package libwallet

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/go-errors/errors"
)

type PublicKey struct {
	key *btcec.PublicKey
}

func NewPublicKeyFromBytes(bytes []byte) (*PublicKey, error) {
	key, err := btcec.ParsePubKey(bytes)
	if err != nil {
		return nil, errors.Errorf("NewPublicKeyFromBytes: failed to parse pub key: %w", err)
	}

	return &PublicKey{key}, nil
}
