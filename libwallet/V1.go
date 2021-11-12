package libwallet

import (
	"fmt"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/btcsuitew/txscriptw"
)

// CreateAddressV1 returns a P2PKH MuunAddress from a publicKey for use in TransactionSchemeV1
func CreateAddressV1(publicKey *HDPublicKey) (MuunAddress, error) {
	return addresses.CreateAddressV1(&publicKey.key, publicKey.Path, publicKey.Network.network)
}

type coinV1 struct {
	Network  *chaincfg.Params
	OutPoint wire.OutPoint
	KeyPath  string
}

func (c *coinV1) SignInput(index int, tx *wire.MsgTx, userKey *HDPrivateKey, _ *HDPublicKey) error {
	userKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive user key: %w", err)
	}

	sig, err := c.signature(index, tx, userKey)
	if err != nil {
		return fmt.Errorf("failed to sign V1 input: %w", err)
	}

	builder := txscript.NewScriptBuilder()
	builder.AddData(sig)
	builder.AddData(userKey.PublicKey().Raw())
	script, err := builder.Script()
	if err != nil {
		return fmt.Errorf("failed to generate signing script: %w", err)
	}

	txInput := tx.TxIn[index]
	txInput.SignatureScript = script
	return nil
}

func (c *coinV1) FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error {
	return c.SignInput(index, tx, userKey, nil)
}

func (c *coinV1) createRedeemScript(publicKey *HDPublicKey) ([]byte, error) {

	userAddress, err := btcutil.NewAddressPubKey(publicKey.Raw(), c.Network)
	if err != nil {
		return nil, fmt.Errorf("failed to generate address for user: %w", err)
	}

	return txscriptw.PayToAddrScript(userAddress.AddressPubKeyHash())
}

func (c *coinV1) signature(index int, tx *wire.MsgTx, userKey *HDPrivateKey) ([]byte, error) {

	redeemScript, err := c.createRedeemScript(userKey.PublicKey())
	if err != nil {
		return nil, fmt.Errorf("failed to build reedem script for signing: %w", err)
	}

	privKey, err := userKey.key.ECPrivKey()
	if err != nil {
		return nil, fmt.Errorf("failed to produce EC priv key for signing: %w", err)
	}

	sig, err := txscript.RawTxInSignature(tx, index, redeemScript, txscript.SigHashAll, privKey)
	if err != nil {
		return nil, fmt.Errorf("failed to sign V1 input: %w", err)
	}

	return sig, nil
}
