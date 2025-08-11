package nfc

import (
	"encoding/hex"
	"errors"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/muun/libwallet"
	"testing"
)

func TestMuunCardSetupAndResetCard_Integration(t *testing.T) {

	mockNfcBridge := &MockNfcBridge{
		mockCard: &MockMuunCard{
			network:         libwallet.Mainnet(),
			privateKeySlots: make([]*libwallet.HDPrivateKey, 1),
		},
	}

	muuncard := NewCard(mockNfcBridge)

	// 1. Set up Card (e.g. generate xpub)

	seedBytes := []byte("0123456789ABCDEF")
	seed16Bytes := [16]byte{}
	copy(seed16Bytes[:], seedBytes)

	xpub, err := muuncard.GenerateKeyPair(seed16Bytes)
	if err != nil {
		t.Fatal(err)
	}

	generatedXpub := base58.Encode(xpub.RawBytes)

	expectedXpubInBase58 := "xpub661MyMwAqRbcGh9azVFw7ed84cvzSBBSN3rGaTCwJnb9ZFtyj3RMkphXKv4bTLJmrayqR6Vt4PFKw7UUktXXdUqTpDj2dT6T7zWSbySR7jc"
	if generatedXpub != expectedXpubInBase58 {
		t.Fatalf("wanted %v, but got %v", expectedXpubInBase58, generatedXpub)
	}

	// 2. Reset Card

	err = muuncard.ResetCard()

	if err != nil {
		t.Fatal(err)
	}

	// 3. Reset Card again (should fail)

	err = muuncard.ResetCard()

	if err == nil {
		t.Fatal("expected error: ErrSlotNotInitialized")
	}

	var cardError *CardError
	if errors.As(err, &cardError) {
		if cardError.Code != ErrSlotNotInitialized {
			t.Fatalf("expected error: ErrSlotNotInitialized, got: %s", err)
		}
	}

	// 4. Set up card again, different seed generates different xpub

	seedBytes = []byte("0000000000000000")
	seed16Bytes = [16]byte{}
	copy(seed16Bytes[:], seedBytes)
	xpub, err = muuncard.GenerateKeyPair(seed16Bytes)

	if err != nil {
		t.Fatal(err)
	}

	generatedXpub = base58.Encode(xpub.RawBytes)

	expectedXpubInBase58 = "xpub661MyMwAqRbcFSR1jrK1urk2s2yKY4avX58e5PsG9bZds6Yj5n5Ya1TVBnyDMqWUANnoQPcCCTohfzbzpNEyX1ZAPzw76iproJpWJJYS6sT"
	if generatedXpub != expectedXpubInBase58 {
		t.Fatalf("wanted %v, but got %v", expectedXpubInBase58, generatedXpub)
	}

	// 5. Set up card again without reset (should fail)

	xpub, err = muuncard.GenerateKeyPair(seed16Bytes)

	if err == nil {
		t.Fatal("expected error: ErrSlotOccupied")
	}

	if errors.As(err, &cardError) {
		if cardError.Code != ErrSlotOccupied {
			t.Fatalf("expected error: ErrSlotOccupied, got: %s", err)
		}
	}

	if xpub != nil {
		t.Fatal("xpub should be nil on error")
	}
}

func TestMuunCardSignMessage_Integration(t *testing.T) {

	mockNfcBridge := &MockNfcBridge{
		mockCard: &MockMuunCard{
			network:         libwallet.Mainnet(),
			privateKeySlots: make([]*libwallet.HDPrivateKey, 1),
		},
	}

	muuncard := NewCard(mockNfcBridge)

	// 1. Set up Card (e.g. generate xpub)

	seedBytes := []byte("1111111111111111")
	seed16Bytes := [16]byte{}
	copy(seed16Bytes[:], seedBytes)

	xpub, err := muuncard.GenerateKeyPair(seed16Bytes)
	if err != nil {
		t.Fatal(err)
	}

	generatedXpub := base58.Encode(xpub.RawBytes)

	expectedXpubInBase58 := "xpub661MyMwAqRbcGeKCHGwgz2ViScN6rkHwoRqnRDMqDEGWCXkjUAY5Hv2TCud3MtQTVhvzYenmLhAkLXnwDo3x2cHKCicWU3qH4XRRvh63zQd"
	if generatedXpub != expectedXpubInBase58 {
		t.Fatalf("wanted %v, but got %v", expectedXpubInBase58, generatedXpub)
	}

	// 2. Sign Message

	message := "testing libwallet muuncard impl"
	signature, err := muuncard.SignMessage(message)
	if err != nil {
		t.Fatal(err)
	}

	// 3. Verify Signature
	pubKey, err := libwallet.NewHDPublicKeyFromString(generatedXpub, "m", libwallet.Mainnet())
	if err != nil {
		t.Fatal(err)
	}

	verified, err := muuncard.VerifySignature(pubKey, []byte(message), signature.RawBytes)
	if err != nil {
		t.Fatal(err)
	}
	if !verified {
		signatureHex := hex.EncodeToString(signature.RawBytes)
		t.Fatalf("invalid signature: %s", signatureHex)
	}
}
