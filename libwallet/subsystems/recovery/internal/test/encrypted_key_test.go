package test

import (
	"bytes"
	"encoding/base64"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/chaincfg"

	"github.com/muun/libwallet/base/domain/cryptography/bitcoin_hpke"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/legacy/recoverycode"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/model/encrypted_key_v3"
	recovery_model "github.com/muun/libwallet/subsystems/recovery/presentation/model"
)

func TestFinishCosignerKeyEncryption(t *testing.T) {

	recoveryCode := recoverycode.Generate()

	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}
	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	cosignerKey, err := hd_keys.NewHDPrivateKeyFromSeed(randomBytes(32), &chaincfg.MainNetParams)
	if err != nil {
		t.Fatal(err)
	}

	// simulate splitting the cosigner key and encrypting second half to RC

	firstHalfKey, err := btcec.NewPrivateKey()
	if err != nil {
		t.Fatal(err)
	}
	cosignerECPrivateKey, err := cosignerKey.ECPrivKey()
	if err != nil {
		t.Fatal(err)
	}

	secondHalfKeyBytes := new(
		btcec.ModNScalar,
	).Set(&firstHalfKey.Key).
		Negate().
		Add(&cosignerECPrivateKey.Key).
		Bytes()
	secondHalfKeyEncryptedToRecoveryCode, err := bitcoin_hpke.SingleShotEncrypt(
		secondHalfKeyBytes[:],
		recoveryCodePublicKey,
		[]byte(recovery_model.CosignerSecondHalfToRecoveryCode),
		[]byte(""),
	)
	if err != nil {
		t.Fatal(err)
	}

	// Now test FinishCosignerKeyEncryption
	encryptedCosignerKey, err := encrypted_key_v3.FinishCosignerKeyEncryption(
		recoveryCodePublicKey,
		firstHalfKey,
		cosignerKey.ChainCode(),
		secondHalfKeyEncryptedToRecoveryCode,
	)
	if err != nil {
		t.Fatal(err)
	}

	decryptedCosignerKey, err := encrypted_key_v3.DecryptExtendedKey(
		recoveryCodePrivateKey,
		encryptedCosignerKey,
		&chaincfg.MainNetParams,
	)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(decryptedCosignerKey.PublicKey().Raw(), cosignerKey.PublicKey().Raw()) {
		t.Fatal("decrypted public key does not match original public key")
	}

	if !bytes.Equal(decryptedCosignerKey.ChainCode(), cosignerKey.ChainCode()) {
		t.Fatal("decrypted chain code does not match original chain code")
	}
}

func TestEncryptUserKey(t *testing.T) {

	recoveryCode := recoverycode.Generate()
	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}
	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	userKey, err := hd_keys.NewHDPrivateKeyFromSeed(randomBytes(32), &chaincfg.MainNetParams)
	if err != nil {
		t.Fatal(err)
	}

	encryptedUserKey, err := encrypted_key_v3.EncryptUserKey(userKey, recoveryCodePublicKey)
	if err != nil {
		t.Fatal(err)
	}

	decryptedUserKey, err := encrypted_key_v3.DecryptExtendedKey(
		recoveryCodePrivateKey,
		encryptedUserKey,
		&chaincfg.MainNetParams,
	)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(decryptedUserKey.PublicKey().Raw(), userKey.PublicKey().Raw()) {
		t.Fatal("decrypted public key does not match original public key")
	}

	if !bytes.Equal(decryptedUserKey.ChainCode(), userKey.ChainCode()) {
		t.Fatal("decrypted chain code does not match original chain code")
	}
}

func TestDeserializationFailsDueToWrongVersion(t *testing.T) {
	bs := []byte{2, 0, 1}
	key := base64.StdEncoding.EncodeToString(bs)
	recoveryCodePrivateKey, _ := btcec.NewPrivateKey()
	_, err := encrypted_key_v3.DecryptExtendedKey(
		recoveryCodePrivateKey, key, &chaincfg.MainNetParams,
	)
	if err.Error() != "decrypting key: expected a v3 key, version byte indicates v2" {
		t.Fatal("Expected to fail due to unexpected cosigner key version")
	}
}
