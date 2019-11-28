package libwallet

import (
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
	"github.com/pkg/errors"
)

// CreateAddressV1 returns a P2PKH MuunAddress from a publicKey for using in TransactionSchameV1
func CreateAddressV1(publicKey *HDPublicKey) (MuunAddress, error) {
	pubkey, err := btcutil.NewAddressPubKey(publicKey.Raw(), publicKey.Network.network)
	if err != nil {
		return nil, err
	}

	pubkeyHash := pubkey.AddressPubKeyHash()
	address := pubkeyHash.String()

	return &muunAddress{address: address, version: addressV1, derivationPath: publicKey.Path}, nil
}

func addUserSignatureInputV1(input Input, index int, tx *wire.MsgTx, privateKey *HDPrivateKey) (*wire.TxIn, error) {

	txInput := tx.TxIn[index]

	sig, err := signInputV1(input, index, tx, privateKey)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to sign V1 input")
	}

	builder := txscript.NewScriptBuilder()
	builder.AddData(sig)
	builder.AddData(privateKey.PublicKey().Raw())
	script, err := builder.Script()
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate signing script")
	}

	txInput.SignatureScript = script

	return txInput, nil
}

func createRedeemScriptV1(publicKey *HDPublicKey) ([]byte, error) {

	userAddress, err := btcutil.NewAddressPubKey(publicKey.Raw(), publicKey.Network.network)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate address for user")
	}

	return txscript.PayToAddrScript(userAddress.AddressPubKeyHash())
}

func signInputV1(input Input, index int, tx *wire.MsgTx, privateKey *HDPrivateKey) ([]byte, error) {

	redeemScript, err := createRedeemScriptV1(privateKey.PublicKey())
	if err != nil {
		return nil, errors.Wrapf(err, "failed to build reedem script for signing")
	}

	privKey, err := privateKey.key.ECPrivKey()
	if err != nil {
		return nil, errors.Wrapf(err, "failed to produce EC priv key for signing")
	}

	sig, err := txscript.RawTxInSignature(tx, index, redeemScript, txscript.SigHashAll, privKey)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to sign V1 input")
	}

	return sig, nil
}
