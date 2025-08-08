package encrypted_cosigning_key

import (
	"encoding/hex"
	"github.com/btcsuite/btcd/btcec/v2"
	"testing"
)

func TestPaddingEncryptionAndDecryption(t *testing.T) {

	receiverPrivateKeyBytes, err := hex.DecodeString("30bbb2cdefb4bd92d3ba68adc13032d4e9e9be28496e871fe39e0970156c5ea8")
	if err != nil {
		t.Error(err)
	}
	receiverPrivateKey, receiverPublicKey := btcec.PrivKeyFromBytes(receiverPrivateKeyBytes)

	secretMessage := "abcdef009999999900000000ffffffffabcdef009999999900000000ffffffff"
	secretMessageBytes, err := hex.DecodeString(secretMessage)
	if err != nil {
		t.Error(err)
	}

	ephemeralPrivateKeyBytes, err := hex.DecodeString("8652d2c55b2cf2217a7912fa0d43a1f15c93ecc98495fd44b887cf7232b16acd")
	if err != nil {
		t.Error(err)
	}
	ephemeralPrivateKey, ephemeralPublicKey := btcec.PrivKeyFromBytes(ephemeralPrivateKeyBytes)

	encryptedMessage, err := paddingEncrypt(secretMessageBytes, receiverPublicKey, ephemeralPrivateKey)
	if err != nil {
		t.Error(err)
	}

	expectedEncryptedMessage := "81ad50df3d5ca9f7a40d4cf97b22e44b2e27aace183be4e020ccd89ad5552486"
	if hex.EncodeToString(encryptedMessage) != expectedEncryptedMessage {
		t.Error("encrypted message is not the expected one")
	}

	decryptedMessage, err := paddingDecrypt(encryptedMessage, receiverPrivateKey, ephemeralPublicKey)
	if err != nil {
		t.Error(err)
	}

	if hex.EncodeToString(decryptedMessage) != secretMessage {
		t.Error("decrypted message is not the original one")
	}
}

func TestPaddingDecrypt(t *testing.T) {
	receiverPrivateKeyBytes, err := hex.DecodeString("30bbb2cdefb4bd92d3ba68adc13032d4e9e9be28496e871fe39e0970156c5ea8")
	if err != nil {
		t.Error(err)
	}
	receiverPrivateKey, _ := btcec.PrivKeyFromBytes(receiverPrivateKeyBytes)

	ephemeralPublicKeyBytes, err := hex.DecodeString("032afde2011f0a454068d4cbf1c14bd1aec1e25035fb523471efa380f1b1cec450")
	if err != nil {
		t.Error(err)
	}
	ephemeralPublicKey, err := btcec.ParsePubKey(ephemeralPublicKeyBytes)
	if err != nil {
		t.Error(err)
	}

	encryptedMessage, err := hex.DecodeString("976b579ddb448dd9964914d4a54d6360cc035c9d43ae2ca03b4a10b1bf69899d")
	if err != nil {
		t.Error(err)
	}

	decryptedMessage, err := paddingDecrypt(encryptedMessage, receiverPrivateKey, ephemeralPublicKey)
	if err != nil {
		t.Error(err)
	}

	expectedSecretMessage := "abcdef009999999900000000ffffffffabcdef009999999900000000ffffffff"

	if hex.EncodeToString(decryptedMessage) != expectedSecretMessage {
		t.Error("decrypted message is not the original one")
	}
}
