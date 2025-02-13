package encryption

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"strings"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/muun/libwallet/hdpath"
)

func cryptersFromKey(key *hdkeychain.ExtendedKey) (HdPubKeyEncrypter, HdPrivKeyDecrypter) {

	decrypter := HdPrivKeyDecrypter{
		KeyProvider: keyProvider{key, "m"},
		SenderKey:   nil,
		FromSelf:    true,
	}
	ecPrivKey, _ := key.ECPrivKey()

	encrypter := HdPubKeyEncrypter{
		ReceiverKey:     ecPrivKey.PubKey(),
		ReceiverKeyPath: "m",
		SenderKey:       ecPrivKey,
	}

	return encrypter, decrypter
}

type keyProvider struct {
	key  *hdkeychain.ExtendedKey
	path string
}

func (k keyProvider) WithPath(path string) (*btcec.PrivateKey, error) {
	indexes := hdpath.MustParse(path).IndexesFrom(hdpath.MustParse(k.path))

	var err error
	key := k.key
	for _, index := range indexes {
		var modifier uint32 = 0
		if index.Hardened {
			modifier = hdkeychain.HardenedKeyStart
		}
		key, err = key.Derive(index.Index | modifier)
		if err != nil {
			return nil, err
		}
	}

	return key.ECPrivKey()
}

func (k keyProvider) WithPathUsingHardenedBug(path string) (*btcec.PrivateKey, error) {
	indexes := hdpath.MustParse(path).IndexesFrom(hdpath.MustParse(k.path))

	var err error
	key := k.key
	for _, index := range indexes {
		var modifier uint32 = 0
		if index.Hardened {
			modifier = hdkeychain.HardenedKeyStart
		}
		//lint:ignore SA1019 using deprecated method for backwards compat with the bug
		key, err = key.DeriveNonStandard(index.Index | modifier)
		if err != nil {
			return nil, err
		}
	}

	return key.ECPrivKey()
}

func (k keyProvider) Path() string {
	return k.path
}

func TestPublicKeyEncryptionV1(t *testing.T) {
	const (
		priv         = "xprv9s21ZrQH143K2DAjx7FiAo2GQAQ5g7GrPYkTB2RaCd2Ei5ZH7f9cbREHiZTCc1FPn9HKuviUHk8sf5cW3dhYjz6W6XPjXNHu5mLpT5oRH1j"
		ciphertext   = "AMWm2L3YjA7myBTQQgiZi9F5g1NzaaupkPq1y7csUkf7WLXwnPYjkmy5KjVkyTKjaSXPwjx2zmX9Augzwwh89AsWYTv7KfJTXTj3Lx2mNZgmxJ7eezaJyRHv4koQaEmRykSoVE4esjWK779Sac28kCstkqDMPDYeNud5H4ApetF4BvhvPJyMaVn4RHYSAGzBzMcBV7WxYoRveKHqU9LbAfhCndPtRSVZyTVXY8iE3EvQJFeZVyYdovPK67aHsXWRdi8QCinMQSG21TMmhs7GQAh6iB26X2ABcVFJRGeEKE2coAsfuAHzcAMZ3CdzGgVAm7rrQw13W3XpxwwjWVatH9Jm9H4TrnnnLxRCsBoSKDvA1hmH8a2UG9iMxkhsBVMPzNRMy4Bg4MHk8WyRo3bwCLSVJUFFEciQ3mUneHprezzbVZio"
		plaintextHex = "ca4dabb05a47d3ab306c1fad895d97b06dc30564191e610f9b254b1a1d0a536b6eca2b83d0d17d67aaad2a958fe6a6557ad5b26f44e12e7662f47a4e4fd6f482b68a83cd140ad4ded43b90a2c2cf349af84d828b1f961901616b4c4cb01f761bd277ad0d3d90506065aef76b930a962fcb90f2f009898c0d55cd07b5e01c355a9067937185fa9237d03e5ed4243e1bf0f8a959c72a83cbb1729b679cbd660052dd2dd3096b0f19e9275ac459b94d02a95642"
	)

	privKey, _ := hdkeychain.NewKeyFromString(priv)

	encrypter, decrypter := cryptersFromKey(privKey)
	plaintext, _ := hex.DecodeString(plaintextHex)

	decrypted, err := decrypter.Decrypt(ciphertext)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(plaintext, decrypted) {
		t.Fatalf("decrypted payload differed from original\ndecrypted %v\noriginal %v",
			hex.EncodeToString(decrypted),
			hex.EncodeToString(plaintext))
	}

	_, err = encrypter.Encrypt(decrypted)
	if err != nil {
		t.Fatal(err)
	}

	// Since we use a random key every time, comparing cipher texts makes no sense
}

