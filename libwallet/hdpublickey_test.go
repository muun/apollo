package libwallet

import (
	"bytes"
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

func TestHDPublicKey_Fingerprint(t *testing.T) {
	pubKey, _ := NewHDPublicKeyFromString(
		"xpub661MyMwAqRbcF3YgLe8xTTTrDHf5bmEQuj5XfQP3bvwHqBpYvt99tcMSXXzroWJoQM4eMDNZNzNYZEJfTqxq5S82J644buASmW4Y7VnwUeJ",
		"m/schema:1'/recovery:1'",
		Mainnet(),
	)

	fingerprint := pubKey.Fingerprint()
	if !bytes.Equal(fingerprint, []byte{207, 227, 7, 97}) {
		t.Fatalf("fingerprint does not match, got %x", fingerprint)
	}
}
