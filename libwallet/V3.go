package libwallet

import (
	"errors"
	"fmt"

	"github.com/btcsuite/btcutil"
	"github.com/muun/libwallet/addresses"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/wire"
)

func CreateAddressV3(userKey, muunKey *HDPublicKey) (MuunAddress, error) {
	return addresses.CreateAddressV3(&userKey.key, &muunKey.key, userKey.Path, userKey.Network.network)
}

type coinV3 struct {
	Network       *chaincfg.Params
	OutPoint      wire.OutPoint
	KeyPath       string
	Amount        btcutil.Amount
	MuunSignature []byte
}

func (c *coinV3) SignInput(index int, tx *wire.MsgTx, userKey *HDPrivateKey, muunKey *HDPublicKey) error {

	userKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive user key: %w", err)
	}

	muunKey, err = muunKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive muun key: %w", err)
	}

	if len(c.MuunSignature) == 0 {
		return errors.New("muun signature must be present")
	}

	witnessScript, err := createWitnessScriptV3(userKey.PublicKey(), muunKey)
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

func (c *coinV3) FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error {

	derivedUserKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive user key: %w", err)
	}

	derivedMuunKey, err := muunKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive muun key: %w", err)
	}

	muunSignature, err := c.signature(index, tx, derivedUserKey.PublicKey(), derivedMuunKey.PublicKey(), derivedMuunKey)
	if err != nil {
		return err
	}
	c.MuunSignature = muunSignature
	return c.SignInput(index, tx, userKey, muunKey.PublicKey())
}

func createRedeemScriptV3(userKey, muunKey *HDPublicKey) ([]byte, error) {
	return addresses.CreateRedeemScriptV3(&userKey.key, &muunKey.key, userKey.Network.network)
}

func createWitnessScriptV3(userKey, muunKey *HDPublicKey) ([]byte, error) {
	return addresses.CreateWitnessScriptV3(&userKey.key, &muunKey.key, userKey.Network.network)
}

func (c *coinV3) signature(index int, tx *wire.MsgTx, userKey *HDPublicKey, muunKey *HDPublicKey,
	signingKey *HDPrivateKey) ([]byte, error) {

	witnessScript, err := createWitnessScriptV3(userKey, muunKey)
	if err != nil {
		return nil, err
	}

	redeemScript, err := createRedeemScriptV3(userKey, muunKey)
	if err != nil {
		return nil, fmt.Errorf("failed to build reedem script for signing: %w", err)
	}

	return signNonNativeSegwitInput(
		index, tx, signingKey, redeemScript, witnessScript, c.Amount)
}
