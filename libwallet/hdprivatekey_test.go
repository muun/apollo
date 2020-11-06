package libwallet

import (
	"crypto/sha256"
	"testing"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcd/chaincfg"
)

const (
	// m
	vector1PrivKey = "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi"
	vector1PubKey  = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"

	vector1FirstPath = "m/0'/1"
	vector1FirstPriv = "xprv9wTYmMFdV23N2TdNG573QoEsfRrWKQgWeibmLntzniatZvR9BmLnvSxqu53Kw1UmYPxLgboyZQaXwTCg8MSY3H2EU4pWcQDnRnrVA1xe8fs"
	vector1FirstPub  = "xpub6ASuArnXKPbfEwhqN6e3mwBcDTgzisQN1wXN9BJcM47sSikHjJf3UFHKkNAWbWMiGj7Wf5uMash7SyYq527Hqck2AxYysAA7xmALppuCkwQ"

	vector1SecondPath = "m/0'/1/2'/2"
	vector1SecondPriv = "xprvA2JDeKCSNNZky6uBCviVfJSKyQ1mDYahRjijr5idH2WwLsEd4Hsb2Tyh8RfQMuPh7f7RtyzTtdrbdqqsunu5Mm3wDvUAKRHSC34sJ7in334"
	vector1SecondPub  = "xpub6FHa3pjLCk84BayeJxFW2SP4XRrFd1JYnxeLeU8EqN3vDfZmbqBqaGJAyiLjTAwm6ZLRQUMv1ZACTj37sR62cfN7fe5JnJ7dh8zL4fiyLHV"

	vector2PrivKey = "xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8NSpSBHNqPqm55Qn3LqFtT2emdEXVYsCzC2U"
	vector2PubKey  = "xpub661MyMwAqRbcFW31YEwpkMuc5THy2PSt5bDMsktWQcFF8syAmRUapSCGu8ED9W6oDMSgv6Zz8idoc4a6mr8BDzTJY47LJhkJ8UB7WEGuduB"

	vector2FirstPath = "m/0"
	vector2FirstPriv = "xprv9vHkqa6EV4sPZHYqZznhT2NPtPCjKuDKGY38FBWLvgaDx45zo9WQRUT3dKYnjwih2yJD9mkrocEZXo1ex8G81dwSM1fwqWpWkeS3v86pgKt"
	vector2FirstPub  = "xpub69H7F5d8KSRgmmdJg2KhpAK8SR3DjMwAdkxj3ZuxV27CprR9LgpeyGmXUbC6wb7ERfvrnKZjXoUmmDznezpbZb7ap6r1D3tgFxHmwMkQTPH"

	vector2SecondPath = "m/0/2147483647'/1"
	vector2SecondPriv = "xprv9zFnWC6h2cLgpmSA46vutJzBcfJ8yaJGg8cX1e5StJh45BBciYTRXSd25UEPVuesF9yog62tGAQtHjXajPPdbRCHuWS6T8XA2ECKADdw4Ef"
	vector2SecondPub  = "xpub6DF8uhdarytz3FWdA8TvFSvvAh8dP3283MY7p2V4SeE2wyWmG5mg5EwVvmdMVCQcoNJxGoWaU9DCWh89LojfZ537wTfunKau47EL2dhHKon"

	vector2ThirdPath = "m/0/2147483647'/1/2147483646'"
	vector2ThirdPriv = "xprvA1RpRA33e1JQ7ifknakTFpgNXPmW2YvmhqLQYMmrj4xJXXWYpDPS3xz7iAxn8L39njGVyuoseXzU6rcxFLJ8HFsTjSyQbLYnMpCqE2VbFWc"
	vector2ThirdPub  = "xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL"

	vector3PrivKey = "xprv9s21ZrQH143K25QhxbucbDDuQ4naNntJRi4KUfWT7xo4EKsHt2QJDu7KXp1A3u7Bi1j8ph3EGsZ9Xvz9dGuVrtHHs7pXeTzjuxBrCmmhgC6"
	vector3PubKey  = "xpub661MyMwAqRbcEZVB4dScxMAdx6d4nFc9nvyvH3v4gJL378CSRZiYmhRoP7mBy6gSPSCYk6SzXPTf3ND1cZAceL7SfJ1Z3GC8vBgp2epUt13"

	vector3FirstPath = "m/0'"
	vector3FirstPriv = "xprv9uPDJpEQgRQfDcW7BkF7eTya6RPxXeJCqCJGHuCJ4GiRVLzkTXBAJMu2qaMWPrS7AANYqdq6vcBcBUdJCVVFceUvJFjaPdGZ2y9WACViL4L"
	vector3FirstPub  = "xpub68NZiKmJWnxxS6aaHmn81bvJeTESw724CRDs6HbuccFQN9Ku14VQrADWgqbhhTHBaohPX4CjNLf9fq9MYo6oDaPPLPxSb7gwQN3ih19Zm4Y"

	symmetricPrivKey = "xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8NSpSBHNqPqm55Qn3LqFtT2emdEXVYsCzC2U"
	symmetricPubKey  = "xpub661MyMwAqRbcFW31YEwpkMuc5THy2PSt5bDMsktWQcFF8syAmRUapSCGu8ED9W6oDMSgv6Zz8idoc4a6mr8BDzTJY47LJhkJ8UB7WEGuduB"

	symmetricFirstPath = "m/0"
	symmetricFirstPriv = "xprv9vHkqa6EV4sPZHYqZznhT2NPtPCjKuDKGY38FBWLvgaDx45zo9WQRUT3dKYnjwih2yJD9mkrocEZXo1ex8G81dwSM1fwqWpWkeS3v86pgKt"
	symmetricFirstPub  = "xpub69H7F5d8KSRgmmdJg2KhpAK8SR3DjMwAdkxj3ZuxV27CprR9LgpeyGmXUbC6wb7ERfvrnKZjXoUmmDznezpbZb7ap6r1D3tgFxHmwMkQTPH"

	symmetricSecondPath = "m/0/2147483647'/1"
	symmetricSecondPriv = "xprv9zFnWC6h2cLgpmSA46vutJzBcfJ8yaJGg8cX1e5StJh45BBciYTRXSd25UEPVuesF9yog62tGAQtHjXajPPdbRCHuWS6T8XA2ECKADdw4Ef"
	symmetricSecondPub  = "xpub6DF8uhdarytz3FWdA8TvFSvvAh8dP3283MY7p2V4SeE2wyWmG5mg5EwVvmdMVCQcoNJxGoWaU9DCWh89LojfZ537wTfunKau47EL2dhHKon"

	symmetricThirdPath = "m/0/2147483647'/1/2147483646'"
	symmetricThirdPriv = "xprvA1RpRA33e1JQ7ifknakTFpgNXPmW2YvmhqLQYMmrj4xJXXWYpDPS3xz7iAxn8L39njGVyuoseXzU6rcxFLJ8HFsTjSyQbLYnMpCqE2VbFWc"
	symmetricThirdPub  = "xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL"
)

