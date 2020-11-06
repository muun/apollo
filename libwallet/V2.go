package libwallet

import (
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/muun/libwallet/addresses"
	"github.com/pkg/errors"

	"github.com/btcsuite/btcd/wire"
)

func CreateAddressV2(userKey, muunKey *HDPublicKey) (MuunAddress, error) {
	// TODO: check both paths match?
	return addresses.CreateAddressV2(&userKey.key, &muunKey.key, userKey.Path, userKey.Network.network)
}

type coinV2 struct {
	Network       *chaincfg.Params
	OutPoint      wire.OutPoint
	KeyPath       string
	MuunSignature []byte
}

func (c *coinV2) SignInput(index int, tx *wire.MsgTx, userKey *HDPrivateKey, muunKey *HDPublicKey) error {
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

	txInput := tx.TxIn[index]

	redeemScript, err := createRedeemScriptV2(userKey.PublicKey(), muunKey)
	if err != nil {
		return errors.Wrapf(err, "failed to build reedem script for signing")
	}

	sig, err := c.signature(index, tx, userKey.PublicKey(), muunKey, userKey)
	if err != nil {
		return err
	}

	// This is a standard 2 of 2 multisig script
	// 0 because of a bug in bitcoind
	// Then the 2 sigs: first the users and then muuns
	// Last, the script that contains the two pub keys and OP_CHECKMULTISIG
	builder := txscript.NewScriptBuilder()
	builder.AddInt64(0)
	builder.AddData(sig)
	builder.AddData(c.MuunSignature)
	builder.AddData(redeemScript)
	script, err := builder.Script()
	if err != nil {
		return errors.Wrapf(err, "failed to generate signing script")
	}

	txInput.SignatureScript = script

	return nil
}

func (c *coinV2) FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error {

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

func (c *coinV2) signature(index int, tx *wire.MsgTx, userKey, muunKey *HDPublicKey,
	signingKey *HDPrivateKey) ([]byte, error) {

	redeemScript, err := createRedeemScriptV2(userKey, muunKey)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to build reedem script for signing")
	}

	privKey, err := signingKey.key.ECPrivKey()
	if err != nil {
		return nil, errors.Wrapf(err, "failed to produce EC priv key for signing")
	}

	sig, err := txscript.RawTxInSignature(tx, index, redeemScript, txscript.SigHashAll, privKey)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to sign V2 output")
	}

	return sig, nil
}

func createRedeemScriptV2(userKey, muunKey *HDPublicKey) ([]byte, error) {
	return addresses.CreateRedeemScriptV2(&userKey.key, &muunKey.key, userKey.Network.network)
}
