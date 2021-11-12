package musig

import (
	"bytes"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec"
)

func TestSigning(t *testing.T) {
	toSign := [32]byte{1, 2, 3}

	userPriv, _ := btcec.NewPrivateKey(btcec.S256())
	muunPriv, _ := btcec.NewPrivateKey(btcec.S256())

	combined, err := CombinePubKeysWithTweak(userPriv.PubKey(), muunPriv.PubKey(), nil)
	if err != nil {
		t.Fatal(err)
	}

	userSessionId := RandomSessionId()
	muunSessionId := RandomSessionId()

	userPubNonces := GeneratePubNonce(userSessionId)
	muunPubNonces := GeneratePubNonce(muunSessionId)

	regeneratedUserNonces := GeneratePubNonce(userSessionId)
	if !bytes.Equal(userPubNonces[:], regeneratedUserNonces[:]) {
		t.Fatalf(
			"Nonces do not match %v != %v",
			hex.EncodeToString(userPubNonces[:]),
			hex.EncodeToString(regeneratedUserNonces[:]),
		)
	}

	muunSig, err := ComputeMuunPartialSignature(
		toSign,
		userPriv.PubKey(),
		muunPriv,
		userPubNonces,
		muunSessionId,
		nil,
	)
	if err != nil {
		t.Fatal(err)
	}

	fullSig, err := AddUserSignatureAndCombine(
		toSign,
		userPriv,
		muunPriv.PubKey(),
		muunSig,
		muunPubNonces,
		userSessionId,
		nil,
	)
	if err != nil {
		t.Fatal(err)
	}

	if !VerifySignature(toSign, fullSig, combined) {
		t.Fatal("failed to verify sig")
	}
}

func TestSigningWithCustomTweak(t *testing.T) {
	someRandomKey, _ := btcec.NewPrivateKey(btcec.S256())
	customTweak := someRandomKey.Serialize()

	toSign := [32]byte{1, 2, 3}

	userPriv, _ := btcec.NewPrivateKey(btcec.S256())
	muunPriv, _ := btcec.NewPrivateKey(btcec.S256())

	combined, err := CombinePubKeysWithTweak(userPriv.PubKey(), muunPriv.PubKey(), customTweak)
	if err != nil {
		t.Fatal(err)
	}

	userSessionId := RandomSessionId()
	muunSessionId := RandomSessionId()

	userPubNonces := GeneratePubNonce(userSessionId)
	muunPubNonces := GeneratePubNonce(muunSessionId)

	regeneratedUserNonces := GeneratePubNonce(userSessionId)
	if !bytes.Equal(userPubNonces[:], regeneratedUserNonces[:]) {
		t.Fatalf(
			"Nonces do not match %v != %v",
			hex.EncodeToString(userPubNonces[:]),
			hex.EncodeToString(regeneratedUserNonces[:]),
		)
	}

	muunSig, err := ComputeMuunPartialSignature(
		toSign,
		userPriv.PubKey(),
		muunPriv,
		userPubNonces,
		muunSessionId,
		customTweak,
	)
	if err != nil {
		t.Fatal(err)
	}

	fullSig, err := AddUserSignatureAndCombine(
		toSign,
		userPriv,
		muunPriv.PubKey(),
		muunSig,
		muunPubNonces,
		userSessionId,
		customTweak,
	)
	if err != nil {
		t.Fatal(err)
	}

	if !VerifySignature(toSign, fullSig, combined) {
		t.Fatal("failed to verify sig")
	}
}

