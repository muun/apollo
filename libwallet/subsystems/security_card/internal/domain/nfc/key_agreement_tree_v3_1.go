package nfc

import (
	"crypto/sha256"
	"math/bits"

	"github.com/go-errors/errors"
)

// KeyAgreementKeyCountV3_1 is NUM_KEYS, how many key agreement keys a card
// holds from firmware 3.1 on: one per 16 pairings, 1024 pairings in all. It
// is the protocol spec's value.
const KeyAgreementKeyCountV3_1 = 64

// RFC 9162 domain separation: a leaf hashes 0x00 || data, an inner node
// 0x01 || left || right.
const (
	merkleLeafPrefixV3_1 = 0x00
	merkleNodePrefixV3_1 = 0x01
)

// SEC1 encodings of a P-256 point. A compressed point is the parity of y as
// a prefix, then x.
const (
	sec1EvenYPrefix     = 0x02
	sec1OddYPrefix      = 0x03
	compressedPointSize = 1 + p256CoordinateSize
)

// keyAgreementTreeDepthV3_1 is the length of every inclusion path:
// log2(KeyAgreementKeyCountV3_1).
var keyAgreementTreeDepthV3_1 = bits.TrailingZeros(uint(KeyAgreementKeyCountV3_1))

// KeyAgreementTreeV3_1 is the RFC 9162 merkle tree whose root the spec's
// certificate commits to, with one leaf per key agreement key, in key index
// order. The leaf is the key as a compressed SEC1 point. The mock card's
// ceremony builds it the way the vendor's does
// (cards/vendor-cli/muuncardv3/merkle.go), and the two must hash the same
// way: the proof one issues is what the other's server verifies.
//
// It is exported so the tests can pin it against the vector the vendor CLI
// pins, since nothing in libwallet verifies a proof yet.
//
// The spec fixes the size at KeyAgreementKeyCountV3_1 leaves, a power of two,
// so the tree is perfect and stored as the spec's flat array of 2 * NUM_KEYS
// hashes: the root at index 1, the children of node i at 2i and 2i + 1, and
// leaf i at NUM_KEYS + i. Index 0 is unused. RFC 9162 also defines trees of
// other sizes; this one refuses them.
type KeyAgreementTreeV3_1 struct {
	nodes [][sha256.Size]byte
}

// NewKeyAgreementTreeV3_1 builds the tree over the compressed key agreement
// public keys, in key index order.
func NewKeyAgreementTreeV3_1(compressedPublicKeys [][]byte) (*KeyAgreementTreeV3_1, error) {
	size := len(compressedPublicKeys)
	if size != KeyAgreementKeyCountV3_1 {
		return nil, errors.Errorf(
			"key agreement tree must have %d leaves, got %d", KeyAgreementKeyCountV3_1, size,
		)
	}

	nodes := make([][sha256.Size]byte, 2*size)
	for i, publicKey := range compressedPublicKeys {
		if len(publicKey) != compressedPointSize {
			return nil, errors.Errorf(
				"leaf %d must be a %d-byte compressed point, got %d bytes",
				i, compressedPointSize, len(publicKey),
			)
		}
		if publicKey[0] != sec1EvenYPrefix && publicKey[0] != sec1OddYPrefix {
			return nil, errors.Errorf(
				"leaf %d must be a compressed point, got prefix 0x%02x", i, publicKey[0],
			)
		}
		nodes[size+i] = merkleLeafHashV3_1(publicKey)
	}
	for i := size - 1; i > 0; i-- {
		nodes[i] = merkleNodeHashV3_1(nodes[2*i], nodes[2*i+1])
	}

	return &KeyAgreementTreeV3_1{nodes: nodes}, nil
}

// Size returns the number of leaves.
func (t *KeyAgreementTreeV3_1) Size() int {
	return len(t.nodes) / 2
}

// Root returns key_agreement_root, the hash the certificate commits to.
func (t *KeyAgreementTreeV3_1) Root() []byte {
	root := t.nodes[1]
	return root[:]
}

// InclusionPath returns the leaf-to-root path of leaf index, RFC 9162 section
// 2.1.3.1: the sibling of the leaf, then the sibling of each ancestor below
// the root.
func (t *KeyAgreementTreeV3_1) InclusionPath(index int) ([][]byte, error) {
	size := t.Size()
	if index < 0 || index >= size {
		return nil, errors.Errorf("leaf index %d is outside a tree of %d leaves", index, size)
	}

	path := make([][]byte, 0, keyAgreementTreeDepthV3_1)
	for position := size + index; position > 1; position /= 2 {
		sibling := t.nodes[position^1]
		path = append(path, sibling[:])
	}
	return path, nil
}

func merkleLeafHashV3_1(leaf []byte) [sha256.Size]byte {
	hash := sha256.New()
	hash.Write([]byte{merkleLeafPrefixV3_1})
	hash.Write(leaf)
	var digest [sha256.Size]byte
	hash.Sum(digest[:0])
	return digest
}

func merkleNodeHashV3_1(left, right [sha256.Size]byte) [sha256.Size]byte {
	hash := sha256.New()
	hash.Write([]byte{merkleNodePrefixV3_1})
	hash.Write(left[:])
	hash.Write(right[:])
	var digest [sha256.Size]byte
	hash.Sum(digest[:0])
	return digest
}
