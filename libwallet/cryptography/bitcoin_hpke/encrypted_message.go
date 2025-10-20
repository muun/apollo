package bitcoin_hpke

import (
	"errors"
	"github.com/btcsuite/btcd/btcec/v2"
	"golang.org/x/crypto/chacha20poly1305"
	"slices"
)

// EncryptedMessage represents a message encrypted with Bitcoin Hpke
type EncryptedMessage struct {
	encapsulatedKey *btcec.PublicKey
	ciphertext      []byte
}

func ParseEncryptedMessage(serializedEncryptedMessage []byte) (*EncryptedMessage, error) {

	if len(serializedEncryptedMessage) < SerializedEncryptedMessageLengthInBytes(0) {
		return nil, errors.New("serialized message too short")
	}

	encapsulatedKey, err := btcec.ParsePubKey(serializedEncryptedMessage[:encapsulatedKeyLengthInBytes])
	if err != nil {
		return nil, err
	}

	return &EncryptedMessage{encapsulatedKey, serializedEncryptedMessage[encapsulatedKeyLengthInBytes:]}, nil
}

func (encryptedMessage EncryptedMessage) Serialize() []byte {
	return slices.Concat(encryptedMessage.encapsulatedKey.SerializeUncompressed(), encryptedMessage.ciphertext)
}

func (encryptedMessage EncryptedMessage) GetEncapsulatedKey() *btcec.PublicKey {
	return encryptedMessage.encapsulatedKey
}

func (encryptedMessage EncryptedMessage) GetCiphertext() []byte {
	return encryptedMessage.ciphertext
}

func (encryptedMessage EncryptedMessage) PlaintextLengthInBytes() int {
	return len(encryptedMessage.ciphertext) - chacha20poly1305.Overhead
}

// This is a companion to ParseEncryptedMessage that allows to know the length in bytes of an encrypted message.
func SerializedEncryptedMessageLengthInBytes(plaintextLengthInBytes int) int {
	return encapsulatedKeyLengthInBytes + plaintextLengthInBytes + authenticationTagLengthInBytes
}
