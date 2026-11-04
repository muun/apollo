package hd_keys

import (
	"crypto/sha256"
	"encoding/binary"
	"fmt"
	"io"
	"log/slog"
	"strings"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/hdpath"
	"github.com/muun/libwallet/platform/preconditions"
	"github.com/muun/libwallet/platform/secret"
)

// HDPrivateKey is a BIP32 private key bound to the derivation path it sits at
// and to its network.
type HDPrivateKey struct {
	key     hdkeychain.ExtendedKey
	network *chaincfg.Params
	path    string
}

// NewHDPrivateKey binds an already-parsed extended private key to the path it
// sits at. Panics if key is public.
//
// The result takes over key's memory: Zero on it wipes key too.
func NewHDPrivateKey(
	key *hdkeychain.ExtendedKey,
	path string,
	network *chaincfg.Params,
) *HDPrivateKey {
	preconditions.CheckStatef(key.IsPrivate(), "key at %v is not a private key", path)

	return &HDPrivateKey{key: *key, network: network, path: path}
}

// NewHDPrivateKeyFromSeed builds a master HD priv key from a seed for a given
// network.
func NewHDPrivateKeyFromSeed(seed []byte, network *chaincfg.Params) (*HDPrivateKey, error) {
	key, err := hdkeychain.NewMaster(seed, network)
	if err != nil {
		return nil, err
	}

	return &HDPrivateKey{key: *key, network: network, path: "m"}, nil
}

// NewMasterHDPrivateKeyFromBytes builds an HD priv key from the compress priv
// and chain code for a given network. The data is assumed to correspond to a
// master key.
//
// The result takes over rawKey and chainCode: Zero on it wipes them too.
func NewMasterHDPrivateKeyFromBytes(
	rawKey, chainCode []byte,
	network *chaincfg.Params,
) *HDPrivateKey {
	parentFP := []byte{0, 0, 0, 0}
	key := hdkeychain.NewExtendedKey(network.HDPrivateKeyID[:],
		rawKey, chainCode, parentFP, 0, 0, true)

	return &HDPrivateKey{key: *key, network: network, path: "m"}
}

// NewBasePathHDPrivateKeyFromBytes builds an HD priv key from the compress
// priv and chain code for a given network.
// The data is assumed to correspond to a key derived at SchemaBasePath.
//
// The result takes over rawKey and chainCode: Zero on it wipes them too.
func NewBasePathHDPrivateKeyFromBytes(
	rawKey, chainCode []byte,
	network *chaincfg.Params,
) *HDPrivateKey {
	basePath := SchemaBasePath
	var depth uint8 = 2
	var childNum uint32 = hdkeychain.HardenedKeyStart + 1
	isPrivate := true
	parentFP := []byte{0, 0, 0, 0}
	key := hdkeychain.NewExtendedKey(network.HDPrivateKeyID[:],
		rawKey, chainCode, parentFP, depth, childNum, isPrivate)

	return &HDPrivateKey{key: *key, network: network, path: basePath}
}

// NewHDPrivateKeyFromString parses a base58-encoded extended private key
// sitting at path. Errors if the encoded key is public.
func NewHDPrivateKeyFromString(
	base58, path string,
	network *chaincfg.Params,
) (*HDPrivateKey, error) {
	key, err := hdkeychain.NewKeyFromString(base58)
	if err != nil {
		return nil, err
	}

	if !key.IsPrivate() {
		return nil, errors.New("encoded key was not a private key")
	}

	return &HDPrivateKey{key: *key, network: network, path: path}, nil
}

// Path returns the derivation path the key sits at.
func (k *HDPrivateKey) Path() string {
	return k.path
}

// Network returns the network the key belongs to.
func (k *HDPrivateKey) Network() *chaincfg.Params {
	return k.network
}

// ExtendedKey returns a shallow copy of the backing BIP32 extended key. It
// shares the key's memory, so Zero wipes it too: don't keep it past the key.
func (k *HDPrivateKey) ExtendedKey() *hdkeychain.ExtendedKey {
	key := k.key
	return &key
}

// PublicKey returns the matching pub key, in memory of its own so it outlives
// Zero on this key.
func (k *HDPrivateKey) PublicKey() *HDPublicKey {
	neutered, err := k.key.Neuter()
	if err != nil {
		panic("original key was invalid")
	}

	// Neuter shares the chain code, parent fingerprint and memoized public key
	// with this key, which Zero wipes. None of it is secret, so copy it out.
	pubKey, err := neutered.ECPubKey()
	if err != nil {
		panic("original key was invalid")
	}
	key := hdkeychain.NewExtendedKey(
		neutered.Version(),
		pubKey.SerializeCompressed(),
		neutered.ChainCode(),
		binary.BigEndian.AppendUint32(nil, neutered.ParentFingerprint()),
		neutered.Depth(),
		neutered.ChildIndex(),
		false,
	)

	return &HDPublicKey{key: *key, network: k.network, path: k.path}
}

// Base58 returns the key as a base58-encoded BIP32 extended private key.
func (k *HDPrivateKey) Base58() string {
	return k.key.String()
}

// String returns the type name and secret.Placeholder. Base58 returns the key itself.
func (k *HDPrivateKey) String() string {
	return k.LogValue().String()
}

// ECPrivKey converts the hd private key to a btcec private key and returns it.
//
// TODO: btcutil's ECPrivKey also computes the public key, which leaves an
// unzeroed copy of the private scalar on the stack. Zeroing it needs a change
// in btcutil.
func (k *HDPrivateKey) ECPrivKey() (*btcec.PrivateKey, error) {
	return k.key.ECPrivKey()
}

