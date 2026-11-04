package libwallet

import (
	"crypto/sha256"
)

func SHA256(data []byte) []byte {
	hash := sha256.Sum256(data)
	return hash[:]
}
