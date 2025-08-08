package nfc

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/sha1"
	"encoding/hex"
	"fmt"
	"github.com/btcsuite/btcd/btcec/v2"
)

type muunCardSecureChannel struct {
	sharedSecret []byte
	derivedKey   []byte
}

func newSecureChannel(
	devicePrivateKey *btcec.PrivateKey,
	cardPubKeyBytes []byte,
) (*muunCardSecureChannel, error) {

	sharedSecret, err := computeSharedSecret(devicePrivateKey, cardPubKeyBytes)
	if err != nil {
		return nil, fmt.Errorf("error generating shared secret: %w", err)
	}

	derivedKey := computeDerivedKey(sharedSecret)

	return &muunCardSecureChannel{
		sharedSecret: sharedSecret,
		derivedKey:   derivedKey,
	}, nil
}

func (s *muunCardSecureChannel) encryptMessage(apdu []byte) ([]byte, error) {
	cla := claEdge

	ins := apdu[iso7816OffsetIns]
	p1 := apdu[iso7816OffsetP1]
	p2 := apdu[iso7816OffsetP2]
	plainData := apdu[iso7816OffsetCData:]

	// Encrypt the payload with AES-CBC and no padding using a zero IV
	zeroIV := make([]byte, aes.BlockSize) // Zero IV
	key := s.derivedKey[:16]              // Use first 16 bytes for AES
	cipherText, err := aesEncrypt(key, zeroIV, plainData)
	if err != nil {
		return nil, err
	}

	// Generate MAC for encrypted data
	mac := computeHmacSha1(s.derivedKey, cipherText) // Use full derived key for HMAC

	// Build encrypted apdu
	encrypted := append(cipherText, mac...)
	msg := newAPDU(byte(cla), ins, p1, p2, encrypted)

	return msg.serialize(), nil
}

func (s *muunCardSecureChannel) verifyResponseMAC(response []byte) ([]byte, error) {
	if len(response) < hmacSha1SizeInBytes { // SHA1-HMAC is 20 bytes
		hexResponse := hex.EncodeToString(response)
		return nil, fmt.Errorf("response too short to contain MAC: %s", hexResponse)
	}

	dataLen := len(response) - hmacSha1SizeInBytes
	data := response[:dataLen]
	responseMAC := response[dataLen:]

	// Generate MAC for verification
	// Note: we're currently sending the derived key (which is an hmac of the shared secret).
	// TODO: this is probably wrong and we'll fix it but its what the current muuncard impl does
	expectedMAC := computeDerivedKey(s.sharedSecret)

	if !hmac.Equal(responseMAC, expectedMAC) {
		return nil, fmt.Errorf(
			"response MAC verification failed, wanted %s, got %s",
			hex.EncodeToString(expectedMAC),
			hex.EncodeToString(responseMAC),
		)
	}

	return data, nil
}

func computeSharedSecret(
	devicePrivateKey *btcec.PrivateKey,
	cardPublicKeyBytes []byte,
) ([]byte, error) {

	cardPublicKey, err := btcec.ParsePubKey(cardPublicKeyBytes)
	if err != nil {
		return nil, fmt.Errorf("invalid card public key: %w", err)
	}

	return btcec.GenerateSharedSecret(devicePrivateKey, cardPublicKey), nil
}

func computeDerivedKey(sharedSecret []byte) []byte {
	// Computing derived key is just applying HMAC-SHA1 to the salt using the sharedSecret as key
	salt := []byte("deriv_key") // 9 bytes
	return computeHmacSha1(sharedSecret, salt)
}

// computeHmacSha1 computes HMAC-SHA160 for the input message using the provided key.
// Note: Sha1 is a 160-bit (20-byte) hash, meaning it outputs 20 bytes.
func computeHmacSha1(key []byte, message []byte) []byte {
	h := hmac.New(sha1.New, key)
	h.Write(message)
	return h.Sum(nil) // 20-byte HMAC-SHA1
}

// aesEncrypt encrypts a plaintext using the specified key and a IV.
// Note: this impl uses AES-128-CBC with no padding. Hence, key and IV must be 16 bytes (128 bits)
// and plaintext length must be a multiple of 16.
func aesEncrypt(key []byte, iv []byte, plaintext []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}

	ciphertext := make([]byte, len(plaintext))

	mode := cipher.NewCBCEncrypter(block, iv)
	mode.CryptBlocks(ciphertext, plaintext)

	return ciphertext, nil
}