func TestPublicKeyDecryptV1(t *testing.T) {

	const (
		privHex = "xprv9s21ZrQH143K36uECEJcmTnxSXfHjT9jdb7FpMoUJpENDxeRgpscDF3g2w4ySH6G9uVsGKK7e6WgGp7Vc9VVnwC2oWdrr7a3taWiKW8jKnD"
		path    = "m"
		pathLen = 1
	)
	payload := []byte("Asado Viernes")

	privKey, _ := hdkeychain.NewKeyFromString(privHex)
	encrypter, decrypter := cryptersFromKey(privKey)
	encrypted, _ := encrypter.Encrypt(payload)

	decrypted, err := decrypter.Decrypt(encrypted)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(payload, decrypted) {
		t.Fatalf("decrypted payload differed from original\ndecrypted %v\noriginal %v",
			string(decrypted),
			string(payload))
	}

	alterAndCheck := func(msg string, alter func(data []byte)) {
		t.Run(msg, func(t *testing.T) {
			encryptedData := base58.Decode(encrypted)
			alter(encryptedData)

			_, err = decrypter.Decrypt(base58.Encode(encryptedData))
			if err == nil {
				t.Fatalf("Got nil error for altered payload: %v", msg)
			}

			t.Logf("Got error for altered payload %v: %v", msg, err)
		})
	}

	alterAndCheck("big nonce size", func(data []byte) {
		// Override the nonce size
		data[1+serializedPublicKeyLength+2+pathLen] = 255
	})

	alterAndCheck("bigger nonce size", func(data []byte) {
		// Override the nonce size
		data[1+serializedPublicKeyLength+2+pathLen+1] = 14
	})

	alterAndCheck("smaller nonce size", func(data []byte) {
		// Override the nonce size
		data[1+serializedPublicKeyLength+2+pathLen+1] = 1
	})

	alterAndCheck("big derivation path len", func(data []byte) {
		// Override derivation path length
		data[1+serializedPublicKeyLength] = 255
	})

	alterAndCheck("bigger derivation path len", func(data []byte) {
		// Override derivation path length
		data[1+serializedPublicKeyLength+1] = 4
	})

	alterAndCheck("smaller derivation path len", func(data []byte) {
		// Override derivation path length
		data[1+serializedPublicKeyLength+1] = 0
	})

	alterAndCheck("nonce", func(data []byte) {
		// Invert last byte of the nonce
		data[1+serializedPublicKeyLength+2+pathLen+2+11] =
			^data[1+serializedPublicKeyLength+2+pathLen+2+11]
	})

	alterAndCheck("tamper ciphertext", func(data []byte) {
		// Invert last byte of the ciphertext
		data[len(data)-1] = ^data[len(data)-1]
	})

	t.Run("tamperCiphertextWithAEAD", func(t *testing.T) {
		data := base58.Decode(encrypted)

		additionalData := data[0 : 1+serializedPublicKeyLength+2+pathLen+2]
		nonce := data[len(data)-12:]
		encryptionKey, _ := hdkeychain.NewKeyFromString(privHex)
		ecEncryptionKey, _ := encryptionKey.ECPrivKey()
		secret, _ := RecoverSharedEncryptionSecretForAES(ecEncryptionKey, data[1:serializedPublicKeyLength+1])

		block, _ := aes.NewCipher(secret)
		gcm, _ := cipher.NewGCM(block)

		randomKeyBytes := make([]byte, 32)
		_, _ = rand.Read(randomKeyBytes)
		fakeHdPrivKey, _ := hdkeychain.NewMaster(randomKeyBytes, &chaincfg.MainNetParams)

		fakePayload := []byte(strings.ToLower(string(payload)))
		fakePrivKey, _ := fakeHdPrivKey.ECPrivKey()

		hash := sha256.Sum256(fakePayload)
		fakeSig, _ := ecdsa.SignCompact(fakePrivKey, hash[:], false)

		plaintext := bytes.NewBuffer(nil)
		addVariableBytes(plaintext, fakeSig)
		plaintext.Write(fakePayload)

		ciphertext := gcm.Seal(nil, nonce, plaintext.Bytes(), additionalData)

		offset := len(additionalData)
		for _, b := range ciphertext {
			data[offset] = b
			offset++
		}

		for _, b := range nonce {
			data[offset] = b
			offset++
		}

		_, err = decrypter.Decrypt(base58.Encode(data))
		if err == nil {
			t.Errorf("Got nil error for altered payload: tamper chiphertex recalculating AEAD")
		}

		t.Logf("Got error for altered payload tamper chiphertex recalculating AEAD: %v", err)
	})
}
