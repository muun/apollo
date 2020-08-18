package libwallet

import (
	"math"
	"testing"

	"github.com/btcsuite/btcutil/hdkeychain"
)

func TestHDPublicKey_DerivedAt(t *testing.T) {
	priv, _ := NewHDPrivateKey(randomBytes(32), Mainnet())

	_, err := priv.PublicKey().DerivedAt(math.MaxUint32)
	if err == nil {
		t.Errorf("derived a hardened pub key")
	}

	_, err = priv.PublicKey().DerivedAt(math.MaxUint32 ^ hdkeychain.HardenedKeyStart)
	if err != nil {
		t.Errorf("failed to derive unhardened pub key due to %v", err)
	}
}
