package recoverycode

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"errors"
	fmt "fmt"
	"strings"

	"golang.org/x/crypto/scrypt"

	"github.com/btcsuite/btcd/btcec"
)

const (
	kdfKey                   = "muun:rc"
	kdfIterations            = 512
	kdfBlockSize             = 8
	kdfParallelizationFactor = 1
	kdfOutputLength          = 32
)

// CurrentVersion defines the current version number for the recovery codes.
const CurrentVersion = 2

// Alphabet contains all upper-case characters except for numbers/letters that
// look alike.
const Alphabet = "ABCDEFHJKLMNPQRSTUVWXYZ2345789"

// AlphabetLegacy constains the letters that pre version 2 recovery codes can
// contain.
const AlphabetLegacy = "ABCDEFHJKMNPQRSTUVWXYZ2345789"

// Generate creates a new random recovery code using a cryptographically
// secure random number generator.
func Generate() string {
	var sb strings.Builder
	sb.WriteByte('L')
	// we subtract 2 from the version number so that the first character of the
	// alphabet correspods to version 2
	sb.WriteByte(Alphabet[CurrentVersion-2])

	codeLen := 30
	for i := 0; i < codeLen; i++ {
		sb.WriteByte(randChar(Alphabet))
		j := i + 3 // we count the two bytes we wrote before the loop
		if j != 0 && i != codeLen-1 && j%4 == 0 {
			sb.WriteByte('-')
		}
	}

	return sb.String()
}

// randChar returns a random character from the given string
//
// The algorithm was inspired by BSD arc4random_uniform function to avoid
// modulo bias and ensure a uniform distribution
// http://cvsweb.openbsd.org/cgi-bin/cvsweb/~checkout~/src/lib/libc/crypt/arc4random_uniform.c
func randChar(chars string) byte {
	clen := len(chars)
	min := -clen % clen
	for {
		var b [1]byte
		_, err := rand.Read(b[:])
		if err != nil {
			panic("could not read enough random bytes for recovery code")
		}
		r := int(b[0])
		if r < min {
			continue
		}
		return chars[r%clen]
	}
}

// ConvertToKey generates a private key using the recovery code as a seed.
//
// The salt parameter is only used for version 1 codes. It will be ignored
// for version 2+ codes.
func ConvertToKey(code, salt string) (*btcec.PrivateKey, error) {
	version, err := Version(code)
	if err != nil {
		return nil, err
	}

	var input []byte

	switch version {
	case 1:
		input, err = scrypt.Key(
			[]byte(code),
			[]byte(salt),
			kdfIterations,
			kdfBlockSize,
			kdfParallelizationFactor,
			kdfOutputLength,
		)
		if err != nil {
			return nil, err
		}
	case 2:
		mac := hmac.New(sha256.New, []byte(kdfKey))
		mac.Write([]byte(code))
		input = mac.Sum(nil)
	}

	// 2nd return value is the pub key which we don't need right now
	priv, _ := btcec.PrivKeyFromBytes(btcec.S256(), input)
	return priv, nil
}

// Validate returns an error if the recovery code is not valid or nil otherwise.
func Validate(code string) error {
	_, err := Version(code)
	return err
}

// Version returns the version that this recovery code corresponds to.
func Version(code string) (int, error) {
	if len(code) != 39 {
		return 0, errors.New("invalid recovery code length")
	}
	if code[0] == 'L' { // version 2+ codes always start with L
		idx := strings.IndexByte(Alphabet, code[1])
		if idx == -1 {
			return 0, errors.New("invalid recovery code version")
		}
		if !validateAlphabet(code, Alphabet) {
			return 0, fmt.Errorf("invalid recovery code characters")
		}
		// we add 2 to the idx because the first letter corresponds to code version 2
		version := idx + 2
		if version > CurrentVersion {
			return 0, fmt.Errorf("unrecognized recovery code version: %d", version)
		}
		return version, nil
	}
	if !validateAlphabet(code, AlphabetLegacy) {
		return 0, fmt.Errorf("invalid recovery code characters")
	}
	return 1, nil
}

func validateAlphabet(s, alphabet string) bool {
	var charsInBlock int
	for _, c := range s {
		if charsInBlock == 4 {
			if c == '-' {
				charsInBlock = 0
				continue
			}
			return false
		}
		if !strings.Contains(alphabet, string(c)) {
			return false
		}
		charsInBlock++
	}
	return true
}
