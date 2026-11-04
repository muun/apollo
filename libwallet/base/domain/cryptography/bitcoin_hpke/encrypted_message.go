package bitcoin_hpke

import (
	"fmt"
	"io"
	"log/slog"
	"slices"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/go-errors/errors"
	"golang.org/x/crypto/chacha20poly1305"

	"github.com/muun/libwallet/platform/secret"
)

// EncryptedMessage represents a message encrypted with Bitcoin Hpke
type EncryptedMessage struct {
	encapsulatedKey *btcec.PublicKey
	ciphertext      secret.Bytes[[]byte]
}

func ParseEncryptedMessage(serializedEncryptedMessage []byte) (*EncryptedMessage, error) {

	if len(serializedEncryptedMessage) < SerializedEncryptedMessageLengthInBytes(0) {
		return nil, errors.New("serialized message too short")
	}

	encapsulatedKey, err := btcec.ParsePubKey(
		serializedEncryptedMessage[:encapsulatedKeyLengthInBytes],
	)
	if err != nil {
		return nil, err
	}

	return &EncryptedMessage{
		encapsulatedKey: encapsulatedKey,
		ciphertext:      secret.NewBytes(serializedEncryptedMessage[encapsulatedKeyLengthInBytes:]),
	}, nil
}

func (encryptedMessage EncryptedMessage) Serialize() []byte {
	return slices.Concat(
		encryptedMessage.encapsulatedKey.SerializeUncompressed(),
		encryptedMessage.ciphertext.Reveal(),
	)
}

func (encryptedMessage EncryptedMessage) GetEncapsulatedKey() *btcec.PublicKey {
	return encryptedMessage.encapsulatedKey
}

func (encryptedMessage EncryptedMessage) GetCiphertext() []byte {
	return encryptedMessage.ciphertext.Reveal()
}

func (encryptedMessage EncryptedMessage) PlaintextLengthInBytes() int {
	return encryptedMessage.ciphertext.Len() - chacha20poly1305.Overhead
}

// Format prints LogValue for every verb.
func (encryptedMessage EncryptedMessage) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, encryptedMessage.LogValue().String())
}

// LogValue returns the ciphertext's length, without the ciphertext.
func (encryptedMessage EncryptedMessage) LogValue() slog.Value {
	return slog.GroupValue(slog.Int("ciphertext_length", encryptedMessage.ciphertext.Len()))
}

// This is a companion to ParseEncryptedMessage that allows to know the length in bytes of an
// encrypted message.
func SerializedEncryptedMessageLengthInBytes(plaintextLengthInBytes int) int {
	return encapsulatedKeyLengthInBytes + plaintextLengthInBytes + authenticationTagLengthInBytes
}
