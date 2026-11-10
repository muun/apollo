package libwallet

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
)

// HDPublicKey is an HD capable pub key.
// It is a gomobile facade: the implementation lives in the cryptography/hd_keys package,
// which Go code should use directly.
type HDPublicKey struct {
	key     hdkeychain.ExtendedKey
	Network *Network
	Path    string
}

// NewHDPublicKeyFromString creates an HD pub key from a base58-encoded string
// If the parsed key is private, it returns an error
func NewHDPublicKeyFromString(str, path string, network *Network) (*HDPublicKey, error) {
	key, err := hd_keys.NewHDPublicKeyFromString(str, path, network.network)
	if err != nil {
		return nil, err
	}

	return NewHDPublicKeyFromModel(key), nil
}

// ToModel builds the hd_keys model equivalent of this facade.
func (p *HDPublicKey) ToModel() *hd_keys.HDPublicKey {
	return hd_keys.NewHDPublicKey(&p.key, p.Path, p.Network.network)
}

// NewHDPublicKeyFromModel wraps a hd_keys model back into the facade.
func NewHDPublicKeyFromModel(key *hd_keys.HDPublicKey) *HDPublicKey {
	return &HDPublicKey{
		key:     *key.ExtendedKey(),
		Network: &Network{network: key.Network()},
		Path:    key.Path(),
	}
}

// String return the key base58-encoded
func (p *HDPublicKey) String() string {
	return p.key.String()
}

// DerivedAt derives a new child pub key
// index should be uint32 but for java compat we use int64
func (p *HDPublicKey) DerivedAt(index int64) (*HDPublicKey, error) {
	derived, err := p.ToModel().DerivedAt(index)
	if err != nil {
		return nil, err
	}

	return NewHDPublicKeyFromModel(derived), nil
}

func (p *HDPublicKey) DeriveTo(path string) (*HDPublicKey, error) {
	derived, err := p.ToModel().DeriveTo(path)
	if err != nil {
		return nil, err
	}

	return NewHDPublicKeyFromModel(derived), nil
}

// Raw returns the backing EC compressed raw key
func (p *HDPublicKey) Raw() []byte {
	return p.ToModel().Raw()
}

// Fingerprint returns the 4-byte fingerprint for this pubkey
func (p *HDPublicKey) Fingerprint() []byte {
	return p.ToModel().Fingerprint()
}

// ECPubKey converts the hd public key to a btcec public key and returns it.
func (p *HDPublicKey) ECPubKey() (*btcec.PublicKey, error) {
	return p.key.ECPubKey()
}

func (p *HDPublicKey) ChainCode() []byte {
	return p.key.ChainCode()
}
