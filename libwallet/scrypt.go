package libwallet

import (
	"golang.org/x/crypto/scrypt"
)

const (
	iterations            = 512
	blockSize             = 8
	parallelizationFactor = 1
	outputLength          = 32
)

func Scrypt256(data, salt []byte) []byte {
	return scrypt256(data, salt, iterations, blockSize, parallelizationFactor)
}

func scrypt256(data, salt []byte, iterations, blockSize, parallelizationFactor int) []byte {

	key, err := scrypt.Key(data, salt, iterations, blockSize, parallelizationFactor, outputLength)

	if err != nil {
		panic("scrypt parameters are bad")
	}

	return key
}
