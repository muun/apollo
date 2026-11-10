package musig

import (
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/stretchr/testify/require"
)

// TestAggregateKeysV100SortsSignerKeys checks that the order the keys are given
// in does not reach the aggregate key, which v100 sorts.
func TestAggregateKeysV100SortsSignerKeys(t *testing.T) {
	t.Parallel()

	signerKeys := aggregationTestKeys(t, 3)
	reversed := []*btcec.PublicKey{signerKeys[2], signerKeys[1], signerKeys[0]}

	aggregation, err := aggregateKeysV100(signerKeys, KeySpendOnlyTweak())
	require.NoError(t, err)

	aggregateKey, err := aggregation.PublicKey()
	require.NoError(t, err)

	shuffled, err := aggregateKeysV100(reversed, KeySpendOnlyTweak())
	require.NoError(t, err)

	shuffledKey, err := shuffled.PublicKey()
	require.NoError(t, err)

	require.Equal(t,
		hex.EncodeToString(aggregateKey), hex.EncodeToString(shuffledKey),
	)
}

// TestComputePartialSignatureV100RejectsSignerOutsideKeySet pins the check
// btcec did in NewContext: libsecp256k1 would otherwise sign with a key that
// is not part of the aggregate and hand back a partial signature that cannot
// verify.
func TestComputePartialSignatureV100RejectsSignerOutsideKeySet(t *testing.T) {
	t.Parallel()

	outsiderKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)

	signerKeys := aggregationTestKeys(t, 2)
	cosignerSessionID := RandomSessionID()
	cosignerNonce, err := MuSig2GenerateNonce(
		Musig2v100, cosignerSessionID[:], signerKeys[1].SerializeCompressed(),
	)
	require.NoError(t, err)

	signerSessionID := RandomSessionID()
	data := make([]byte, 32)

	_, err = computePartialSignatureV100(
		data,
		outsiderKey.Serialize(),
		signerKeys,
		[][]byte{cosignerNonce.PubNonce[:]},
		signerSessionID[:],
		KeySpendOnlyTweak(),
	)
	require.ErrorContains(t, err, "not found in key set")
}

func aggregationTestKeys(t *testing.T, count int) []*btcec.PublicKey {
	t.Helper()

	keys := make([]*btcec.PublicKey, count)
	for i := range keys {
		signerKey, err := btcec.NewPrivateKey()
		require.NoError(t, err)

		keys[i] = signerKey.PubKey()
	}

	return keys
}
