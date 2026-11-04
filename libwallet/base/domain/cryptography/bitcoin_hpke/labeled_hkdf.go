package bitcoin_hpke

import (
	"crypto/sha256"
	"slices"

	"github.com/go-errors/errors"
	"golang.org/x/crypto/hkdf"
)

// See Section 4.0 of RFC 9180
func labeledExtract(
	salt, label, ikm, suiteID []byte,
) []byte {
	labeledIkm := slices.Concat([]byte(hpkeIdentifier), suiteID, label, ikm)
	return hkdf.Extract(sha256.New, labeledIkm, salt)
}

// See Section 4.0 of RFC 9180
func labeledExpand(
	pseudoRandomKey, label, info []byte,
	lengthInBytes int,
	suiteID []byte,
) ([]byte, error) {
	labeledInfo := slices.Concat(
		i2Osp(lengthInBytes, 2),
		[]byte(hpkeIdentifier),
		suiteID,
		label,
		info,
	)
	expandReader := hkdf.Expand(sha256.New, pseudoRandomKey, labeledInfo)
	result := make([]byte, lengthInBytes)
	n, err := expandReader.Read(result)
	if n != lengthInBytes {
		return nil, errors.Errorf("expand failed")
	}
	if err != nil {
		return nil, err
	}
	return result, nil
}
