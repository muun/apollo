package libsecp256k1

import (
	"crypto/sha256"
	"fmt"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	btcecmusig2 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/stretchr/testify/require"
)

func TestAggregatePublicKeysMatchesBtcec(t *testing.T) {
	for _, signerCount := range []int{1, 2, 3, 5} {
		t.Run(fmt.Sprintf("%d signers", signerCount), func(t *testing.T) {
			publicKeys, btcecKeys := signerKeys(signerCount)

			aggregation, err := AggregatePublicKeys(publicKeys)
			require.NoError(t, err)

			aggregate, err := aggregation.PublicKey()
			require.NoError(t, err)

			expected, _, _, err := btcecmusig2.AggregateKeys(btcecKeys, false)
			require.NoError(t, err)

			require.Equal(t, expected.FinalKey.SerializeCompressed(), aggregate)
		})
	}
}

func TestAddPlainTweakMatchesBtcec(t *testing.T) {
	publicKeys, btcecKeys := signerKeys(3)
	tweak := sha256.Sum256([]byte("plain tweak"))

	aggregation, err := AggregatePublicKeys(publicKeys)
	require.NoError(t, err)

	require.NoError(t, aggregation.AddPlainTweak(tweak[:]))

	tweaked, err := aggregation.PublicKey()
	require.NoError(t, err)

	expected, _, _, err := btcecmusig2.AggregateKeys(btcecKeys, false,
		btcecmusig2.WithKeyTweaks(btcecmusig2.KeyTweakDesc{Tweak: tweak}),
	)
	require.NoError(t, err)

	require.Equal(t, expected.FinalKey.SerializeCompressed(), tweaked)
}

func TestAddXOnlyTweakMatchesBtcec(t *testing.T) {
	publicKeys, btcecKeys := signerKeys(3)
	tweak := sha256.Sum256([]byte("x-only tweak"))

	aggregation, err := AggregatePublicKeys(publicKeys)
	require.NoError(t, err)

	require.NoError(t, aggregation.AddXOnlyTweak(tweak[:]))

	tweaked, err := aggregation.PublicKey()
	require.NoError(t, err)

	expected, _, _, err := btcecmusig2.AggregateKeys(btcecKeys, false,
		btcecmusig2.WithKeyTweaks(btcecmusig2.KeyTweakDesc{Tweak: tweak, IsXOnly: true}),
	)
	require.NoError(t, err)

	require.Equal(t, expected.FinalKey.SerializeCompressed(), tweaked)
}

// TestChainedTweaksMatchBtcec applies tweaks one call at a time, which is how
// unhardened derivation reaches the bindings, where btcec takes them all at
// once. Each call reads the cache the previous one left behind.
func TestChainedTweaksMatchBtcec(t *testing.T) {
	first := sha256.Sum256([]byte("first tweak"))
	second := sha256.Sum256([]byte("second tweak"))

	tests := []struct {
		name   string
		tweaks []btcecmusig2.KeyTweakDesc
	}{
		{
			name: "two plain tweaks",
			tweaks: []btcecmusig2.KeyTweakDesc{
				{Tweak: first}, {Tweak: second},
			},
		},
		{
			name: "a plain tweak then an x-only one",
			tweaks: []btcecmusig2.KeyTweakDesc{
				{Tweak: first}, {Tweak: second, IsXOnly: true},
			},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			publicKeys, btcecKeys := signerKeys(3)

			aggregation, err := AggregatePublicKeys(publicKeys)
			require.NoError(t, err)

			for i, tweak := range test.tweaks {
				if tweak.IsXOnly {
					err = aggregation.AddXOnlyTweak(tweak.Tweak[:])
				} else {
					err = aggregation.AddPlainTweak(tweak.Tweak[:])
				}
				require.NoError(t, err)

				// The key read back after each step has the tweaks so far
				// applied.
				expected, _, _, err := btcecmusig2.AggregateKeys(btcecKeys, false,
					btcecmusig2.WithKeyTweaks(test.tweaks[:i+1]...),
				)
				require.NoError(t, err)

				current, err := aggregation.PublicKey()
				require.NoError(t, err)
				require.Equal(t, expected.FinalKey.SerializeCompressed(), current)
			}
		})
	}
}

func TestAggregatePublicKeysRejectsBadInput(t *testing.T) {
	publicKeys, _ := signerKeys(2)

	_, err := AggregatePublicKeys(nil)
	require.Error(t, err, "aggregating no keys")

	_, err = AggregatePublicKeys([][]byte{publicKeys[0][:PublicKeySize-1]})
	require.Error(t, err, "aggregating a short key")

	aggregation, err := AggregatePublicKeys(publicKeys)
	require.NoError(t, err)

	err = aggregation.AddPlainTweak(make([]byte, TweakSize-1))
	require.Error(t, err, "a short plain tweak")

	err = aggregation.AddXOnlyTweak(make([]byte, TweakSize+1))
	require.Error(t, err, "a long x-only tweak")
}

// signerKeys derives count public keys, serialized for this package and parsed
// for btcec.
func signerKeys(count int) ([][]byte, []*btcec.PublicKey) {
	serialized := make([][]byte, count)
	parsed := make([]*btcec.PublicKey, count)

	for i := range serialized {
		secretKey := sha256.Sum256(fmt.Appendf(nil, "signer %d", i))
		_, publicKey := btcec.PrivKeyFromBytes(secretKey[:])

		serialized[i] = publicKey.SerializeCompressed()
		parsed[i] = publicKey
	}

	return serialized, parsed
}
