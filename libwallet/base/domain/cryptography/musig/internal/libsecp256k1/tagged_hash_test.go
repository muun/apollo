package libsecp256k1

import (
	"crypto/sha256"
	"testing"

	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/stretchr/testify/require"
)

func TestTaggedHashMatchesBtcec(t *testing.T) {
	message := sha256.Sum256([]byte("a script root"))
	internalKey := sha256.Sum256([]byte("an internal key"))

	tests := []struct {
		name    string
		tag     string
		message []byte
	}{
		{name: "taproot tweak", tag: "TapTweak", message: internalKey[:]},
		{
			name:    "taproot tweak committing to a script root",
			tag:     "TapTweak",
			message: append(internalKey[:], message[:]...),
		},
		{name: "empty message", tag: "TapTweak", message: nil},
		{name: "another tag", tag: "TapLeaf", message: internalKey[:]},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			hash, err := TaggedHash(test.tag, test.message)
			require.NoError(t, err)

			expected := chainhash.TaggedHash([]byte(test.tag), test.message)
			require.Equal(t, expected[:], hash)
		})
	}
}

func TestTaggedHashRejectsAnEmptyTag(t *testing.T) {
	_, err := TaggedHash("", []byte("a message"))
	require.Error(t, err, "hashing without a tag")
}
