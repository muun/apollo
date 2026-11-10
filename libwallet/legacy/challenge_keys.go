package libwallet

import (
	"bytes"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/platform/secret"
)

const (
	// EncodedKeyLength is the size of a modern encoded key, as exported by the clients.
	EncodedKeyLength = 147

	// EncodedKeyLengthLegacy is the size of a legacy key, when salt resided only in the 2nd key.
	EncodedKeyLengthLegacy = 136
)

const (
	KeySerializationVersion2 = 2
	KeySerializationVersion3 = 3
)

type ChallengePrivateKey struct {
	key *btcec.PrivateKey
}

type encryptedPrivateKey struct {
	Version      uint8
	Birthday     uint16
	EphPublicKey []byte               // 33-byte compressed public-key
	CipherText   secret.Bytes[[]byte] // 64-byte encrypted text
	Salt         secret.Bytes[[]byte] // (optional) 8-byte salt
}

// Format prints LogValue for every verb.
func (k encryptedPrivateKey) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, k.LogValue().String())
}

// LogValue returns the key's version and birthday, without the ciphertext and salt.
func (k encryptedPrivateKey) LogValue() slog.Value {
	return slog.GroupValue(
		slog.Int("version", int(k.Version)),
		slog.Int("birthday", int(k.Birthday)),
	)
}

// EncryptedPrivateKeyInfo is a Gomobile-compatible version of EncryptedPrivateKey using
// hex-encoding.
type EncryptedPrivateKeyInfo struct {
	Version      int
	Birthday     int
	EphPublicKey string
	CipherText   string
	Salt         string
}

// String returns the key's version and birthday, without the ciphertext and salt.
func (i EncryptedPrivateKeyInfo) String() string {
	return i.LogValue().String()
}

// Format prints String for every verb.
func (i EncryptedPrivateKeyInfo) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, i.String())
}

// LogValue returns the key's version and birthday, without the ciphertext and salt.
func (i EncryptedPrivateKeyInfo) LogValue() slog.Value {
	return slog.GroupValue(
		slog.Int("version", i.Version),
		slog.Int("birthday", i.Birthday),
	)
}

type DecryptedPrivateKey struct {
	Key      *HDPrivateKey
	Birthday int
}

func NewChallengePrivateKey(input, salt []byte) *ChallengePrivateKey {

	key := Scrypt256(input, salt)

	// 2nd return value is the pub key which we don't need right now
	priv, _ := btcec.PrivKeyFromBytes(key)

	return &ChallengePrivateKey{key: priv}
}

// SignSha computes the SHA-256 digest of the given payload and signs it.
func (k *ChallengePrivateKey) SignSha(payload []byte) ([]byte, error) {

	hash := sha256.Sum256(payload)
	sig := ecdsa.Sign(k.key, hash[:])

	return sig.Serialize(), nil
}

func (k *ChallengePrivateKey) PubKeyHex() string {
	rawKey := k.key.PubKey().SerializeCompressed()
	return hex.EncodeToString(rawKey)
}

func (k *ChallengePrivateKey) PubKey() *ChallengePublicKey {
	return &ChallengePublicKey{pubKey: k.key.PubKey()}
}

func (k *ChallengePrivateKey) DecryptRawKey(
	encryptedKey string,
	network *Network,
) (*DecryptedPrivateKey, error) {
	decoded, err := DecodeEncryptedPrivateKey(encryptedKey)
	if err != nil {
		return nil, err
	}

	return k.DecryptKey(decoded, network)
}

func (k *ChallengePrivateKey) DecryptKey(
	decodedInfo *EncryptedPrivateKeyInfo,
	network *Network,
) (*DecryptedPrivateKey, error) {
	decoded, err := unwrapEncryptedPrivateKey(decodedInfo)
	if err != nil {
		return nil, err
	}

	plaintext, err := decryptWithPrivKey(k.key, decoded.EphPublicKey, decoded.CipherText.Reveal())
	if err != nil {
		return nil, err
	}

	rawPrivKey := plaintext[0:32]
	rawChainCode := plaintext[32:]

	privKey, err := NewMasterHDPrivateKeyFromBytes(rawPrivKey, rawChainCode, network)
	if err != nil {
		return nil, errors.Errorf("decrypting key: failed to parse key: %w", err)
	}

	return &DecryptedPrivateKey{
		privKey,
		int(decoded.Birthday),
	}, nil
}

// Format prints LogValue for every verb.
// Format and LogValue take value receivers, unlike the other methods, so values print
// redacted too, not only pointers.
func (k ChallengePrivateKey) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, k.LogValue().String())
}

// LogValue returns the type name and secret.Placeholder.
func (k ChallengePrivateKey) LogValue() slog.Value {
	return slog.StringValue("ChallengePrivateKey(" + secret.Placeholder + ")")
}

func DecodeEncryptedPrivateKey(encodedKey string) (*EncryptedPrivateKeyInfo, error) {
	reader := bytes.NewReader(base58.Decode(encodedKey))
	version, err := reader.ReadByte()

	if err != nil {
		return nil, errors.Errorf("decrypting key: %w", err)
	}

	if version == KeySerializationVersion2 {
		return decodeEncryptedPrivateKeyV2(reader, encodedKey)
	}

	if version == KeySerializationVersion3 {
		return decodeEncryptedPrivateKeyV3(reader)
	}

	return nil, errors.Errorf("unrecognized key version %v", version)
}

func decodeEncryptedPrivateKeyV3(reader *bytes.Reader) (*EncryptedPrivateKeyInfo, error) {

	rawPubEph := make([]byte, serializedPublicKeyLength)
	ciphertext := make([]byte, 64)
	recoveryCodeSalt := make([]byte, 8)

	n, err := reader.Read(rawPubEph)
	if err != nil || n != serializedPublicKeyLength {
		return nil, errors.New("decrypting key: failed to read pubeph")
	}

	n, err = reader.Read(ciphertext)
	if err != nil || n != 64 {
		return nil, errors.New("decrypting key: failed to read ciphertext")
	}

	n, err = reader.Read(recoveryCodeSalt)

	if err != nil || n != 8 {
		return nil, errors.New("decrypting key: failed to read recoveryCodeSalt")
	}

	result := &EncryptedPrivateKeyInfo{
		Version:      KeySerializationVersion3,
		Birthday:     0, //Keys v3 doesn't contain birthday since it was useless.
		EphPublicKey: hex.EncodeToString(rawPubEph),
		CipherText:   hex.EncodeToString(ciphertext),
		Salt:         hex.EncodeToString(recoveryCodeSalt),
	}

	return result, nil
}

func decodeEncryptedPrivateKeyV2(
	reader *bytes.Reader,
	encodedKey string,
) (*EncryptedPrivateKeyInfo, error) {

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
		Version:      KeySerializationVersion2,
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
		CipherText:   secret.NewBytes(cipherText),
		Salt:         secret.NewBytes(salt),
	}

	return unwrapped, nil
}
