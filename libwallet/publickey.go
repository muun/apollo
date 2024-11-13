package libwallet

import (
	"fmt"

	"github.com/btcsuite/btcd/btcec/v2"
)

type PublicKey struct {
	key *btcec.PublicKey
}

func NewPublicKeyFromBytes(bytes []byte) (*PublicKey, error) {
	key, err := btcec.ParsePubKey(bytes)
	if err != nil {
		return nil, fmt.Errorf("NewPublicKeyFromBytes: failed to parse pub key: %w", err)
	}

	return &PublicKey{key}, nil
}
