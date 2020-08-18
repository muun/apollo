package libwallet

import (
	"bytes"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcutil/base58"
	"github.com/pkg/errors"
)

type ChallengePrivateKey struct {
	key *btcec.PrivateKey
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
		return nil, errors.Wrapf(err, "failed to sign payload")
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

func (k *ChallengePrivateKey) DecryptKey(encryptedKey string, network *Network) (*DecryptedPrivateKey, error) {

	reader := bytes.NewReader(base58.Decode(encryptedKey))
	version, err := reader.ReadByte()
	if err != nil {
		return nil, errors.Wrapf(err, "decrypting key")
	}
	if version != 2 {
		return nil, errors.Errorf("decrypting key: found key version %v, expected 2", version)
	}

	birthdayBytes := make([]byte, 2)
	rawPubEph := make([]byte, serializedPublicKeyLength)
	ciphertext := make([]byte, 64)
	recoveryCodeSalt := make([]byte, 8)

	n, err := reader.Read(birthdayBytes)
	if err != nil || n != 2 {
		return nil, errors.Errorf("decrypting key: failed to read birthday")
	}
	birthday := binary.BigEndian.Uint16(birthdayBytes)

	n, err = reader.Read(rawPubEph)
	if err != nil || n != serializedPublicKeyLength {
		return nil, errors.Errorf("decrypting key: failed to read pubeph")
	}

	n, err = reader.Read(ciphertext)
	if err != nil || n != 64 {
		return nil, errors.Errorf("decrypting key: failed to read ciphertext")
	}

	n, err = reader.Read(recoveryCodeSalt)
	if err != nil || n != 8 {
		return nil, errors.Errorf("decrypting key: failed to read recoveryCodeSalt")
	}

	plaintext, err := decryptWithPrivKey(k.key, rawPubEph, ciphertext)
	if err != nil {
		return nil, err
	}

	rawPrivKey := plaintext[0:32]
	rawChainCode := plaintext[32:]

	privKey, err := NewHDPrivateKeyFromBytes(rawPrivKey, rawChainCode, network)
	if err != nil {
		return nil, errors.Wrapf(err, "decrypting key: failed to parse key")
	}

	return &DecryptedPrivateKey{
		privKey,
		int(birthday),
	}, nil
}
