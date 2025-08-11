package encrypted_cosigning_key

import (
	"errors"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/encryption"
)

func paddingEncrypt(plaintextMessage []byte, receiverKey *btcec.PublicKey, senderKey *btcec.PrivateKey) ([]byte, error) {

	if len(plaintextMessage) != 32 {
		return nil, errors.New("plaintext message is not 32 bytes")
	}

	var plaintextMessageAsPrivateKey, _ = btcec.PrivKeyFromBytes(plaintextMessage)
	padding := computePadding(senderKey, receiverKey)

	return btcec.PrivKeyFromScalar(plaintextMessageAsPrivateKey.Key.Add(&padding.Key)).Serialize(), nil
}

func paddingDecrypt(encryptedMessage []byte, receiverKey *btcec.PrivateKey, senderKey *btcec.PublicKey) ([]byte, error) {

	if len(encryptedMessage) != 32 {
		return nil, errors.New("encrypted message is not 32 bytes")
	}

	var encryptedMessageAsPrivateKey, _ = btcec.PrivKeyFromBytes(encryptedMessage)
	padding := computePadding(receiverKey, senderKey)

	return btcec.PrivKeyFromScalar(encryptedMessageAsPrivateKey.Key.Add(padding.Key.Negate())).Serialize(), nil
}

func computePadding(privateKey *btcec.PrivateKey, publicKey *btcec.PublicKey) *btcec.PrivateKey {
	X, _ := btcec.S256().ScalarMult(publicKey.X(), publicKey.Y(), privateKey.Serialize())
	padding, _ := btcec.PrivKeyFromBytes(libwallet.SHA256(encryption.PaddedSerializeBigInt(32, X)))
	return padding
}
