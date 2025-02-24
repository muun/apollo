package libwallet

// Utils for tests

import (
	"encoding/hex"
)

func hexToBytes(value string) []byte {
	bytes, _ := hex.DecodeString(value)
	return bytes
}
