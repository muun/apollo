package hd_keys

import (
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/platform/preconditions"
)

// PublicKeyTriple is the (user, cosigner, lightningServer) public key set of
// one output. Constructing one guarantees all three keys sit at the same
// derivation path on the same network.
type PublicKeyTriple struct {
	user            *HDPublicKey
	cosigner        *HDPublicKey
	lightningServer *HDPublicKey
}

// NewPublicKeyTriple builds a PublicKeyTriple. Panics if any key is nil or
// the keys don't sit at the same derivation path on the same network.
func NewPublicKeyTriple(user, cosigner, lightningServer *HDPublicKey) *PublicKeyTriple {
	preconditions.CheckNotNil(user)
	preconditions.CheckNotNil(cosigner)
	preconditions.CheckNotNil(lightningServer)
	preconditions.CheckStatef(
		user.Path() == cosigner.Path() && user.Path() == lightningServer.Path(),
		"keys have mismatching derivation paths: user %v, cosigner %v, lightning server %v",
		user.Path(), cosigner.Path(), lightningServer.Path(),
	)
	preconditions.CheckStatef(
		user.Network().Name == cosigner.Network().Name &&
			user.Network().Name == lightningServer.Network().Name,
		"keys have mismatching networks: user %v, cosigner %v, lightning server %v",
		user.Network().Name, cosigner.Network().Name, lightningServer.Network().Name,
	)

	return &PublicKeyTriple{
		user:            user,
		cosigner:        cosigner,
		lightningServer: lightningServer,
	}
}

// DeriveTo derives every key of the triple to path.
func (t *PublicKeyTriple) DeriveTo(path string) (*PublicKeyTriple, error) {
	user, err := t.user.DeriveTo(path)
	if err != nil {
		return nil, errors.Errorf("derive user key: %w", err)
	}
	cosigner, err := t.cosigner.DeriveTo(path)
	if err != nil {
		return nil, errors.Errorf("derive cosigner key: %w", err)
	}
	lightningServer, err := t.lightningServer.DeriveTo(path)
	if err != nil {
		return nil, errors.Errorf("derive lightning server key: %w", err)
	}

	return &PublicKeyTriple{
		user:            user,
		cosigner:        cosigner,
		lightningServer: lightningServer,
	}, nil
}

// UserPublicKey returns the user's key of the triple.
func (t *PublicKeyTriple) UserPublicKey() *HDPublicKey {
	return t.user
}

// CosignerPublicKey returns the cosigner's key of the triple.
func (t *PublicKeyTriple) CosignerPublicKey() *HDPublicKey {
	return t.cosigner
}

// LightningServerPublicKey returns the lightning server's key of the triple.
func (t *PublicKeyTriple) LightningServerPublicKey() *HDPublicKey {
	return t.lightningServer
}

// Path returns the derivation path every key of the triple sits at.
func (t *PublicKeyTriple) Path() string {
	return t.user.Path()
}

// Network returns the network every key of the triple belongs to.
func (t *PublicKeyTriple) Network() *chaincfg.Params {
	return t.user.Network()
}