var (
	network = Regtest()
)

func TestNewHDPrivateKeySerialization(t *testing.T) {

	t.Run("bad key generation", func(t *testing.T) {
		badKey, err := NewHDPrivateKey(randomBytes(1), network)
		if badKey != nil || err == nil {
			t.Errorf("keys with only 1 byte should return error, got %v, %v", badKey, err)
		}
	})

	t.Run("invalid key deserialization", func(t *testing.T) {
		badKey, err := NewHDPrivateKeyFromString("fooo", "m", Regtest())
		if badKey != nil || err == nil {
			t.Errorf("bad key should only return error returned %v, %v", badKey, err)
		}

		badKey, err = NewHDPrivateKeyFromString(vector1FirstPub, "m", Regtest())
		if badKey != nil || err == nil {
			t.Errorf("expected failure when parsing pub key as priv key, got %v, %v", badKey, err)
		}

		badPubKey, err := NewHDPublicKeyFromString(vector1FirstPriv, "m", Regtest())
		if badPubKey != nil || err == nil {
			t.Errorf("expected failure when parsing priv key as pub key, got %v, %v", badPubKey, err)
		}
	})

	t.Run("test regtest address serialization", func(t *testing.T) {
		// Create a new key and set regtest as chain
		randomKey, _ := NewHDPrivateKey(randomBytes(16), network)
		randomKey.key.SetNet(&chaincfg.RegressionNetParams)

		// Parsing it should fail since we check we know the chain
		key, err := NewHDPrivateKeyFromString(randomKey.String(), "m", Regtest())
		if key == nil || err != nil {
			t.Errorf("failed to parse regtest key, got err %v", err)
		}
	})

	t.Run("Random key serialization", func(t *testing.T) {
		seed := randomBytes(16)
		randomKey, err := NewHDPrivateKey(seed, network)
		if err != nil {
			t.Fatalf("couldn't generate priv key")
		}

		serialized := randomKey.String()
		deserialized, err := NewHDPrivateKeyFromString(serialized, "m", Regtest())
		if err != nil {
			t.Fatalf("failed to deserialize key")
		}

		if serialized != deserialized.String() {
			t.Errorf("keys are different")
		}
	})

	t.Run("Child key serialization", func(t *testing.T) {
		root, err := NewHDPrivateKeyFromString(
			"tprv8ZgxMBicQKsPdGCzsJ31BsQnFL1TSQ82dfsZYTtsWJ1T8g7xTfnV19gf8nYPqzkzk6yLL9kzDYshmUrYyXt7uXsGbk9eN7juRxg9sjaxSjn", "m", Regtest())
		if err != nil {
			t.Fatalf("failed to parse root key")
		}

		key1, _ := root.DerivedAt(1, true)
		key2, _ := key1.DerivedAt(1, true)

		const encodedKey = "tprv8e8vMhwEcLr1ZfZETKTQSpxJ6KfZuczALe8KrRCDLpSbXPwp7PY1ZVHtqUkFsYZETPRcfjVSCv8DiYP9RyAZrFhnLE8aYdaSaZEWyT5c8Ji"
		if key2.String() != encodedKey {
			t.Fatalf("derived key doesn't match serialized")
		}

		decodedKey, _ := NewHDPrivateKeyFromString(encodedKey, "m", Regtest())

		if decodedKey.String() != encodedKey {
			t.Fatalf("decoded key doesn't match encoded string")
		}

	})
}