func TestCrossWithJava(t *testing.T) {

	decode32Bytes := func(str string) (result [32]byte) {
		d, _ := hex.DecodeString(str)
		copy(result[:], d)
		return
	}

	rawUserPriv := decode32Bytes("6e39c6add6323a5ac5f65e50231fb815026476e734eb9f4f66dce3298fddf1dc")
	rawMuunPriv := decode32Bytes("b876ecf97c19588cf4be95ddc0b06c0d9f623f2cf679276c25e4dfb512b19743")
	userSessionId := decode32Bytes("52fdfc072182654f163f5f0f9a621d729566c74d10037c4d7bbb0407d1e2c649")
	muunSessionId := decode32Bytes("81855ad8681d0d86d1e91e00167939cb6694d2c422acd208a0072939487f6999")
	toSign := decode32Bytes("0102030000000000000000000000000000000000000000000000000000000000")

	expectedKey, _ := hex.DecodeString("027ca7eab04c2ad445418fa6a0ed2a331f121444aedd043adef94bdc00040ff96c")
	expectedSig, _ := hex.DecodeString("773ad923eb5eef593095a6787b674675de2a558335dc3e44fe40cf7b3736637e66d508e4eaadca28dab02e2fdcf5707392b561fffa1837205d2fa77b74cbc82f")

	userPriv, _ := btcec.PrivKeyFromBytes(btcec.S256(), rawUserPriv[:])
	muunPriv, _ := btcec.PrivKeyFromBytes(btcec.S256(), rawMuunPriv[:])
	combined, err := CombinePubKeysWithTweak(userPriv.PubKey(), muunPriv.PubKey(), nil)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(combined.SerializeCompressed(), expectedKey) {
		t.Fatal("Combined key doesn't match")
	}

	userPubNonces := GeneratePubNonce(userSessionId)
	muunPubNonces := GeneratePubNonce(muunSessionId)

	muunSig, err := ComputeMuunPartialSignature(
		toSign,
		userPriv.PubKey(),
		muunPriv,
		userPubNonces,
		muunSessionId,
		nil,
	)
	if err != nil {
		t.Fatal(err)
	}

	fullSig, err := AddUserSignatureAndCombine(
		toSign,
		userPriv,
		muunPriv.PubKey(),
		muunSig,
		muunPubNonces,
		userSessionId,
		nil,
	)
	if err != nil {
		t.Fatal(err)
	}

	if !VerifySignature(toSign, fullSig, combined) {
		t.Fatal("failed to verify sig")
	}
	if !bytes.Equal(fullSig[:], expectedSig) {
		t.Fatal("Signatures do no match")
	}
}

func TestCrossWithJavaUsingTweak(t *testing.T) {

	decode32Bytes := func(str string) (result [32]byte) {
		d, _ := hex.DecodeString(str)
		copy(result[:], d)
		return
	}

	tweak := decode32Bytes("99f8135f02f85b24687a7b221f3cbc5c8641851b52c6b1a305fc32f6b7259fa0")
	rawUserPriv := decode32Bytes("316f4ead37ad5ea564c9ca653a535cb8e5cad0fce249625372ba20453db01afe")
	rawMuunPriv := decode32Bytes("cbd712e93a39b0eac8b4ae2922de2baa7f3765172ddecf3109e5fce4bff24560")
	userSessionId := decode32Bytes("52fdfc072182654f163f5f0f9a621d729566c74d10037c4d7bbb0407d1e2c649")
	muunSessionId := decode32Bytes("81855ad8681d0d86d1e91e00167939cb6694d2c422acd208a0072939487f6999")
	toSign := decode32Bytes("0102030000000000000000000000000000000000000000000000000000000000")

	expectedKey, _ := hex.DecodeString("0348131bb4b25e17ad573923b3a24f0ac780a9cf5cefaefe44f0b762c1e83bfc06")
	expectedSig, _ := hex.DecodeString("1467fb39edcfc400f036ee97c0e5b0f10879e8f9c53de022c97fbd4e997b2ed8ad2a170747b3edbafd370e203c6284aad605486696887791c09d9e99195c2b85")

	userPriv, _ := btcec.PrivKeyFromBytes(btcec.S256(), rawUserPriv[:])
	muunPriv, _ := btcec.PrivKeyFromBytes(btcec.S256(), rawMuunPriv[:])
	combined, err := CombinePubKeysWithTweak(userPriv.PubKey(), muunPriv.PubKey(), tweak[:])
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(combined.SerializeCompressed(), expectedKey) {
		t.Fatal("Combined key doesn't match")
	}

	userPubNonces := GeneratePubNonce(userSessionId)
	muunPubNonces := GeneratePubNonce(muunSessionId)

	muunSig, err := ComputeMuunPartialSignature(
		toSign,
		userPriv.PubKey(),
		muunPriv,
		userPubNonces,
		muunSessionId,
		tweak[:],
	)
	if err != nil {
		t.Fatal(err)
	}

	fullSig, err := AddUserSignatureAndCombine(
		toSign,
		userPriv,
		muunPriv.PubKey(),
		muunSig,
		muunPubNonces,
		userSessionId,
		tweak[:],
	)
	if err != nil {
		t.Fatal(err)
	}

	if !VerifySignature(toSign, fullSig, combined) {
		t.Fatal("failed to verify sig")
	}
	if !bytes.Equal(fullSig[:], expectedSig) {
		t.Fatal("Signatures do no match")
	}
}
