package test

import (
	"crypto/sha256"
	"encoding/hex"
	"math/big"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

// The tree is what the spec's certificate commits to, so the mock card's has
// to hash exactly the way the vendor's does. The root below was computed
// outside libwallet with an independent P-256 and SHA-256, the same way
// cards/vendor-cli/muuncardv3/merkle_test.go pins its root.

func TestKeyAgreementTreeV3_1_RootMatchesAnIndependentComputation(t *testing.T) {
	keys := testKeyAgreementTreeKeysV3_1(t)
	require.Equal(
		t,
		decodeHex(t, "036b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"),
		keys[0],
		"leaf 0 should be G",
	)

	tree, err := nfc.NewKeyAgreementTreeV3_1(keys)
	require.NoError(t, err)

	require.Equal(t, nfc.KeyAgreementKeyCountV3_1, tree.Size())
	require.Equal(
		t,
		decodeHex(t, "b6b57e382fbad879d19bf22b19c7f17186d61b975e946b0a8fb4b3504caba11b"),
		tree.Root(),
	)
}

func TestKeyAgreementTreeV3_1_EveryInclusionPathLeadsToTheRoot(t *testing.T) {
	keys := testKeyAgreementTreeKeysV3_1(t)
	tree, err := nfc.NewKeyAgreementTreeV3_1(keys)
	require.NoError(t, err)

	for index, key := range keys {
		path, err := tree.InclusionPath(index)
		require.NoError(t, err)

		require.Len(t, path, 6, "a tree of 64 leaves is 6 levels deep")
		require.Equal(t, tree.Root(), walkInclusionPathV3_1(key, index, path), "leaf %d", index)
	}
}

func TestKeyAgreementTreeV3_1_RefusesAnotherSize(t *testing.T) {
	keys := testKeyAgreementTreeKeysV3_1(t)

	for _, size := range []int{0, 32, 63} {
		_, err := nfc.NewKeyAgreementTreeV3_1(keys[:size])
		require.ErrorContains(t, err, "must have 64 leaves")
	}
	_, err := nfc.NewKeyAgreementTreeV3_1(append(keys, keys[0]))
	require.ErrorContains(t, err, "must have 64 leaves")
}

func TestKeyAgreementTreeV3_1_RefusesALeafThatIsNotACompressedPoint(t *testing.T) {
	keys := testKeyAgreementTreeKeysV3_1(t)

	uncompressed := append([][]byte{}, keys...)
	uncompressed[7] = append([]byte{0x04}, keys[7][1:]...)
	_, err := nfc.NewKeyAgreementTreeV3_1(uncompressed)
	require.ErrorContains(t, err, "leaf 7 must be a compressed point")

	short := append([][]byte{}, keys...)
	short[9] = keys[9][:32]
	_, err = nfc.NewKeyAgreementTreeV3_1(short)
	require.ErrorContains(t, err, "leaf 9 must be a 33-byte compressed point")
}

func TestKeyAgreementTreeV3_1_RefusesAnIndexOutsideTheTree(t *testing.T) {
	tree, err := nfc.NewKeyAgreementTreeV3_1(testKeyAgreementTreeKeysV3_1(t))
	require.NoError(t, err)

	for _, index := range []int{-1, tree.Size()} {
		_, err := tree.InclusionPath(index)
		require.ErrorContains(t, err, "outside a tree of 64 leaves")
	}
}

//==========================
// Private, helper functions
//==========================

// testKeyAgreementTreeKeysV3_1 are the compressed points k * G for k = 1 to
// 64, in that order.
func testKeyAgreementTreeKeysV3_1(t *testing.T) [][]byte {
	t.Helper()

	keys := make([][]byte, 0, nfc.KeyAgreementKeyCountV3_1)
	for k := 1; k <= nfc.KeyAgreementKeyCountV3_1; k++ {
		scalar := make([]byte, 32)
		big.NewInt(int64(k)).FillBytes(scalar)

		publicKey, err := cryptography.GenerateSecp256r1PublicKey(scalar)
		require.NoError(t, err)

		// SEC1 compression: the parity of y as the prefix, then x.
		compressed := []byte{0x02 | publicKey[64]&1}
		keys = append(keys, append(compressed, publicKey[1:33]...))
	}
	return keys
}

// walkInclusionPathV3_1 recomputes the root from a leaf and its path the way
// a verifier does (RFC 9162 section 2.1.3.2), hashing by hand rather than
// through the tree, so it cannot agree with the tree by sharing its mistakes.
func walkInclusionPathV3_1(leaf []byte, index int, path [][]byte) []byte {
	hash := sha256.Sum256(append([]byte{0x00}, leaf...))

	for _, sibling := range path {
		if index%2 == 0 {
			hash = sha256.Sum256(append(append([]byte{0x01}, hash[:]...), sibling...))
		} else {
			hash = sha256.Sum256(append(append([]byte{0x01}, sibling...), hash[:]...))
		}
		index /= 2
	}

	return hash[:]
}

func decodeHex(t *testing.T, encoded string) []byte {
	t.Helper()

	decoded, err := hex.DecodeString(encoded)
	require.NoError(t, err)
	return decoded
}
