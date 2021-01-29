package libwallet

import (
	"fmt"

	"github.com/btcsuite/btcd/btcec"
)

type PublicKey struct {
	key *btcec.PublicKey
}

func NewPublicKeyFromBytes(bytes []byte) (*PublicKey, error) {
	key, err := btcec.ParsePubKey(bytes, btcec.S256())
	if err != nil {
		return nil, fmt.Errorf("NewPublicKeyFromBytes: failed to parse pub key: %w", err)
	}

	return &PublicKey{key}, nil
}
