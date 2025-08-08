package libwallet

import (
	"bytes"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil/base58"
)

type ChallengePublicKey struct {
	pubKey *btcec.PublicKey
}

func NewChallengePublicKeyFromSerialized(serializedKey []byte) (*ChallengePublicKey, error) {

	pubKey, err := btcec.ParsePubKey(serializedKey)
	if err != nil {
		return nil, err
	}

	return &ChallengePublicKey{pubKey}, nil
}

// EncryptKey
// We must check whether the MuunKey is serialized as V2 or V3 before serializing the UserKey.
// Since the MuunKey is stored client-side after login, users already logged in will always have
// the MuunKey serialized as V2 on their devices. If the user updates the app without re-logging in,
// a MuunKeyV2 will remain stored, and we must serialize the UserKey as V2 to maintain key consistency
// in the EmergencyKit.
func (k *ChallengePublicKey) EncryptKey(
	privKey *HDPrivateKey,
	recoveryCodeSalt []byte,
	birthday int,
	muunPrivateKey string,
) (string, error) {
	reader := bytes.NewReader(base58.Decode(muunPrivateKey))
	version, err := reader.ReadByte()
	if err != nil {
		return "", fmt.Errorf("decrypting key: %w", err)
	}

	switch version {
	case 2:
		return k.encryptKeyAsV2(privKey, recoveryCodeSalt, birthday)
	case 3:
		return k.encryptKeyAsV3(privKey, recoveryCodeSalt, birthday)
	default:
		return "", fmt.Errorf("unrecognized key version %v", version)
	}
}

func (k *ChallengePublicKey) encryptKeyAsV2(privKey *HDPrivateKey, recoveryCodeSalt []byte, birthday int) (string, error) {
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

func (k *ChallengePublicKey) encryptKeyAsV3(privKey *HDPrivateKey, recoveryCodeSalt []byte, birthday int) (string, error) {
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

	if len(recoveryCodeSalt) == 0 {
		// Fill the salt with zeros to maintain the encrypted keys format
		recoveryCodeSalt = make([]byte, 8)
	}

	result := make([]byte, 0, 1+serializedPublicKeyLength+len(ciphertext)+len(recoveryCodeSalt))
	buf := bytes.NewBuffer(result)
	buf.WriteByte(3)
	buf.Write(pubEph.SerializeCompressed())
	buf.Write(ciphertext)
	buf.Write(recoveryCodeSalt)

	return base58.Encode(buf.Bytes()), nil
}

func (k *ChallengePublicKey) GetChecksum() string {
	hash := SHA256(k.pubKey.SerializeCompressed())
	last8 := hash[len(hash)-8:]

	return hex.EncodeToString(last8)
}
