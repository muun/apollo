package libwallet

import (
	"bytes"
	"encoding/binary"
	"fmt"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcutil/base58"
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
		chainCodeStart  = 13
		chainCodeLength = 32
		privKeyStart    = 46
		privKeyLength   = 32
	)

	rawHDKey := base58.Decode(privKey.String())
	plaintext := make([]byte, 0)
	plaintext = append(plaintext, rawHDKey[privKeyStart:privKeyStart+privKeyLength]...)
	plaintext = append(plaintext, rawHDKey[chainCodeStart:chainCodeStart+chainCodeLength]...)
	if len(plaintext) != 64 {
		return "", fmt.Errorf("failed to encrypt key: expected payload of 64 bytes, found %v", len(plaintext))
	}

	pubEph, ciphertext, err := encryptWithPubKey(k.pubKey, plaintext)
	if err != nil {
		return "", err
	}

	birthdayBytes := make([]byte, 2)
	binary.BigEndian.PutUint16(birthdayBytes, uint16(birthday))

	if len(recoveryCodeSalt) == 0 {
		// Fill the salt with zeros to maintain the encrypted keys format
		recoveryCodeSalt = make([]byte, 8)
	}

	result := make([]byte, 0, 1+2+serializedPublicKeyLength+len(ciphertext)+len(recoveryCodeSalt))
	buf := bytes.NewBuffer(result)
	buf.WriteByte(2)
	buf.Write(birthdayBytes)
	buf.Write(pubEph.SerializeCompressed())
	buf.Write(ciphertext)
	buf.Write(recoveryCodeSalt)

	return base58.Encode(buf.Bytes()), nil
}
