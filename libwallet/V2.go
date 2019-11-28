package libwallet

import (
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcutil"
	"github.com/pkg/errors"

	"github.com/btcsuite/btcd/wire"
)

func CreateAddressV2(userKey, muunKey *HDPublicKey) (MuunAddress, error) {

	script, err := createRedeemScriptV2(userKey, muunKey)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate redeem script v2")
	}

	address, err := btcutil.NewAddressScriptHash(script, userKey.Network.network)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate multisig address")
	}

	return &muunAddress{
		address:        address.String(),
		version:        addressV2,
		derivationPath: userKey.Path,
	}, nil
}

func createRedeemScriptV2(userKey, muunKey *HDPublicKey) ([]byte, error) {

	userAddress, err := btcutil.NewAddressPubKey(userKey.Raw(), userKey.Network.network)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate address for user")
	}

	muunAddress, err := btcutil.NewAddressPubKey(muunKey.Raw(), muunKey.Network.network)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate address for muun")
	}

	return txscript.MultiSigScript([]*btcutil.AddressPubKey{
		userAddress,
		muunAddress,
	}, 2)
}

func addUserSignatureInputV2(input Input, index int, tx *wire.MsgTx, privateKey *HDPrivateKey,
	muunKey *HDPublicKey) (*wire.TxIn, error) {

	if len(input.MuunSignature()) == 0 {
		return nil, errors.Errorf("muun signature must be present")
	}

	txInput := tx.TxIn[index]

	redeemScript, err := createRedeemScriptV2(privateKey.PublicKey(), muunKey)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to build reedem script for signing")
	}

	sig, err := signInputV2(input, index, tx, privateKey.PublicKey(), muunKey, privateKey)
	if err != nil {
		return nil, err
	}

	// This is a standard 2 of 2 multisig script
	// 0 because of a bug in bitcoind
	// Then the 2 sigs: first the users and then muuns
	// Last, the script that contains the two pub keys and OP_CHECKMULTISIG
	builder := txscript.NewScriptBuilder()
	builder.AddInt64(0)
	builder.AddData(sig)
	builder.AddData(input.MuunSignature())
	builder.AddData(redeemScript)
	script, err := builder.Script()
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate signing script")
	}

	txInput.SignatureScript = script

	return txInput, nil
}

func signInputV2(input Input, index int, tx *wire.MsgTx, userKey, muunKey *HDPublicKey,
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
