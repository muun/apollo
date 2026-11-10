package nfc

import (
	"crypto/sha256"
)

// ComputeHMACSHA256 computes HMAC-SHA256. It computes the HMAC (Hash-based Message Authentication
// Code) of the given data using the provided key and the SHA-256 hash function.
//
// It manually implements the HMAC algorithm as defined in RFC 2104:
//
//	HMAC(key, data) = SHA256((key ⊕ opad) || SHA256((key ⊕ ipad) || data))
//
// If the key is longer than the SHA-256 block size (64 bytes), it is first
// hashed with SHA-256. The resulting HMAC is returned as a byte slice.
//
// This function can be used to verify message integrity and authenticity when
// both parties share a secret key.
//
// Example:
//
//	mac := ComputeHMACSHA256([]byte("secret"), []byte("message"))
func ComputeHMACSHA256(key, data []byte) []byte {
	const blockSize = 64 // SHA-256 block size

	// Key preprocessing
	if len(key) > blockSize {
		hash := sha256.Sum256(key)
		key = hash[:]
	}

	// Pad key to block size
	paddedKey := make([]byte, blockSize)
	copy(paddedKey, key)

	// Create inner and outer padding
	innerPad := make([]byte, blockSize)
	outerPad := make([]byte, blockSize)

	for i := range blockSize {
		innerPad[i] = paddedKey[i] ^ 0x36
		outerPad[i] = paddedKey[i] ^ 0x5c
	}

	// Inner hash
	innerHash := sha256.New()
	innerHash.Write(innerPad)
	innerHash.Write(data)
	innerResult := innerHash.Sum(nil)

	// Outer hash
	outerHash := sha256.New()
	outerHash.Write(outerPad)
	outerHash.Write(innerResult)

	return outerHash.Sum(nil)
}
