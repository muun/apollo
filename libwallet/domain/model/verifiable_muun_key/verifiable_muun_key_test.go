package verifiable_muun_key

import (
	"encoding/hex"
	"github.com/btcsuite/btcd/btcec/v2"
	"testing"
)

func TestSubtractPublicKeys(t *testing.T) {

	aSerialization, err := hex.DecodeString("028e949335c8d8bc841167860949c990ac10c87004f74c4513c39603dddf687dbb")
	if err != nil {
		t.Fatal(err)
	}

	bSerialization, err := hex.DecodeString("0316e7c706d5bfd42194360e7109d0717c18bdba36c24442af99555dc981d1a66b")
	if err != nil {
		t.Fatal(err)
	}

	aMinusBExpected := "03d8047be580c609b8f63ba3c9ffb4b4bf20da0ae655d651fc95afb4f087b9f3d5"

	A, err := btcec.ParsePubKey(aSerialization)
	if err != nil {
		t.Fatal(err)
	}

	B, err := btcec.ParsePubKey(bSerialization)
	if err != nil {
		t.Fatal(err)
	}

	if hex.EncodeToString(subtractPublicKeys(A, B).SerializeCompressed()) != aMinusBExpected {
		t.Fatalf("incorrect value for subtractPublicKeys(A,B)")
	}
}
