package libsecp256k1

// #include "secp256k1.h"
import "C"

import (
	"github.com/go-errors/errors"
)

// TaggedHashSize is the length of a tagged hash.
const TaggedHashSize = 32

// TaggedHash computes the BIP340 tagged hash of message under tag, which is
// SHA256(SHA256(tag) || SHA256(tag) || message). Taproot uses it to commit to a
// script tree, among others.
func TaggedHash(tag string, message []byte) ([]byte, error) {
	if len(tag) == 0 {
		return nil, errors.New("a tag is required to hash")
	}

	// Hashing rejects a null message even when it is empty, and toUchar only
	// points at a slice that has capacity.
	if len(message) == 0 {
		message = make([]byte, 0, 1)
	}

	hash := make([]byte, TaggedHashSize)

	if C.secp256k1_tagged_sha256(
		ctx,
		toUchar(hash),
		toUchar([]byte(tag)),
		C.size_t(len(tag)),
		toUchar(message),
		C.size_t(len(message)),
	) != 1 {
		return nil, errors.New("failed to compute the tagged hash")
	}

	return hash, nil
}