// ChainCode returns the key's BIP32 chain code.
func (k *HDPrivateKey) ChainCode() []byte {
	return k.key.ChainCode()
}

// DerivedAt derives the child priv key at index, which may be hardened.
// index should be uint32 but for java compat we use int64.
func (k *HDPrivateKey) DerivedAt(index int64, hardened bool) (*HDPrivateKey, error) {
	if index&hdkeychain.HardenedKeyStart != 0 {
		return nil, errors.Errorf("index should not be hardened (index %v)", index)
	}
	if index < 0 || index > int64(hdkeychain.HardenedKeyStart) {
		return nil, errors.Errorf("index is out of bounds (index %v)", index)
	}

	var modifier uint32
	if hardened {
		modifier = hdkeychain.HardenedKeyStart
	}

	child, err := k.key.Derive(uint32(index) | modifier)
	if err != nil {
		return nil, err
	}

	parentPath, err := hdpath.Parse(k.path)
	if err != nil {
		return nil, err
	}
	path := parentPath.Child(uint32(index) | modifier)

	return &HDPrivateKey{key: *child, network: k.network, path: path.String()}, nil
}

// DeriveTo derives the key at path, which must extend the key's own path.
func (k *HDPrivateKey) DeriveTo(path string) (*HDPrivateKey, error) {
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
	if len(indexes) == 0 {
		return k.clone(path)
	}

	derivedKey := k
	for depth, index := range indexes {
		derivedKey, err = derivedKey.DerivedAt(int64(index.Index), index.Hardened)
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

// Deprecated: DeriveToPathWithHardenedBug derives the key up to the provided
// path, using a buggy derivation algorithm that causes hardened private key
// derivation to differ from the HD wallet BIP. We updated our logic to use
// the new version, but we still need this buggy algorithm for any keys that
// we derived before the fix.
// For information on the bug see ExtendedKey#DeriveNonStandard.
func (k *HDPrivateKey) DeriveToPathWithHardenedBug(path string) (*HDPrivateKey, error) {
	if !strings.HasPrefix(path, k.path) {
		return nil, errors.Errorf(
			"derivation path %v is not prefix of the keys path %v", path, k.path,
		)
	}

	firstPath, err := hdpath.Parse(k.path)
	if err != nil {
		return nil, errors.Errorf("couldn't parse derivation path %v: %w", k.path, err)
	}

	secondPath, err := hdpath.Parse(path)
	if err != nil {
		return nil, errors.Errorf("couldn't parse derivation path %v: %w", path, err)
	}

	levels := secondPath.IndexesFrom(firstPath)
	if len(levels) == 0 {
		return k.clone(path)
	}

	derivedKey := &k.key
	derivedKeyPath := firstPath

	for depth, level := range levels {

		var modifier uint32
		if level.Hardened {
			modifier = hdkeychain.HardenedKeyStart
		}

		index := level.Index | modifier
		derivedKeyPath = derivedKeyPath.Child(index)
		derivedKey, err = derivedKey.DeriveNonStandard(index)
		if err != nil {
			return nil, errors.Errorf(
				"failed to derive key at path %v on depth %v: %w",
				path,
				depth,
				err,
			)
		}
	}

	return &HDPrivateKey{key: *derivedKey, network: k.network, path: path}, nil
}

// clone returns this key at path, in memory of its own: Zero on either one
// leaves the other intact. The caller owns the copy and has to Zero it.
//
// TODO: no caller zeroes these copies yet, same as keys derived to a deeper
// path.
func (k *HDPrivateKey) clone(path string) (*HDPrivateKey, error) {
	privateKey, err := k.key.ECPrivKey()
	if err != nil {
		return nil, err
	}
	defer privateKey.Zero()

	key := hdkeychain.NewExtendedKey(
		append([]byte{}, k.key.Version()...),
		privateKey.Serialize(),
		k.key.ChainCode(),
		binary.BigEndian.AppendUint32(nil, k.key.ParentFingerprint()),
		k.key.Depth(),
		k.key.ChildIndex(),
		true,
	)

	return &HDPrivateKey{key: *key, network: k.network, path: path}, nil
}

// Sign signs a payload using the backing EC key.
func (k *HDPrivateKey) Sign(data []byte) ([]byte, error) {
	signingKey, err := k.key.ECPrivKey()
	if err != nil {
		return nil, err
	}

	hash := sha256.Sum256(data)
	sig := ecdsa.Sign(signingKey, hash[:])

	return sig.Serialize(), nil
}

// Zero wipes the key's private bytes, chain code, parent fingerprint and
// memoized public key, and leaves the key unusable.
//
// It also wipes whatever shares that memory, which stays well-formed instead
// of failing: copies from ExtendedKey sign with a zero scalar, and so do the
// buffers handed to NewHDPrivateKey, NewMasterHDPrivateKeyFromBytes and
// NewBasePathHDPrivateKeyFromBytes. Public keys and derived keys, including
// derivations to the key's own path, have memory of their own and survive it.
func (k *HDPrivateKey) Zero() {
	k.key.Zero()
}

// Format prints LogValue for every verb.
// Format and LogValue take value receivers, unlike the other methods, so values print
// redacted too, not only pointers.
func (k HDPrivateKey) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, k.LogValue().String())
}

// LogValue returns the type name and secret.Placeholder.
func (k HDPrivateKey) LogValue() slog.Value {
	return slog.StringValue("HDPrivateKey(" + secret.Placeholder + ")")
}