// These tests are based on the test vectors in BIP 32
// https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki#test-vectors
func TestKeyDerivation(t *testing.T) {

	testPath := func(t *testing.T, privKey *HDPrivateKey, path, priv, pub string) {
		key, _ := privKey.DeriveTo(path)
		if key.String() != priv {
			t.Errorf("%v priv doesn't match", path)
		}

		if key.PublicKey().String() != pub {
			t.Errorf("%v pub doesn't match", path)
		}
	}

	t.Run("vector1", func(t *testing.T) {
		privKey, _ := NewHDPrivateKeyFromString(vector1PrivKey, "m", Regtest())
		if privKey.PublicKey().String() != vector1PubKey {
			t.Errorf("pub key doesnt match")
		}

		testPath(t, privKey, vector1FirstPath, vector1FirstPriv, vector1FirstPub)
		testPath(t, privKey, vector1SecondPath, vector1SecondPriv, vector1SecondPub)
	})

	t.Run("vector2", func(t *testing.T) {
		privKey, _ := NewHDPrivateKeyFromString(vector2PrivKey, "m", Regtest())
		if privKey.PublicKey().String() != vector2PubKey {
			t.Errorf("pub key doesnt match")
		}

		testPath(t, privKey, vector2FirstPath, vector2FirstPriv, vector2FirstPub)
		testPath(t, privKey, vector2SecondPath, vector2SecondPriv, vector2SecondPub)
		testPath(t, privKey, vector2ThirdPath, vector2ThirdPriv, vector2ThirdPub)
	})

	t.Run("vector3", func(t *testing.T) {
		privKey, _ := NewHDPrivateKeyFromString(vector3PrivKey, "m", Regtest())
		if privKey.PublicKey().String() != vector3PubKey {
			t.Errorf("pub key doesnt match")
		}

		testPath(t, privKey, vector3FirstPath, vector3FirstPriv, vector3FirstPub)
	})
}

