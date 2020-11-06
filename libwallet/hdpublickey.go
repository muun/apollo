package libwallet

import (
	"strings"

	"github.com/muun/libwallet/hdpath"
	"github.com/pkg/errors"

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
		return nil, errors.Errorf("can't derive a hardened pub key (index %v)", index)
	}

	child, err := p.key.Child(uint32(index))
	if err != nil {
		return nil, err
	}

	path := hdpath.MustParse(p.Path).Child(uint32(index))
	return &HDPublicKey{key: *child, Network: p.Network, Path: path.String()}, nil
}

func (p *HDPublicKey) DeriveTo(path string) (*HDPublicKey, error) {

	if !strings.HasPrefix(path, p.Path) {
		return nil, errors.Errorf("derivation path %v is not prefix of the keys path %v", path, p.Path)
	}

	firstPath, err := hdpath.Parse(p.Path)
	if err != nil {
		return nil, errors.Wrapf(err, "couldn't parse derivation path %v", p.Path)
	}

	secondPath, err := hdpath.Parse(path)
	if err != nil {
		return nil, errors.Wrapf(err, "couldn't parse derivation path %v", path)
	}

	indexes := secondPath.IndexesFrom(firstPath)
	derivedKey := p
	for depth, index := range indexes {
		if index.Hardened {
			return nil, errors.Errorf("can't derive a hardened pub key (path %v)", path)
		}

		derivedKey, err = derivedKey.DerivedAt(int64(index.Index))
		if err != nil {
			return nil, errors.Wrapf(err, "failed to derive key at path %v on depth %v", path, depth)
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
