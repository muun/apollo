package libwallet

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"encoding/binary"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcutil/base58"
	"github.com/pkg/errors"
)

type ChallengePublicKey struct {
	pubKey *btcec.PublicKey
}

func NewChallengePublicKeyFromSerialized(serializedKey []byte) (*ChallengePublicKey, error) {

	pubKey, err := btcec.ParsePubKey(serializedKey, btcec.S256())
	if err != nil {
		return nil, err
	}

	return &ChallengePublicKey{pubKey}, nil
}

func (k *ChallengePublicKey) EncryptKey(privKey *HDPrivateKey, recoveryCodeSalt []byte, birthday int) (string, error) {

	const (
		chainCodeStart = 13
		chainCodeLength = 32
		privKeyStart = 46
		privKeyLength = 32
	)

	rawHDKey := base58.Decode(privKey.String())
	plaintext := make([]byte, 0)
	plaintext = append(plaintext, rawHDKey[privKeyStart:privKeyStart+privKeyLength]...)
	plaintext = append(plaintext, rawHDKey[chainCodeStart:chainCodeStart+chainCodeLength]...)
	if len(plaintext) != 64 {
		return "", errors.Errorf("failed to encrypt key: expected payload of 64 bytes, found %v", len(plaintext))
	}

	privEph, err := btcec.NewPrivateKey(btcec.S256())
	if err != nil {
		return "", errors.Wrapf(err, "failed to encrypt key")
	}

	sharedSecret, _ := k.pubKey.ScalarMult(k.pubKey.X, k.pubKey.Y, privEph.D.Bytes())
	serializedPubkey := privEph.PubKey().SerializeCompressed()
	iv := serializedPubkey[len(serializedPubkey)-aes.BlockSize:]

	block, err := aes.NewCipher(sharedSecret.Bytes())
	if err != nil {
		return "", err
	}

	ciphertext := make([]byte, len(plaintext))

	mode := cipher.NewCBCEncrypter(block, iv)
	mode.CryptBlocks(ciphertext, plaintext)

	birthdayBytes := make([]byte, 2)
	binary.BigEndian.PutUint16(birthdayBytes, uint16(birthday))

	result := make([]byte, 0, 1+2+33+len(ciphertext)+len(recoveryCodeSalt))
	buf := bytes.NewBuffer(result)
	buf.WriteByte(2)
	buf.Write(birthdayBytes)
	buf.Write(privEph.PubKey().SerializeCompressed())
	buf.Write(ciphertext)
	buf.Write(recoveryCodeSalt)

	return base58.Encode(buf.Bytes()), nil
}