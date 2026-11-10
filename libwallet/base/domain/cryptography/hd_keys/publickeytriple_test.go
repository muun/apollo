package hd_keys

import (
	"testing"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/stretchr/testify/require"
)

// tripleKeyForTest derives a fresh public key at path from a random seed.
func tripleKeyForTest(t *testing.T, path string, network *chaincfg.Params) *HDPublicKey {
	t.Helper()

	priv, err := NewHDPrivateKeyFromSeed(randomBytes(32), network)
	require.NoError(t, err)
	derived, err := priv.PublicKey().DeriveTo(path)
	require.NoError(t, err)
	return derived
}

func TestNewPublicKeyTriple(t *testing.T) {
	const path = "m/1/2"
	network := &chaincfg.RegressionNetParams

	user := tripleKeyForTest(t, path, network)
	cosigner := tripleKeyForTest(t, path, network)
	lightningServer := tripleKeyForTest(t, path, network)

	triple := NewPublicKeyTriple(user, cosigner, lightningServer)

	require.Equal(t, user, triple.UserPublicKey())
	require.Equal(t, cosigner, triple.CosignerPublicKey())
	require.Equal(t, lightningServer, triple.LightningServerPublicKey())
	require.Equal(t, path, triple.Path())
	require.Equal(t, network, triple.Network())
}

func TestNewPublicKeyTripleRejectsMismatchingPaths(t *testing.T) {
	network := &chaincfg.RegressionNetParams

	user := tripleKeyForTest(t, "m/1/2", network)
	cosigner := tripleKeyForTest(t, "m/1/3", network)
	lightningServer := tripleKeyForTest(t, "m/1/2", network)

	require.PanicsWithError(
		t,
		"keys have mismatching derivation paths: "+
			"user m/1/2, cosigner m/1/3, lightning server m/1/2",
		func() { NewPublicKeyTriple(user, cosigner, lightningServer) },
	)
}

func TestNewPublicKeyTripleRejectsMismatchingNetworks(t *testing.T) {
	const path = "m/1/2"

	user := tripleKeyForTest(t, path, &chaincfg.RegressionNetParams)
	cosigner := tripleKeyForTest(t, path, &chaincfg.RegressionNetParams)
	lightningServer := tripleKeyForTest(t, path, &chaincfg.MainNetParams)

	require.PanicsWithError(
		t,
		"keys have mismatching networks: user regtest, cosigner regtest, lightning server mainnet",
		func() { NewPublicKeyTriple(user, cosigner, lightningServer) },
	)
}

func TestPublicKeyTripleDeriveTo(t *testing.T) {
	const path = "m/1"
	network := &chaincfg.RegressionNetParams

	user := tripleKeyForTest(t, path, network)
	cosigner := tripleKeyForTest(t, path, network)
	lightningServer := tripleKeyForTest(t, path, network)

	triple := NewPublicKeyTriple(user, cosigner, lightningServer)

	derived, err := triple.DeriveTo("m/1/7")
	require.NoError(t, err)

	require.Equal(t, "m/1/7", derived.Path())
	expectedUser, err := user.DeriveTo("m/1/7")
	require.NoError(t, err)
	require.Equal(t, expectedUser.String(), derived.UserPublicKey().String())

	// Deriving to a path that doesn't extend the triple's fails.
	_, err = triple.DeriveTo("m/2")
	require.Error(t, err)
}
