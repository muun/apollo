package libwallet

import (
	"github.com/btcsuite/btcutil"
	"github.com/muun/libwallet/addresses"

	"github.com/pkg/errors"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/wire"
)

// CreateAddressV4 returns a P2WSH MuunAddress from a user HD-pubkey and a Muun co-signing HD-pubkey.
func CreateAddressV4(userKey, muunKey *HDPublicKey) (MuunAddress, error) {
	return addresses.CreateAddressV4(&userKey.key, &muunKey.key, userKey.Path, userKey.Network.network)
}

type coinV4 struct {
	Network       *chaincfg.Params
	OutPoint      wire.OutPoint
	KeyPath       string
	Amount        btcutil.Amount
	MuunSignature []byte
}

func (c *coinV4) SignInput(index int, tx *wire.MsgTx, userKey *HDPrivateKey, muunKey *HDPublicKey) error {

	userKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Wrapf(err, "failed to derive user key")
	}

	muunKey, err = muunKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Wrapf(err, "failed to derive muun key")
	}

	if len(c.MuunSignature) == 0 {
		return errors.Errorf("muun signature must be present")
	}

	witnessScript, err := createWitnessScriptV4(userKey.PublicKey(), muunKey)
	if err != nil {
		return err
	}

	sig, err := c.signature(index, tx, userKey.PublicKey(), muunKey, userKey)
	if err != nil {
		return err
	}

	zeroByteArray := []byte{}

	txInput := tx.TxIn[index]
	txInput.Witness = wire.TxWitness{zeroByteArray, sig, c.MuunSignature, witnessScript}

	return nil
}

func (c *coinV4) FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error {

	derivedUserKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Wrapf(err, "failed to derive user key")
	}

	derivedMuunKey, err := muunKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Wrapf(err, "failed to derive muun key")
	}

	muunSignature, err := c.signature(index, tx, derivedUserKey.PublicKey(), derivedMuunKey.PublicKey(), derivedMuunKey)
	if err != nil {
		return err
	}
	c.MuunSignature = muunSignature
	return c.SignInput(index, tx, userKey, muunKey.PublicKey())
}

func (c *coinV4) signature(index int, tx *wire.MsgTx, userKey *HDPublicKey, muunKey *HDPublicKey,
	signingKey *HDPrivateKey) ([]byte, error) {

	witnessScript, err := createWitnessScriptV4(userKey, muunKey)
	if err != nil {
		return nil, err
	}

	return signNativeSegwitInput(
		index, tx, signingKey, witnessScript, c.Amount)
}

func createWitnessScriptV4(userKey, muunKey *HDPublicKey) ([]byte, error) {
	return addresses.CreateWitnessScriptV4(&userKey.key, &muunKey.key, userKey.Network.network)
}
