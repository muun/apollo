package libwallet

import (
	"bytes"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcutil/base58"
)

const (
	// EncodedKeyLength is the size of a modern encoded key, as exported by the clients.
	EncodedKeyLength = 147

	// EncodedKeyLengthLegacy is the size of a legacy key, when salt resided only in the 2nd key.
	EncodedKeyLengthLegacy = 136
)

type ChallengePrivateKey struct {
	key *btcec.PrivateKey
}

type encryptedPrivateKey struct {
	Version      uint8
	Birthday     uint16
	EphPublicKey []byte // 33-byte compressed public-key
	CipherText   []byte // 64-byte encrypted text
	Salt         []byte // (optional) 8-byte salt
}

// EncryptedPrivateKeyInfo is a Gomobile-compatible version of EncryptedPrivateKey using hex-encoding.
type EncryptedPrivateKeyInfo struct {
	Version      int
	Birthday     int
	EphPublicKey string
	CipherText   string
	Salt         string
}

type DecryptedPrivateKey struct {
	Key      *HDPrivateKey
	Birthday int
}

func NewChallengePrivateKey(input, salt []byte) *ChallengePrivateKey {

	key := Scrypt256(input, salt)

	// 2nd return value is the pub key which we don't need right now
	priv, _ := btcec.PrivKeyFromBytes(btcec.S256(), key)

	return &ChallengePrivateKey{key: priv}
}

// SignSha computes the SHA-256 digest of the given payload and signs it.
func (k *ChallengePrivateKey) SignSha(payload []byte) ([]byte, error) {

	hash := sha256.Sum256(payload)
	sig, err := k.key.Sign(hash[:])

	if err != nil {
		return nil, fmt.Errorf("failed to sign payload: %w", err)
	}

	return sig.Serialize(), nil
}

func (k *ChallengePrivateKey) PubKeyHex() string {
	rawKey := k.key.PubKey().SerializeCompressed()
	return hex.EncodeToString(rawKey)
}

func (k *ChallengePrivateKey) PubKey() *ChallengePublicKey {
	return &ChallengePublicKey{pubKey: k.key.PubKey()}
}

func (k *ChallengePrivateKey) DecryptRawKey(encryptedKey string, network *Network) (*DecryptedPrivateKey, error) {
	decoded, err := DecodeEncryptedPrivateKey(encryptedKey)
	if err != nil {
		return nil, err
	}

	return k.DecryptKey(decoded, network)
}

func (k *ChallengePrivateKey) DecryptKey(decodedInfo *EncryptedPrivateKeyInfo, network *Network) (*DecryptedPrivateKey, error) {
	decoded, err := unwrapEncryptedPrivateKey(decodedInfo)
	if err != nil {
		return nil, err
	}

	plaintext, err := decryptWithPrivKey(k.key, decoded.EphPublicKey, decoded.CipherText)
	if err != nil {
		return nil, err
	}

	rawPrivKey := plaintext[0:32]
	rawChainCode := plaintext[32:]

	privKey, err := NewHDPrivateKeyFromBytes(rawPrivKey, rawChainCode, network)
	if err != nil {
		return nil, fmt.Errorf("decrypting key: failed to parse key: %w", err)
	}

	return &DecryptedPrivateKey{
		privKey,
		int(decoded.Birthday),
	}, nil
}

func DecodeEncryptedPrivateKey(encodedKey string) (*EncryptedPrivateKeyInfo, error) {
	reader := bytes.NewReader(base58.Decode(encodedKey))
	version, err := reader.ReadByte()
	if err != nil {
		return nil, fmt.Errorf("decrypting key: %w", err)
	}
	if version != 2 {
		return nil, fmt.Errorf("decrypting key: found key version %v, expected 2", version)
	}

	birthdayBytes := make([]byte, 2)
	rawPubEph := make([]byte, serializedPublicKeyLength)
	ciphertext := make([]byte, 64)
	recoveryCodeSalt := make([]byte, 8)

	n, err := reader.Read(birthdayBytes)
	if err != nil || n != 2 {
		return nil, errors.New("decrypting key: failed to read birthday")
	}
	birthday := binary.BigEndian.Uint16(birthdayBytes)

	n, err = reader.Read(rawPubEph)
	if err != nil || n != serializedPublicKeyLength {
		return nil, errors.New("decrypting key: failed to read pubeph")
	}

	n, err = reader.Read(ciphertext)
	if err != nil || n != 64 {
		return nil, errors.New("decrypting key: failed to read ciphertext")
	}

	// NOTE:
	// The very, very old format for encrypted keys didn't contain the encryption salt in the first
	// of the two keys. This is a valid scenario, and a zero-filled salt can be returned.
	if shouldHaveSalt(encodedKey) {
		n, err = reader.Read(recoveryCodeSalt)

		if err != nil || n != 8 {
			return nil, errors.New("decrypting key: failed to read recoveryCodeSalt")
		}
	}

	result := &EncryptedPrivateKeyInfo{
		Version:      int(version),
		Birthday:     int(birthday),
		EphPublicKey: hex.EncodeToString(rawPubEph),
		CipherText:   hex.EncodeToString(ciphertext),
		Salt:         hex.EncodeToString(recoveryCodeSalt),
	}

	return result, nil
}

func shouldHaveSalt(encodedKey string) bool {
	return len(encodedKey) > EncodedKeyLengthLegacy // not military-grade logic, but works for now
}

func unwrapEncryptedPrivateKey(info *EncryptedPrivateKeyInfo) (*encryptedPrivateKey, error) {
	ephPublicKey, err := hex.DecodeString(info.EphPublicKey)
	if err != nil {
		return nil, err
	}

	cipherText, err := hex.DecodeString(info.CipherText)
	if err != nil {
		return nil, err
	}

	salt, err := hex.DecodeString(info.Salt)
	if err != nil {
		return nil, err
	}

	unwrapped := &encryptedPrivateKey{
		Version:      uint8(info.Version),
		Birthday:     uint16(info.Birthday),
		EphPublicKey: ephPublicKey,
		CipherText:   cipherText,
		Salt:         salt,
	}

	return unwrapped, nil
}
