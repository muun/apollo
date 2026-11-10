// Package hd_keys holds the HD key models shared by the wallet: BIP32 extended
// keys bound to the derivation path they sit at and to their network. The
// root libwallet HDPublicKey and HDPrivateKey types are gomobile facades
// delegating here; Go code should use this package directly.
package hd_keys

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/hdpath"
	"github.com/muun/libwallet/platform/preconditions"
)

// HDPublicKey is a BIP32 public key bound to the derivation path it sits at
// and to its network.
type HDPublicKey struct {
	key     hdkeychain.ExtendedKey
	network *chaincfg.Params
	path    string
}

// NewHDPublicKey binds an already-parsed extended public key to the path it
// sits at. Panics if key is private.
func NewHDPublicKey(
	key *hdkeychain.ExtendedKey,
	path string,
	network *chaincfg.Params,
) *HDPublicKey {
	preconditions.CheckStatef(!key.IsPrivate(), "key at %v is not a public key", path)

	return &HDPublicKey{key: *key, network: network, path: path}
}

// NewHDPublicKeyFromString parses a base58-encoded extended public key
// sitting at path. Errors if the encoded key is private.
func NewHDPublicKeyFromString(
	base58, path string,
	network *chaincfg.Params,
) (*HDPublicKey, error) {
	key, err := hdkeychain.NewKeyFromString(base58)
	if err != nil {
		return nil, err
	}

	if key.IsPrivate() {
		return nil, errors.New("encoded key was not a public key")
	}

	return &HDPublicKey{key: *key, network: network, path: path}, nil
}

// Path returns the derivation path the key sits at.
func (k *HDPublicKey) Path() string {
	return k.path
}

// Network returns the network the key belongs to.
func (k *HDPublicKey) Network() *chaincfg.Params {
	return k.network
}

// ExtendedKey returns a copy of the backing BIP32 extended key.
func (k *HDPublicKey) ExtendedKey() *hdkeychain.ExtendedKey {
	key := k.key
	return &key
}

// String returns the key base58-encoded.
func (k *HDPublicKey) String() string {
	return k.key.String()
}

// DerivedAt derives the child pub key at index.
// index should be uint32 but for java compat we use int64.
func (k *HDPublicKey) DerivedAt(index int64) (*HDPublicKey, error) {
	if index&hdkeychain.HardenedKeyStart != 0 {
		return nil, errors.Errorf("can't derive a hardened pub key (index %v)", index)
	}
	if index < 0 || index > int64(hdkeychain.HardenedKeyStart) {
		return nil, errors.Errorf("index is out of bounds (index %v)", index)
	}

	child, err := k.key.Derive(uint32(index))
	if err != nil {
		return nil, err
	}

	parentPath, err := hdpath.Parse(k.path)
	if err != nil {
		return nil, err
	}
	path := parentPath.Child(uint32(index))

	return &HDPublicKey{key: *child, network: k.network, path: path.String()}, nil
}

// DeriveTo derives the key at path, which must extend the key's own path
// with non-hardened steps.
func (k *HDPublicKey) DeriveTo(path string) (*HDPublicKey, error) {
	firstPath, err := hdpath.Parse(k.path)
	if err != nil {
		return nil, errors.Errorf("couldn't parse derivation path %v: %w", k.path, err)
	}

	secondPath, err := hdpath.Parse(path)
	if err != nil {
		return nil, errors.Errorf("couldn't parse derivation path %v: %w", path, err)
	}

	if !secondPath.HasPrefix(firstPath) {
		return nil, errors.Errorf(
			"derivation path %v is not prefix of the keys path %v", path, k.path,
		)
	}

	indexes := secondPath.IndexesFrom(firstPath)
	derivedKey := k
	for depth, index := range indexes {
		if index.Hardened {
			return nil, errors.Errorf("can't derive a hardened pub key (path %v)", path)
		}

		derivedKey, err = derivedKey.DerivedAt(int64(index.Index))
		if err != nil {
			return nil, errors.Errorf(
				"failed to derive key at path %v on depth %v: %w",
				path,
				depth,
				err,
			)
		}
	}
	// The generated path has no names in it, so replace it.
	derivedKey.path = path

	return derivedKey, nil
}

// Raw returns the backing EC compressed raw key.
func (k *HDPublicKey) Raw() []byte {
	key, err := k.key.ECPubKey()
	if err != nil {
		panic("failed to extract pub key")
	}

	return key.SerializeCompressed()
}

// Fingerprint returns the 4-byte fingerprint for this pubkey.
func (k *HDPublicKey) Fingerprint() []byte {
	key, err := k.key.ECPubKey()
	if err != nil {
		panic("failed to extract pub key")
	}

	bytes := key.SerializeCompressed()
	hash := btcutil.Hash160(bytes)

	return hash[:4]
}

// ECPubKey converts the hd public key to a btcec public key and returns it.
func (k *HDPublicKey) ECPubKey() (*btcec.PublicKey, error) {
	return k.key.ECPubKey()
}

// ChainCode returns the key's BIP32 chain code.
func (k *HDPublicKey) ChainCode() []byte {
	return k.key.ChainCode()
}
