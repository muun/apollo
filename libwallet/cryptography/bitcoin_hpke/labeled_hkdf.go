package bitcoin_hpke

import (
	"crypto/sha256"
	"fmt"
	"golang.org/x/crypto/hkdf"
	"slices"
)

// See Section 4.0 of RFC 9180
func labeledExtract(salt, label, ikm, suiteId []byte) []byte {
	labeledIkm := slices.Concat([]byte(hpkeIdentifier), suiteId, label, ikm)
	return hkdf.Extract(sha256.New, labeledIkm, salt)
}

// See Section 4.0 of RFC 9180
func labeledExpand(pseudoRandomKey, label, info []byte, lengthInBytes int, suiteId []byte) ([]byte, error) {
	labeledInfo := slices.Concat(i2Osp(lengthInBytes, 2), []byte(hpkeIdentifier), suiteId, label, info)
	expandReader := hkdf.Expand(sha256.New, pseudoRandomKey, labeledInfo)
	result := make([]byte, lengthInBytes)
	n, err := expandReader.Read(result)
	if n != lengthInBytes {
		return nil, fmt.Errorf("expand failed")
	}
	if err != nil {
		return nil, err
	}
	return result, nil
}
