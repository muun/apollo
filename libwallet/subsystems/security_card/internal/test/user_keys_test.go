package test

import (
	"bytes"
	"crypto/rand"
	"testing"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/stretchr/testify/require"

	adapted_mock "github.com/muun/libwallet/app_provided_data/adapted/mock"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/platform/test/testkeys"
)

// v3TestUserKey is the user key these tests run under: the Houston simulator
// derives the client approval key from its public half, and the tests sign
// approvals with it. Every setup has to share one, so it comes from a fixed
// seed. It sits at the base path, where the apps hand libwallet the key.
func v3TestUserKey(t *testing.T) *hd_keys.HDPrivateKey {
	t.Helper()

	return basePathUserKeyFromSeed(t, bytes.Repeat([]byte{0x42}, 32))
}

// newBasePathUserKey returns a fresh random user key at the base path, for
// tests that need a wallet of their own.
func newBasePathUserKey(t *testing.T) *hd_keys.HDPrivateKey {
	t.Helper()

	seed := make([]byte, 32)
	_, err := rand.Read(seed)
	require.NoError(t, err)

	return basePathUserKeyFromSeed(t, seed)
}

// basePathUserKeyFromSeed returns the user key the given seed derives at the
// base path.
func basePathUserKeyFromSeed(t *testing.T, seed []byte) *hd_keys.HDPrivateKey {
	t.Helper()

	master, err := hd_keys.NewHDPrivateKeyFromSeed(seed, &chaincfg.RegressionNetParams)
	require.NoError(t, err)

	userKey, err := master.DeriveTo(hd_keys.SchemaBasePath)
	require.NoError(t, err)

	return userKey
}

// newUserKeyProvider returns a key provider that hands out userKey: its
// private half to the sign flow, its public half to the Houston simulator.
func newUserKeyProvider(t *testing.T, userKey *hd_keys.HDPrivateKey) *adapted_mock.MockKeyProvider {
	t.Helper()

	keyProvider := adapted_mock.NewMockKeyProvider(t)
	testkeys.ExpectUserPrivateKey(keyProvider, userKey).Maybe()
	keyProvider.EXPECT().UserPublicKey().Return(userKey.PublicKey(), nil).Maybe()

	return keyProvider
}
