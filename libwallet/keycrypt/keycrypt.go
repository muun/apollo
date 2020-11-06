package keycrypt

import (
	"bytes"
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"unicode/utf16"

	"github.com/btcsuite/btcutil/hdkeychain"

	"github.com/muun/libwallet/aescbc"
	"golang.org/x/crypto/scrypt"
)

const (
	ivLength   = 16
	saltLength = 8

	scryptIterations            = 512
	scryptBlockSize             = 8
	scryptParallelizationFactor = 1
	scryptOutputLength          = 32

	separator = ":"
)

// Encrypt encrypts an HD priv key using a user-provided secret into a string
// We use SCrypt256 for key derivation and AES-CBC-PKCS7 for encryption.
// The returned string has information about version, derivation path, scrypt and AES parameters.
func Encrypt(key *hdkeychain.ExtendedKey, keyPath, passphrase string) (string, error) {
	iv := randomBytes(ivLength)
	salt := randomBytes(saltLength)

	inputSecret, err := scrypt.Key(
		encodeUTF16(passphrase),
		salt,
		scryptIterations,
		scryptBlockSize,
		scryptParallelizationFactor,
		scryptOutputLength,
	)
	if err != nil {
		return "", fmt.Errorf("failed to compute scrypt key: %w", err)
	}

	privateKeyBytes := []byte(key.String())

	encrypted, err := aescbc.EncryptPkcs7(inputSecret, iv, privateKeyBytes)
	if err != nil {
		return "", fmt.Errorf("failed to encrypt: %w", err)
	}

	derivationPathBytes := []byte(keyPath)

	elements := []string{
		"v1",
		strconv.Itoa(scryptIterations),
		strconv.Itoa(scryptParallelizationFactor),
		strconv.Itoa(scryptBlockSize),
		hex.EncodeToString(salt),
		hex.EncodeToString(iv),
		hex.EncodeToString(encrypted),
		hex.EncodeToString(derivationPathBytes),
	}

	return strings.Join(elements, separator), nil
}

// Decrypt decrypts a key encrypted with Encrypt
func Decrypt(value, passphrase string) (*hdkeychain.ExtendedKey, string, error) {

	elements := strings.Split(value, separator)

	if len(elements) != 8 {
		return nil, "", errors.New("invalid format")
	}

	version := elements[0]
	iterations, err := strconv.Atoi(elements[1])
	if err != nil {
		return nil, "", fmt.Errorf("invalid iterations: %w", err)
	}

	parallelizationFactor, err := strconv.Atoi(elements[2])
	if err != nil {
		return nil, "", fmt.Errorf("invalid p: %w", err)
	}

	blockSize, err := strconv.Atoi(elements[3])
	if err != nil {
		return nil, "", fmt.Errorf("invalid blocksize: %w", err)
	}

	salt, err := hex.DecodeString(elements[4])
	if err != nil {
		return nil, "", fmt.Errorf("invalid salt: %w", err)
	}

	iv, err := hex.DecodeString(elements[5])
	if err != nil {
		return nil, "", fmt.Errorf("invalid iv: %w", err)
	}

	payload, err := hex.DecodeString(elements[6])
	if err != nil {
		return nil, "", fmt.Errorf("invalid payload: %w", err)
	}

	pathBytes, err := hex.DecodeString(elements[7])
	if err != nil {
		return nil, "", fmt.Errorf("invalid path: %w", err)
	}

	if version != "v1" {
		return nil, "", fmt.Errorf("invalid version %s", version)
	}

	inputSecret, err := scrypt.Key(
		encodeUTF16(passphrase),
		salt,
		iterations,
		blockSize,
		parallelizationFactor,
		scryptOutputLength,
	)
	if err != nil {
		return nil, "", fmt.Errorf("failed to compute scrypt key: %w", err)
	}

	decryptedBytes, err := aescbc.DecryptPkcs7(inputSecret, iv, payload)
	if err != nil {
		return nil, "", fmt.Errorf("failed to decrypt: %w", err)
	}

	key, err := hdkeychain.NewKeyFromString(string(decryptedBytes[:]))
	if err != nil {
		return nil, "", fmt.Errorf("could not decode private key: %w", err)
	}
	if !key.IsPrivate() {
		return nil, "", errors.New("expected extended key to be private, not public")
	}

	path := string(pathBytes[:])

	return key, path, nil
}

func randomBytes(count int) []byte {
	buf := make([]byte, count)
	_, err := rand.Read(buf)
	if err != nil {
		panic("couldn't read random bytes")
	}

	return buf
}

func encodeUTF16(s string) []byte {
	// You might wonder why this code exists....
	// Turns out that the scrypt implementation used in android is hardwired
	// to use strings as UTF16 (which is Java's native format). So we need to
	// use the same exact byte array encoding.

	var buf bytes.Buffer
	for _, r := range utf16.Encode([]rune(s)) {
		binary.Write(&buf, binary.BigEndian, r)
	}
	return buf.Bytes()
}
