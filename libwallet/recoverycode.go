package libwallet

import (
	"github.com/muun/libwallet/recoverycode"
)

// RecoveryCodeAlphabet contains all upper-case characters except for
// numbers/letters that look alike.
const RecoveryCodeAlphabet = recoverycode.Alphabet

// GenerateRecoveryCode generates a new random recovery code using a
// cryptographically secure random number generator.
func GenerateRecoveryCode() string {
	return recoverycode.Generate()
}

// RecoveryCodeToKey generates a challenge private key using the
// recovery code as a seed.
//
// The salt parameter is only used for version 1 codes. It will be ignored
// for version 2+ codes.
func RecoveryCodeToKey(code, salt string) (*ChallengePrivateKey, error) {
	privKey, err := recoverycode.ConvertToKey(code, salt)
	if err != nil {
		return nil, err
	}
	return &ChallengePrivateKey{key: privKey}, nil
}

// ValidateRecoveryCode returns an error if the recovery code is not valid
// or nil otherwise.
func ValidateRecoveryCode(code string) error {
	return recoverycode.Validate(code)
}

// GetRecoveryCodeVersion returns the version for the recovery code given.
// If no version can be recognized, it returns an error.
func GetRecoveryCodeVersion(code string) (int, error) {
	return recoverycode.Version(code)
}
