package v2

import (
	"crypto/sha256"
	"encoding/hex"
	"slices"
)

// GetScriptHash returns the script hash parameter to use with Electrum, given a Bitcoin script.
func GetScriptHash(script []byte) string {
	indexHash := sha256.Sum256(script)
	slices.Reverse(indexHash[:])
	return hex.EncodeToString(indexHash[:])
}
