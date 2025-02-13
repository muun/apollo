package encryption

import (
	"crypto/sha256"
	"fmt"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet/aescbc"
)

// GenerateSharedEncryptionSecret performs a ECDH with pubKey
// Deprecated: this function is unsafe and GenerateSharedEncryptionSecretForAES should be used
func GenerateSharedEncryptionSecret(pubKey *btcec.PublicKey) (*btcec.PublicKey, []byte, error) {
	privEph, err := btcec.NewPrivateKey()
	if err != nil {
		return nil, nil, fmt.Errorf("GenerateSharedEncryptionSecretForAES: failed to generate key: %w", err)
	}

	sharedSecret, _ := btcec.S256().ScalarMult(pubKey.X(), pubKey.Y(), privEph.ToECDSA().D.Bytes())

	return privEph.PubKey(), paddedSerializeBigInt(aescbc.KeySize, sharedSecret), nil
}

// RecoverSharedEncryptionSecret performs an ECDH to recover the encryption secret meant for privKey from rawPubEph
// Deprecated: this function is unsafe and RecoverSharedEncryptionSecretForAES should be used
func RecoverSharedEncryptionSecret(privKey *btcec.PrivateKey, rawPubEph []byte) ([]byte, error) {
	pubEph, err := btcec.ParsePubKey(rawPubEph)
	if err != nil {
		return nil, fmt.Errorf("RecoverSharedEncryptionSecretForAES: failed to parse pub eph: %w", err)
	}

	sharedSecret, _ := btcec.S256().ScalarMult(pubEph.X(), pubEph.Y(), privKey.ToECDSA().D.Bytes())
	return paddedSerializeBigInt(aescbc.KeySize, sharedSecret), nil
}

// GenerateSharedEncryptionSecret performs a ECDH with pubKey and produces a secret usable with AES
func GenerateSharedEncryptionSecretForAES(pubKey *btcec.PublicKey) (*btcec.PublicKey, []byte, error) {
	privEph, sharedSecret, err := GenerateSharedEncryptionSecret(pubKey)
	if err != nil {
		return nil, nil, err
	}

	hash := sha256.Sum256(sharedSecret)
	return privEph, hash[:], nil
}

func RecoverSharedEncryptionSecretForAES(privKey *btcec.PrivateKey, rawPubEph []byte) ([]byte, error) {
	sharedSecret, err := RecoverSharedEncryptionSecret(privKey, rawPubEph)
	if err != nil {
		return nil, err
	}

	hash := sha256.Sum256(sharedSecret)
	return hash[:], nil
}
