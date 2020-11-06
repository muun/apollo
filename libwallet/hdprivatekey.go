package libwallet

import (
	"crypto/sha256"
	"strings"

	"github.com/muun/libwallet/hdpath"
	"github.com/pkg/errors"

	"github.com/btcsuite/btcutil/hdkeychain"
)

// HDPrivateKey is an HD capable priv key
type HDPrivateKey struct {
	key     hdkeychain.ExtendedKey
	Network *Network
	Path    string
}

// NewHDPrivateKey builds an HD priv key from a seed for a given network
func NewHDPrivateKey(seed []byte, network *Network) (*HDPrivateKey, error) {

	key, err := hdkeychain.NewMaster(seed, network.network)
	if err != nil {
		return nil, err
	}

	return &HDPrivateKey{key: *key, Network: network, Path: "m"}, nil
}

// NewHDPrivateKeyFromBytes builds an HD priv key from the compress priv and chain code for a given network
func NewHDPrivateKeyFromBytes(rawKey, chainCode []byte, network *Network) (*HDPrivateKey, error) {

	parentFP := []byte{0, 0, 0, 0}
	key := hdkeychain.NewExtendedKey(network.network.HDPrivateKeyID[:],
		rawKey, chainCode, parentFP, 0, 0, true)

	return &HDPrivateKey{key: *key, Network: network, Path: "m"}, nil
}

// NewHDPrivateKeyFromString creates an HD priv key from a base58-encoded string
// If the parsed key is public, it returns an error
func NewHDPrivateKeyFromString(str, path string, network *Network) (*HDPrivateKey, error) {

	key, err := hdkeychain.NewKeyFromString(str)
	if err != nil {
		return nil, err
	}

	if !key.IsPrivate() {
		return nil, errors.New("encoded key was not a private key")
	}

	return &HDPrivateKey{key: *key, Network: network, Path: path}, nil
}

// PublicKey returns the matching pub key
func (p *HDPrivateKey) PublicKey() *HDPublicKey {

	key, err := p.key.Neuter()
	if err != nil {
		panic("original key was invalid")
	}

	return &HDPublicKey{key: *key, Network: p.Network, Path: p.Path}
}

// String return the key base58-encoded
func (p *HDPrivateKey) String() string {
	return p.key.String()
}

// DerivedAt derives a new child priv key, which may be hardened
// index should be uint32 but for java compat we use int64
func (p *HDPrivateKey) DerivedAt(index int64, hardened bool) (*HDPrivateKey, error) {
	var modifier uint32
	if hardened {
		modifier = hdkeychain.HardenedKeyStart
	}

	path := hdpath.MustParse(p.Path).Child(uint32(index) | modifier)

	child, err := p.key.Child(uint32(index) | modifier)
	if err != nil {
		return nil, err
	}

	return &HDPrivateKey{key: *child, Network: p.Network, Path: path.String()}, nil
}

func (p *HDPrivateKey) DeriveTo(path string) (*HDPrivateKey, error) {

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
		derivedKey, err = derivedKey.DerivedAt(int64(index.Index), index.Hardened)
		if err != nil {
			return nil, errors.Wrapf(err, "failed to derive key at path %v on depth %v", path, depth)
		}
	}
	// The generated path has no names in it, so replace it
	derivedKey.Path = path

	return derivedKey, nil
}

// Sign a payload using the backing EC key
func (p *HDPrivateKey) Sign(data []byte) ([]byte, error) {

	signingKey, err := p.key.ECPrivKey()
	if err != nil {
		return nil, err
	}

	hash := sha256.Sum256(data)
	sig, err := signingKey.Sign(hash[:])
	if err != nil {
		return nil, err
	}

	return sig.Serialize(), nil
}

func (p *HDPrivateKey) Decrypter() Decrypter {
	return &hdPrivKeyDecrypter{p, nil, true}
}

func (p *HDPrivateKey) DecrypterFrom(senderKey *PublicKey) Decrypter {
	return &hdPrivKeyDecrypter{p, senderKey, false}
}

func (p *HDPrivateKey) Encrypter() Encrypter {
	return &hdPubKeyEncrypter{p.PublicKey(), p}
}

func (p *HDPrivateKey) EncrypterTo(receiver *HDPublicKey) Encrypter {
	return &hdPubKeyEncrypter{receiver, p}
}
