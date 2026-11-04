package addresses

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
)

// participantECKeys holds the participant EC public keys of one output,
// derived at its derivation path.
type participantECKeys struct {
	userPubKey            *btcec.PublicKey
	cosignerPubKey        *btcec.PublicKey
	lightningServerPubKey *btcec.PublicKey
}

// deriveParticipantECKeys derives baseKeys to derivationPath and extracts
// every participant's EC public key. derivationPath must match
// m/schema:1'/recovery:1'/<change:0|external:1>/<index>.
func deriveParticipantECKeys(
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
) (*participantECKeys, error) {
	if err := validateAddressDerivationPath(derivationPath); err != nil {
		return nil, err
	}

	derivedKeys, err := baseKeys.DeriveTo(derivationPath)
	if err != nil {
		return nil, err
	}
	userPubKey, err := derivedKeys.UserPublicKey().ECPubKey()
	if err != nil {
		return nil, errors.Errorf("get user public key: %w", err)
	}
	cosignerPubKey, err := derivedKeys.CosignerPublicKey().ECPubKey()
	if err != nil {
		return nil, errors.Errorf("get cosigner public key: %w", err)
	}
	lightningServerPubKey, err := derivedKeys.LightningServerPublicKey().ECPubKey()
	if err != nil {
		return nil, errors.Errorf("get lightning server public key: %w", err)
	}

	return &participantECKeys{
		userPubKey:            userPubKey,
		cosignerPubKey:        cosignerPubKey,
		lightningServerPubKey: lightningServerPubKey,
	}, nil
}

// deriveSignerECKey derives privKey to path and returns its EC private key,
// checking it belongs to expectedPubKey.
func deriveSignerECKey(
	privKey *hd_keys.HDPrivateKey,
	path string,
	expectedPubKey *btcec.PublicKey,
) (*btcec.PrivateKey, error) {
	derived, err := privKey.DeriveTo(path)
	if err != nil {
		return nil, errors.Errorf("derive: %w", err)
	}
	ecPrivKey, err := derived.ECPrivKey()
	if err != nil {
		return nil, errors.Errorf("get private key: %w", err)
	}
	if !ecPrivKey.PubKey().IsEqual(expectedPubKey) {
		return nil, errors.New("the private key does not match the public key")
	}
	return ecPrivKey, nil
}
