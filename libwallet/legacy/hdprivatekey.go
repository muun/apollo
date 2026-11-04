package libwallet

import (
	"fmt"
	"io"
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/legacy/encryption"
	"github.com/muun/libwallet/platform/secret"
)

// HDPrivateKey is an HD capable priv key.
// It is a gomobile facade: the implementation lives in the cryptography/hd_keys package,
// which Go code should use directly.
type HDPrivateKey struct {
	key     hdkeychain.ExtendedKey
	Network *Network
	Path    string
}

// NewHDPrivateKey builds an HD priv key from a seed for a given network
func NewHDPrivateKey(seed []byte, network *Network) (*HDPrivateKey, error) {
	key, err := hd_keys.NewHDPrivateKeyFromSeed(seed, network.network)
	if err != nil {
		return nil, err
	}

	return NewHDPrivateKeyFromModel(key), nil
}

// NewMasterHDPrivateKeyFromBytes builds an HD priv key from the compress priv and chain code for a
// given network The data is assumed to correspond to a master key.
func NewMasterHDPrivateKeyFromBytes(
	rawKey, chainCode []byte,
	network *Network,
) (*HDPrivateKey, error) {
	key := hd_keys.NewMasterHDPrivateKeyFromBytes(rawKey, chainCode, network.network)
	return NewHDPrivateKeyFromModel(key), nil
}

// NewBasePathHDPrivateKeyFromBytes builds an HD priv key from the compress
// priv and chain code for a given network.
// The data is assumed to correspond to a key derived at our usual base path
// "m/schema:1'/recovery:1'".
func NewBasePathHDPrivateKeyFromBytes(
	rawKey, chainCode []byte,
	network *Network,
) (*HDPrivateKey, error) {
	key := hd_keys.NewBasePathHDPrivateKeyFromBytes(rawKey, chainCode, network.network)
	return NewHDPrivateKeyFromModel(key), nil
}

// NewHDPrivateKeyFromString creates an HD priv key from a base58-encoded string
// If the parsed key is public, it returns an error
func NewHDPrivateKeyFromString(str, path string, network *Network) (*HDPrivateKey, error) {
	key, err := hd_keys.NewHDPrivateKeyFromString(str, path, network.network)
	if err != nil {
		return nil, err
	}

	return NewHDPrivateKeyFromModel(key), nil
}

// ToModel builds the hd_keys model equivalent of this facade.
func (p *HDPrivateKey) ToModel() *hd_keys.HDPrivateKey {
	return hd_keys.NewHDPrivateKey(&p.key, p.Path, p.Network.network)
}

// NewHDPrivateKeyFromModel wraps a hd_keys model back into the facade.
func NewHDPrivateKeyFromModel(key *hd_keys.HDPrivateKey) *HDPrivateKey {
	return &HDPrivateKey{
		key:     *key.ExtendedKey(),
		Network: &Network{network: key.Network()},
		Path:    key.Path(),
	}
}

// PublicKey returns the matching pub key
func (p *HDPrivateKey) PublicKey() *HDPublicKey {
	return NewHDPublicKeyFromModel(p.ToModel().PublicKey())
}

// Base58 returns the key as a base58-encoded BIP32 extended private key.
func (p *HDPrivateKey) Base58() string {
	return p.key.String()
}

// String returns the type name and secret.Placeholder. Base58 returns the key itself.
func (p *HDPrivateKey) String() string {
	return p.LogValue().String()
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
	derived, err := p.ToModel().DerivedAt(index, hardened)
	if err != nil {
		return nil, err
	}

	return NewHDPrivateKeyFromModel(derived), nil
}

func (p *HDPrivateKey) DeriveTo(path string) (*HDPrivateKey, error) {
	derived, err := p.ToModel().DeriveTo(path)
	if err != nil {
		return nil, err
	}

	return NewHDPrivateKeyFromModel(derived), nil
}

// Sign a payload using the backing EC key
func (p *HDPrivateKey) Sign(data []byte) ([]byte, error) {
	return p.ToModel().Sign(data)
}

type keyProvider struct {
	key *hd_keys.HDPrivateKey
}

func (k keyProvider) WithPath(path string) (*btcec.PrivateKey, error) {
	derivedKey, err := k.key.DeriveTo(path)
	if err != nil {
		return nil, err
	}

	return derivedKey.ECPrivKey()
}

func (k keyProvider) WithPathUsingHardenedBug(path string) (*btcec.PrivateKey, error) {
	//nolint:staticcheck // we must keep serving keys derived before the fix
	derivedKey, err := k.key.DeriveToPathWithHardenedBug(path)
	if err != nil {
		return nil, err
	}

	return derivedKey.ECPrivKey()
}

func (k keyProvider) Path() string {
	return k.key.Path()
}

func (p *HDPrivateKey) Decrypter() Decrypter {
	return &encryption.HdPrivKeyDecrypter{
		KeyProvider: keyProvider{key: p.ToModel()},
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
		KeyProvider: keyProvider{key: p.ToModel()},
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

// Format prints LogValue for every verb.
// Format and LogValue take value receivers, unlike the other methods, so values print
// redacted too, not only pointers.
func (p HDPrivateKey) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, p.LogValue().String())
}

// LogValue returns the type name and secret.Placeholder.
func (p HDPrivateKey) LogValue() slog.Value {
	return slog.StringValue("HDPrivateKey(" + secret.Placeholder + ")")
}

// What follows is a workaround for https://github.com/golang/go/issues/46893

func SignWithPrivateKey(key *HDPrivateKey, data []byte) ([]byte, error) {
	return key.Sign(data)
}
