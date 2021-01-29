package libwallet

import (
	"errors"
	"fmt"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
	"github.com/muun/libwallet/swaps"
)

type coinSubmarineSwapV1 struct {
	Network         *chaincfg.Params
	OutPoint        wire.OutPoint
	KeyPath         string
	Amount          btcutil.Amount
	RefundAddress   string
	PaymentHash256  []byte
	ServerPublicKey []byte
	LockTime        int64
}

func (c *coinSubmarineSwapV1) SignInput(index int, tx *wire.MsgTx, userKey *HDPrivateKey,
	_ *HDPublicKey) error {

	userKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive user key: %w", err)
	}

	witnessScript, err := swaps.CreateWitnessScriptSubmarineSwapV1(
		c.RefundAddress,
		c.PaymentHash256,
		c.ServerPublicKey,
		c.LockTime,
		userKey.Network.network,
	)
	if err != nil {
		return err
	}

	redeemScript, err := createNonNativeSegwitRedeemScript(witnessScript)
	if err != nil {
		return fmt.Errorf("failed to build reedem script for signing: %w", err)
	}

	sig, err := signNonNativeSegwitInput(
		index, tx, userKey, redeemScript, witnessScript, c.Amount)
	if err != nil {
		return err
	}

	txInput := tx.TxIn[index]
	txInput.Witness = wire.TxWitness{sig, userKey.PublicKey().Raw(), witnessScript}

	return nil
}

func (c *coinSubmarineSwapV1) FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error {
	return errors.New("cannot fully sign submarine swap transactions")
}