func TestSymmetricDerivation(t *testing.T) {

	privKey, _ := NewHDPrivateKeyFromString(symmetricPrivKey, "m", Regtest())
	pubKey := privKey.PublicKey()

	t.Run("basic check", func(t *testing.T) {
		if pubKey.String() != symmetricPubKey {
			t.Fatalf("pub key doesn't match")
		}
	})

	t.Run("first path", func(t *testing.T) {
		newPriv, _ := privKey.DeriveTo(symmetricFirstPath)
		if newPriv.String() != symmetricFirstPriv {
			t.Errorf("priv key doesn't match")
		}

		if newPriv.PublicKey().String() != symmetricFirstPub {
			t.Errorf("pub key doesn't match")
		}

		newPub, _ := pubKey.DeriveTo(symmetricFirstPath)
		if newPub.String() != newPriv.PublicKey().String() {
			t.Errorf("extracted and derived pub key don't match")
		}
	})

	t.Run("second path", func(t *testing.T) {
		newPriv, _ := privKey.DeriveTo(symmetricSecondPath)
		if newPriv.String() != symmetricSecondPriv {
			t.Errorf("priv key doesn't match")
		}

		if newPriv.PublicKey().String() != symmetricSecondPub {
			t.Errorf("pub key doesn't match")
		}

		hPriv, _ := privKey.DeriveTo("m/0/2147483647'")
		newPub, _ := hPriv.PublicKey().DeriveTo(symmetricSecondPath)
		if newPub.String() != newPriv.PublicKey().String() {
			t.Errorf("extracted and derived pub key don't match")
		}
	})

	t.Run("third path", func(t *testing.T) {
		newPriv, _ := privKey.DeriveTo(symmetricThirdPath)
		if newPriv.String() != symmetricThirdPriv {
			t.Errorf("priv key doesn't match")
		}

		if newPriv.PublicKey().String() != symmetricThirdPub {
			t.Errorf("pub key doesn't match")
		}
	})

	testBadDerivation := func(t *testing.T, path string) {
		privKey, _ := NewHDPrivateKeyFromString(vector1PrivKey, "m/123", Regtest())
		pubKey := privKey.PublicKey()

		badKey, err := privKey.DeriveTo(path)
		if badKey != nil || err == nil {
			t.Errorf("derivation should fail got %v, %v", badKey, err)
		}

		badPubKey, err := pubKey.DeriveTo(path)
		if badPubKey != nil || err == nil {
			t.Errorf("derivation should fail got %v, %v", badPubKey, err)
		}
	}

	t.Run("new path is not prefix of old path", func(t *testing.T) {
		testBadDerivation(t, "m/45")
	})

	t.Run("derivation path is invalid", func(t *testing.T) {
		testBadDerivation(t, "m/123/asd45")
	})
}

func TestHDPrivateKeySign(t *testing.T) {
	seed := randomBytes(32)
	privKey, err := NewHDPrivateKey(seed, Regtest())
	if err != nil {
		t.Fatal(err)
	}
	data := []byte("foobar")
	sigBytes, err := privKey.Sign(data)
	if err != nil {
		t.Fatal(err)
	}
	sig, err := btcec.ParseSignature(sigBytes, btcec.S256())
	if err != nil {
		t.Fatal(err)
	}
	pubKey, err := privKey.key.ECPubKey()
	if err != nil {
		t.Fatal(err)
	}
	hash := sha256.Sum256(data)
	if ok := sig.Verify(hash[:], pubKey); !ok {
		t.Fatal(err)
	}
}
