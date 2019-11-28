package libwallet

import (
	"strconv"
	"strings"
	"unicode/utf16"

	"github.com/pkg/errors"

	"crypto/rand"
	"encoding/hex"
)

const (
	ivLength   = 16
	saltLength = 8

	scryptIterations            = 512
	scryptBlockSize             = 8
	scryptParallelizationFactor = 1

	seperator = ":"
)

type DecryptedKey struct {
	Key  *HDPrivateKey
	Path string
}

// KeyEncrypt encrypts an HD priv key using a user-provided secret into a string
// We use SCrypt256 for key derivation and AES-CBC-PKCS7 for encryption.
// The returned string has information about version, derivation path, scrypt and AES parameters.
func KeyEncrypt(key *HDPrivateKey, passphrase string) (string, error) {

	privateKeyBytes := []byte(key.String())
	iv := randomBytes(ivLength)
	salt := randomBytes(saltLength)

	inputSecret := scrypt256(stringToBytes(passphrase),
		salt,
		scryptIterations,
		scryptBlockSize,
		scryptParallelizationFactor)

	encrypted, err := encrypt(inputSecret, iv, privateKeyBytes)
	if err != nil {
		return "", err
	}

	derivationPathBytes := []byte(key.Path)

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

	return strings.Join(elements, seperator), nil
}

// KeyDecrypt decrypts a key encrypted with KeyEncrypt
func KeyDecrypt(value, passphrase string, network *Network) (*DecryptedKey, error) {

	elements := strings.Split(value, seperator)

	if len(elements) != 8 {
		return nil, errors.New("KeyCrypter: invalid format")
	}

	version := elements[0]
	iterations, err := strconv.Atoi(elements[1])
	if err != nil {
		return nil, errors.New("KeyCrypter: invalid iterations: " + err.Error())
	}

	parallelizationFactor, err := strconv.Atoi(elements[2])
	if err != nil {
		return nil, errors.New("KeyCrypter: invalid p: " + err.Error())
	}

	blockSize, err := strconv.Atoi(elements[3])
	if err != nil {
		return nil, errors.New("KeyCrypter: invalid blocksize: " + err.Error())
	}

	salt, err := hex.DecodeString(elements[4])
	if err != nil {
		return nil, errors.New("KeyCrypter: invalid salt: " + err.Error())
	}

	iv, err := hex.DecodeString(elements[5])
	if err != nil {
		return nil, errors.New("KeyCrypter: invalid iv: " + err.Error())
	}

	payload, err := hex.DecodeString(elements[6])
	if err != nil {
		return nil, errors.New("KeyCrypter: invalid payload: " + err.Error())
	}

	pathBytes, err := hex.DecodeString(elements[7])
	if err != nil {
		return nil, errors.New("KeyCrypter: invalid path: " + err.Error())
	}

	if version != "v1" {
		return nil, errors.New("KeyCrypter: invalid version " + version)
	}

	inputSecret := scrypt256(stringToBytes(passphrase),
		salt,
		iterations,
		blockSize,
		parallelizationFactor)

	decryptedBytes, err := decrypt(inputSecret, iv, payload)
	if err != nil {
		return nil, errors.New("KeyCrypter: failed to decrypt pk: " + err.Error())
	}

	encodedPrivateKey := string(decryptedBytes[:])
	path := string(pathBytes[:])

	privateKey, err := NewHDPrivateKeyFromString(encodedPrivateKey, path, network)
	if err != nil {
		return nil, errors.New("KeyCrypter: failed to decode pk: " + err.Error())
	}

	return &DecryptedKey{Key: privateKey, Path: path}, nil
}

func randomBytes(count int) []byte {
	buf := make([]byte, count)
	_, err := rand.Read(buf)
	if err != nil {
		panic("couldn't read random bytes")
	}

	return buf
}

func stringToBytes(str string) []byte {

	// You might wonder why this code exists....
	// Turns out that the scrypt implementation used in android is hardwired
	// to use strings as UTF16 (which is Java's native format). So we need to
	// use the same exact byte array encoding.

	// There seems no way to convert a strint to code points directly
	// for range iterates strings in a rune basis, so we do that
	var runes []rune
	for _, rune := range str {
		runes = append(runes, rune)
	}

	result := make([]byte, 0)
	for _, char := range utf16.Encode(runes) {
		result = append(result, byte((char&0xFF00)>>8))
		result = append(result, byte(char&0x00FF))
	}

	return result
}
