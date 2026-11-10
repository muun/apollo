package adapted

import (
	"context"
	"runtime"
	"unsafe"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/base/data/securekv"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	libwallet "github.com/muun/libwallet/legacy"
)

// TODO: Apollo and Falcon store the user key under different names. Unify them
// on the native side and drop the platform branch in userKeyStorageKey.
const (
	apolloUserKeyStorageKey = "key_base_58_private_key"
	falconUserKeyStorageKey = "privateKey"
)

type KeyProvider interface {
	// WithUserPrivateKey fetches the user key fresh from secure storage on
	// every call and wipes it when fn returns, so neither the key nor anything
	// sharing its memory may escape fn.
	//
	// It is not copy-free. Parsing leaves short-lived copies of the key in
	// btcutil's big.Int temporaries until the GC reuses that memory, and keys
	// derived inside fn are separate allocations nobody wipes.
	WithUserPrivateKey(fn func(*hd_keys.HDPrivateKey) error) error
	UserPublicKey() (*hd_keys.HDPublicKey, error)
	CosignerPublicKey() (*hd_keys.HDPublicKey, error)
	EncryptedCosignerPrivateKey() (
		*libwallet.EncryptedPrivateKeyInfo, error,
	)
	MaxDerivedIndex() int
}

type keyProvider struct {
	bridge              app_provided_data.KeyProvider
	secureKeyValueStore securekv.SecureKeyValueStore
	network             libwallet.Network
}

func NewKeyProvider(
	bridge app_provided_data.KeyProvider,
	secureKeyValueStore securekv.SecureKeyValueStore,
	network libwallet.Network,
) KeyProvider {
	return &keyProvider{
		bridge:              bridge,
		secureKeyValueStore: secureKeyValueStore,
		network:             network,
	}
}

func (p *keyProvider) WithUserPrivateKey(fn func(*hd_keys.HDPrivateKey) error) error {
	path, err := p.bridge.FetchUserKeyPath()
	if err != nil {
		return errors.Errorf("fetch user key path: %w", err)
	}

	// TODO: take a ctx once the KeyProvider callers have one to pass.
	secret, err := p.secureKeyValueStore.Get(context.Background(), p.userKeyStorageKey())
	if err != nil {
		return errors.Errorf("read user key: %w", err)
	}

	// fn's error goes back as is; only failures reading the key get wrapped.
	var fnErr error
	err = secret.WithSecret(func(base58 []byte) error {
		// unsafe.String aliases the bytes WithSecret wipes, so the base58 key is
		// never copied into a string nobody can zero. It's safe because the
		// parser keeps no reference to the string.
		//
		// The parse itself still leaves copies we can't zero: base58.Decode
		// builds the whole serialized key in big.Int words and copies it out
		// through answer.Bytes(), the range check copies the private key into
		// another big.Int, and the checksum hash buffers the last 14 bytes of
		// the private key in sha256's digest. They stay in memory until the GC
		// or the stack reuses it. If the parse fails, there is no key to Zero,
		// so the decoded buffer is left unwiped too.
		encoded := unsafe.String(&base58[0], len(base58)) //nolint:gosec // see above
		key, err := hd_keys.NewHDPrivateKeyFromString(encoded, path, p.network.ToParams())
		// The parsed key doesn't share memory with base58, so wipe it now
		// instead of after fn. encoded aliases it and is dead from here on.
		clear(base58)
		if err != nil {
			return err
		}
		// The parsed key shares memory with the decoded buffer, so this wipes
		// both, along with anything else that shares it.
		defer key.Zero()

		fnErr = fn(key)
		return fnErr
	})
	if fnErr != nil {
		return fnErr
	}
	if err != nil {
		return errors.Errorf("read user key: %w", err)
	}
	return nil
}

func (p *keyProvider) UserPublicKey() (
	*hd_keys.HDPublicKey, error,
) {
	data, err := p.bridge.FetchUserPublicKey()
	if err != nil {
		return nil, err
	}
	return hd_keys.NewHDPublicKeyFromString(
		data.Serialized, data.Path, p.network.ToParams(),
	)
}

func (p *keyProvider) CosignerPublicKey() (
	*hd_keys.HDPublicKey, error,
) {
	data, err := p.bridge.FetchCosignerKey()
	if err != nil {
		return nil, err
	}
	return hd_keys.NewHDPublicKeyFromString(
		data.Serialized, data.Path, p.network.ToParams(),
	)
}

func (p *keyProvider) EncryptedCosignerPrivateKey() (
	*libwallet.EncryptedPrivateKeyInfo, error,
) {
	encoded, err := p.bridge.FetchEncryptedCosignerPrivateKey()
	if err != nil {
		return nil, err
	}
	return libwallet.DecodeEncryptedPrivateKey(encoded)
}

func (p *keyProvider) MaxDerivedIndex() int {
	return p.bridge.FetchMaxDerivedIndex()
}

func (p *keyProvider) userKeyStorageKey() string {
	if runtime.GOOS == "ios" {
		return falconUserKeyStorageKey
	}
	return apolloUserKeyStorageKey
}
