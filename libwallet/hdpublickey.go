package libwallet

import (
	"errors"
	"fmt"
	"strings"

	"github.com/muun/libwallet/hdpath"

	"github.com/btcsuite/btcutil"
	"github.com/btcsuite/btcutil/hdkeychain"
)

// HDPublicKey is an HD capable pub key
type HDPublicKey struct {
	key     hdkeychain.ExtendedKey
	Network *Network
	Path    string
}

// NewHDPublicKeyFromString creates an HD pub key from a base58-encoded string
// If the parsed key is private, it returns an error
func NewHDPublicKeyFromString(str, path string, network *Network) (*HDPublicKey, error) {

	key, err := hdkeychain.NewKeyFromString(str)
	if err != nil {
		return nil, err
	}

	if key.IsPrivate() {
		return nil, errors.New("encoded key was not a public key")
	}

	return &HDPublicKey{key: *key, Network: network, Path: path}, nil
}

// String return the key base58-encoded
func (p *HDPublicKey) String() string {
	return p.key.String()
}

// DerivedAt derives a new child pub key
// index should be uint32 but for java compat we use int64
func (p *HDPublicKey) DerivedAt(index int64) (*HDPublicKey, error) {

	if index&hdkeychain.HardenedKeyStart != 0 {
		return nil, fmt.Errorf("can't derive a hardened pub key (index %v)", index)
	}

	child, err := p.key.Child(uint32(index))
	if err != nil {
		return nil, err
	}

	parentPath, err := hdpath.Parse(p.Path)
	if err != nil {
		return nil, err
	}
	path := parentPath.Child(uint32(index))

	return &HDPublicKey{key: *child, Network: p.Network, Path: path.String()}, nil
}

func (p *HDPublicKey) DeriveTo(path string) (*HDPublicKey, error) {

	if !strings.HasPrefix(path, p.Path) {
		return nil, fmt.Errorf("derivation path %v is not prefix of the keys path %v", path, p.Path)
	}

	firstPath, err := hdpath.Parse(p.Path)
	if err != nil {
		return nil, fmt.Errorf("couldn't parse derivation path %v: %w", p.Path, err)
	}

	secondPath, err := hdpath.Parse(path)
	if err != nil {
		return nil, fmt.Errorf("couldn't parse derivation path %v: %w", path, err)
	}

	indexes := secondPath.IndexesFrom(firstPath)
	derivedKey := p
	for depth, index := range indexes {
		if index.Hardened {
			return nil, fmt.Errorf("can't derive a hardened pub key (path %v)", path)
		}

		derivedKey, err = derivedKey.DerivedAt(int64(index.Index))
		if err != nil {
			return nil, fmt.Errorf("failed to derive key at path %v on depth %v: %w", path, depth, err)
		}
	}
	// The generated path has no names in it, so replace it
	derivedKey.Path = path

	return derivedKey, nil
}

// Raw returns the backing EC compressed raw key
func (p *HDPublicKey) Raw() []byte {

	key, err := p.key.ECPubKey()
	if err != nil {
		panic("failed to extract pub key")
	}

	return key.SerializeCompressed()
}

// Fingerprint returns the 4-byte fingerprint for this pubkey
func (p *HDPublicKey) Fingerprint() []byte {

	key, err := p.key.ECPubKey()
	if err != nil {
		panic("failed to extract pub key")
	}

	bytes := key.SerializeCompressed()
	hash := btcutil.Hash160(bytes)

	return hash[:4]
}
