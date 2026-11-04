package adapted

import (
	"bytes"
	"testing"

	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/go-errors/errors"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/base/data/securekv"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	libwallet "github.com/muun/libwallet/legacy"
)

const testUserKeyPath = "m/schema:1'/recovery:1'"

func TestWithUserPrivateKey(t *testing.T) {
	network := libwallet.Regtest()
	expected, err := hd_keys.NewHDPrivateKeyFromSeed(
		bytes.Repeat([]byte{0x42}, 32), network.ToParams(),
	)
	require.NoError(t, err)

	t.Run("reads the key and path, then wipes the value", func(t *testing.T) {
		store := newFakeSecureKeyValueStore(map[string][]byte{
			apolloUserKeyStorageKey: []byte(expected.Base58()),
		})
		provider := newTestKeyProvider(&fakeKeyBridge{path: testUserKeyPath}, store, network)

		err := provider.WithUserPrivateKey(func(key *hd_keys.HDPrivateKey) error {
			require.Equal(t, expected.Base58(), key.Base58())
			require.Equal(t, testUserKeyPath, key.Path())
			require.Equal(t, make([]byte, len(store.lastGet.Value)), store.lastGet.Value,
				"secure storage value survived the parse")
			return nil
		})

		require.NoError(t, err)
		require.Equal(t, make([]byte, len(store.lastGet.Value)), store.lastGet.Value,
			"secure storage value survived the scope")
	})

	t.Run("wipes a key that escapes fn", func(t *testing.T) {
		store := newFakeSecureKeyValueStore(map[string][]byte{
			apolloUserKeyStorageKey: []byte(expected.Base58()),
		})
		provider := newTestKeyProvider(&fakeKeyBridge{path: testUserKeyPath}, store, network)
		var escaped *hdkeychain.ExtendedKey

		err := provider.WithUserPrivateKey(func(key *hd_keys.HDPrivateKey) error {
			escaped = key.ExtendedKey()
			return nil
		})

		require.NoError(t, err)
		escapedPrivateKey, err := escaped.ECPrivKey()
		require.NoError(t, err)
		require.True(t, escapedPrivateKey.Key.IsZero(), "private key survived the scope")
	})

	t.Run("returns fn's error as is", func(t *testing.T) {
		store := newFakeSecureKeyValueStore(map[string][]byte{
			apolloUserKeyStorageKey: []byte(expected.Base58()),
		})
		provider := newTestKeyProvider(&fakeKeyBridge{path: testUserKeyPath}, store, network)
		fnErr := errors.New("fn failed")

		err := provider.WithUserPrivateKey(func(*hd_keys.HDPrivateKey) error {
			return fnErr
		})

		require.Equal(t, fnErr, err)
	})

	t.Run("fails without calling fn when the stored key doesn't parse", func(t *testing.T) {
		store := newFakeSecureKeyValueStore(map[string][]byte{
			apolloUserKeyStorageKey: []byte(expected.PublicKey().String()),
		})
		provider := newTestKeyProvider(&fakeKeyBridge{path: testUserKeyPath}, store, network)

		err := provider.WithUserPrivateKey(failIfCalled(t))

		require.ErrorContains(t, err, "read user key")
		require.Equal(t, make([]byte, len(store.lastGet.Value)), store.lastGet.Value,
			"secure storage value survived the scope")
	})

	t.Run("surfaces a missing key as NotFound", func(t *testing.T) {
		store := newFakeSecureKeyValueStore(map[string][]byte{})
		provider := newTestKeyProvider(&fakeKeyBridge{path: testUserKeyPath}, store, network)

		err := provider.WithUserPrivateKey(failIfCalled(t))

		var notFound *securekv.NotFoundError
		require.ErrorAs(t, err, &notFound)
		require.ErrorContains(t, err, "read user key")
	})

	t.Run("fails before touching secure storage when the path fails", func(t *testing.T) {
		store := newFakeSecureKeyValueStore(map[string][]byte{
			apolloUserKeyStorageKey: []byte(expected.Base58()),
		})
		pathErr := errors.New("no path")
		provider := newTestKeyProvider(&fakeKeyBridge{pathErr: pathErr}, store, network)

		err := provider.WithUserPrivateKey(failIfCalled(t))

		require.ErrorIs(t, err, pathErr)
		require.Nil(t, store.lastGet, "secure storage was read")
	})

}

func TestUserPublicKey(t *testing.T) {
	network := libwallet.Regtest()
	expected, err := hd_keys.NewHDPrivateKeyFromSeed(
		bytes.Repeat([]byte{0x42}, 32), network.ToParams(),
	)
	require.NoError(t, err)
	// Without values, a read of the private key would fail the test.
	store := newFakeSecureKeyValueStore(map[string][]byte{})
	bridge := &fakeKeyBridge{publicKey: &app_provided_data.KeyData{
		Serialized: expected.PublicKey().String(),
		Path:       testUserKeyPath,
	}}
	provider := newTestKeyProvider(bridge, store, network)

	publicKey, err := provider.UserPublicKey()

	require.NoError(t, err)
	require.Equal(t, expected.PublicKey().String(), publicKey.String())
	require.Equal(t, testUserKeyPath, publicKey.Path())
	require.Nil(t, store.lastGet, "the private key was read")
}

func newTestKeyProvider(
	bridge app_provided_data.KeyProvider,
	store *fakeSecureKeyValueStore,
	network *libwallet.Network,
) KeyProvider {
	return NewKeyProvider(bridge, securekv.NewSecureKeyValueStore(store), *network)
}

func failIfCalled(t *testing.T) func(*hd_keys.HDPrivateKey) error {
	t.Helper()
	return func(*hd_keys.HDPrivateKey) error {
		t.Helper()
		t.Fatal("fn must not be called")
		return nil
	}
}
