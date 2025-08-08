package libwallet

import (
	"crypto/sha256"
	"errors"
	"fmt"
	"strings"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet/encryption"
	"github.com/muun/libwallet/hdpath"

	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
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

func (p *HDPrivateKey) ECPrivateKey() (*btcec.PrivateKey, error) {
	return p.key.ECPrivKey()
}

func (p *HDPrivateKey) ChainCode() []byte {
	return p.key.ChainCode()
}

// DerivedAt derives a new child priv key, which may be hardened
// index should be uint32 but for java compat we use int64
func (p *HDPrivateKey) DerivedAt(index int64, hardened bool) (*HDPrivateKey, error) {
	if index&hdkeychain.HardenedKeyStart != 0 {
		return nil, fmt.Errorf("index should not be hardened (index %v)", index)
	}
	if index < 0 || index > int64(hdkeychain.HardenedKeyStart) {
		return nil, fmt.Errorf("index is out of bounds (index %v)", index)
	}

	var modifier uint32
	if hardened {
		modifier = hdkeychain.HardenedKeyStart
	}

	child, err := p.key.Derive(uint32(index) | modifier)
	if err != nil {
		return nil, err
	}

	parentPath, err := hdpath.Parse(p.Path)
	if err != nil {
		return nil, err
	}
	path := parentPath.Child(uint32(index) | modifier)

	return &HDPrivateKey{key: *child, Network: p.Network, Path: path.String()}, nil
}

func (p *HDPrivateKey) DeriveTo(path string) (*HDPrivateKey, error) {

	firstPath, err := hdpath.Parse(p.Path)
	if err != nil {
		return nil, fmt.Errorf("couldn't parse derivation path %v: %w", p.Path, err)
	}

	secondPath, err := hdpath.Parse(path)
	if err != nil {
		return nil, fmt.Errorf("couldn't parse derivation path %v: %w", path, err)
	}

	if !secondPath.HasPrefix(firstPath) {
		return nil, fmt.Errorf("derivation path %v is not prefix of the keys path %v", path, p.Path)
	}

	indexes := secondPath.IndexesFrom(firstPath)
	derivedKey := p
	for depth, index := range indexes {
		derivedKey, err = derivedKey.DerivedAt(int64(index.Index), index.Hardened)
		if err != nil {
			return nil, fmt.Errorf("failed to derive key at path %v on depth %v: %w", path, depth, err)
		}
	}
	// The generated path has no names in it, so replace it
	derivedKey.Path = path

	return derivedKey, nil
}

// Deprecated: deriveToPathWithHardenedBug derives the key up to the provided path, using a buggy
// derivation algorithm that causes hardened private key derivation to differ from the HD wallet
// BIP. We updated our logic to use the new version, but we still need this buggy algorithm for
// any keys that we derived before the fix.
// For information on the bug see ExtendedKey#DeriveNonStandard.
func (p *HDPrivateKey) deriveToPathWithHardenedBug(path string) (*HDPrivateKey, error) {

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

	derivedKey := &p.key
	derivedKeyPath := firstPath

	for depth, level := range secondPath.IndexesFrom(firstPath) {

		var modifier uint32
		if level.Hardened {
			modifier = hdkeychain.HardenedKeyStart
		}

		index := level.Index | modifier
		derivedKeyPath = derivedKeyPath.Child(index)
		derivedKey, err = derivedKey.DeriveNonStandard(index)
		if err != nil {
			return nil, fmt.Errorf("failed to derive key at path %v on depth %v: %w", path, depth, err)
		}
	}

	return &HDPrivateKey{
		key:     *derivedKey,
		Network: p.Network,
		Path:    path,
	}, nil
}

// Sign a payload using the backing EC key
func (p *HDPrivateKey) Sign(data []byte) ([]byte, error) {

	signingKey, err := p.key.ECPrivKey()
	if err != nil {
		return nil, err
	}

	hash := sha256.Sum256(data)
	sig := ecdsa.Sign(signingKey, hash[:])

	return sig.Serialize(), nil
}

type keyProvider struct {
	key *HDPrivateKey
}

func (k keyProvider) WithPath(path string) (*btcec.PrivateKey, error) {
	derivedKey, err := k.key.DeriveTo(path)
	if err != nil {
		return nil, err
	}

	return derivedKey.key.ECPrivKey()
}

func (k keyProvider) WithPathUsingHardenedBug(path string) (*btcec.PrivateKey, error) {
	derivedKey, err := k.key.deriveToPathWithHardenedBug(path)
	if err != nil {
		return nil, err
	}

	return derivedKey.key.ECPrivKey()
}

func (k keyProvider) Path() string {
	return k.key.Path
}

func (p *HDPrivateKey) Decrypter() Decrypter {
	return &encryption.HdPrivKeyDecrypter{
		KeyProvider: keyProvider{key: p},
		SenderKey:   nil,
		FromSelf:    true,
	}
}

func (p *HDPrivateKey) DecrypterFrom(sender *PublicKey) Decrypter {

	var senderKey *btcec.PublicKey
	if sender != nil {
		senderKey = sender.key
	}

	return &encryption.HdPrivKeyDecrypter{
		KeyProvider: keyProvider{key: p},
		SenderKey:   senderKey,
		FromSelf:    false,
	}
}

func (p *HDPrivateKey) Encrypter() Encrypter {
	key, err := p.key.ECPrivKey()
	if err != nil {
		panic(err)
	}

	return &encryption.HdPubKeyEncrypter{
		ReceiverKey:     key.PubKey(),
		ReceiverKeyPath: p.Path,
		SenderKey:       key,
	}
}

func (p *HDPrivateKey) EncrypterTo(receiver *HDPublicKey) Encrypter {
	key, err := p.key.ECPrivKey()
	if err != nil {
		panic(err)
	}

	var receiverKey *btcec.PublicKey
	if receiver != nil {
		receiverKey, err = receiver.key.ECPubKey()
		if err != nil {
			panic(err)
		}
	}

	return &encryption.HdPubKeyEncrypter{
		ReceiverKey:     receiverKey,
		ReceiverKeyPath: p.Path,
		SenderKey:       key,
	}
}

// What follows is a workaround for https://github.com/golang/go/issues/46893

func SignWithPrivateKey(key *HDPrivateKey, data []byte) ([]byte, error) {
	return key.Sign(data)
}
