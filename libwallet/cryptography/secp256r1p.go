package cryptography

import (
	"crypto/ecdh"
	"crypto/rand"
	"fmt"
)

type KeyPair struct {
	PrivateKey []byte
	PublicKey  []byte
}

// GenerateSecp256r1PrivateKey generates a random SECP256R1 private key
func GenerateSecp256r1PrivateKey() ([]byte, error) {
	privKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		return nil, err
	}

	return privKey.Bytes(), nil
}

// GenerateSecp256r1PublicKey generates the public key from a private key
func GenerateSecp256r1PublicKey(privateKey []byte) ([]byte, error) {
	privKey, err := ecdh.P256().NewPrivateKey(privateKey)
	if err != nil {
		// This should never happen if privateKey was generated correctly
		return nil, err
	}

	return privKey.PublicKey().Bytes(), nil
}

func GenerateSecp256r1PKeyPair() (*KeyPair, error) {

	privateKey, err := GenerateSecp256r1PrivateKey()
	if err != nil {
		return nil, fmt.Errorf("failed to generate private key: %v", err)
	}

	publicKey, err := GenerateSecp256r1PublicKey(privateKey)
	if err != nil {
		return nil, fmt.Errorf("failed to generate public key: %v", err)
	}

	return &KeyPair{PrivateKey: privateKey, PublicKey: publicKey}, nil
}

func ValidateSecp256r1PublicKey(publicKey []byte) error {
	if len(publicKey) != 65 {
		return fmt.Errorf(
			"invalid public key length: %d (expected %d)",
			len(publicKey),
			65,
		)
	}

	if publicKey[0] != 0x04 {
		return fmt.Errorf(
			"invalid public key format: expected 0x04 prefix, got 0x%02X",
			publicKey[0],
		)
	}

	_, err := ecdh.P256().NewPublicKey(publicKey)
	if err != nil {
		return fmt.Errorf("invalid public key: not a valid point on curve secp256r1")
	}

	return nil
}

// ECDH performs Elliptic-curve Diffie–Hellman key agreement.
// Returns only the x-coordinate of the shared point.
func ECDH(privateKey, publicKey []byte) ([]byte, error) {

	priv, err := ecdh.P256().NewPrivateKey(privateKey)
	if err != nil {
		return nil, fmt.Errorf("invalid private key: %w", err)
	}

	pub, err := ecdh.P256().NewPublicKey(publicKey)
	if err != nil {
		return nil, fmt.Errorf("invalid public key: %w", err)
	}

	secret, err := priv.ECDH(pub)
	if err != nil {
		return nil, fmt.Errorf("ecdh error: %w", err)
	}

	return secret, nil
}
