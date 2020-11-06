package libwallet

import (
	"fmt"

	"github.com/muun/libwallet/keycrypt"
)

type DecryptedKey struct {
	Key  *HDPrivateKey
	Path string
}

// KeyEncrypt encrypts an HD priv key using a user-provided secret into a string
// We use SCrypt256 for key derivation and AES-CBC-PKCS7 for encryption.
// The returned string has information about version, derivation path, scrypt and AES parameters.
func KeyEncrypt(privKey *HDPrivateKey, passphrase string) (string, error) {
	ciphertext, err := keycrypt.Encrypt(&privKey.key, privKey.Path, passphrase)
	if err != nil {
		return "", fmt.Errorf("KeyEncrypt: failed to encrypt: %w", err)
	}
	return ciphertext, nil
}

// KeyDecrypt decrypts a key encrypted with KeyEncrypt
func KeyDecrypt(value, passphrase string, network *Network) (*DecryptedKey, error) {
	key, path, err := keycrypt.Decrypt(value, passphrase)
	if err != nil {
		return nil, fmt.Errorf("KeyDecrypt: failed to decrypt: %w", err)
	}
	privateKey := &HDPrivateKey{key: *key, Network: network, Path: path}

	return &DecryptedKey{Key: privateKey, Path: path}, nil
}
