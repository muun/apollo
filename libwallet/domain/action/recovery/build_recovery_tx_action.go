package recovery

import (
	"encoding/hex"
	"fmt"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/wire"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/btcsuitew/txscriptw"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/scanner"
	"math"
)

const dustThreshold = 546

type BuildSweepTxAction struct {
	keyProvider keys.KeyProvider
	network     *libwallet.Network
}

func NewBuildSweepTxAction(keyProvider keys.KeyProvider, network *libwallet.Network) *BuildSweepTxAction {
	return &BuildSweepTxAction{
		keyProvider: keyProvider,
		network:     network,
	}
}

func (action *BuildSweepTxAction) Run(utxos []*scanner.Utxo, sweepAddress btcutil.Address, feeRateInSatsPerVByte float64) (*wire.MsgTx, error) {
	value := int64(0)

	tx := wire.NewMsgTx(2)
	for _, utxo := range utxos {
		chainHash, err := chainhash.NewHashFromStr(utxo.TxID)
		if err != nil {
			return nil, err
		}

		outpoint := wire.OutPoint{
			Hash:  *chainHash,
			Index: uint32(utxo.OutputIndex),
		}

		tx.AddTxIn(wire.NewTxIn(&outpoint, []byte{}, [][]byte{}))
		value += utxo.Amount
	}

	script, err := txscriptw.PayToAddrScript(sweepAddress)
	if err != nil {
		return nil, err
	}

	tx.AddTxOut(wire.NewTxOut(value, script)) // Note: No fees yet.

	fullSize := tx.SerializeSize()
	witnessSize := fullSize - tx.SerializeSizeStripped()
	// virtual size can be non-integer, so store it as float64
	virtualSize := float64(fullSize) + float64(witnessSize)/4
	finalFee := int64(math.Ceil(virtualSize * feeRateInSatsPerVByte))

	if len(tx.TxOut) != 1 {
		return nil, fmt.Errorf("expected 1 output, got %d", len(tx.TxOut))
	}

	if tx.TxOut[0].Value < finalFee {
		return nil, fmt.Errorf("fees (%d sats) exceed total funds (%d sats)", finalFee, tx.TxOut[0].Value)
	}

	// Reduce value by calculated fee.
	tx.TxOut[0].Value -= finalFee

	if tx.TxOut[0].Value < dustThreshold {
		return nil, fmt.Errorf("output is sub-dust (%d sats) after deducting fees (%d sats)", tx.TxOut[0].Value, finalFee)
	}

	return tx, nil
}

type input struct {
	utxo          *scanner.Utxo
	muunSignature []byte
}

func (i *input) OutPoint() libwallet.Outpoint {
	return &outpoint{utxo: i.utxo}
}

func (i *input) Address() libwallet.MuunAddress {
	return i.utxo.Address
}

func (i *input) UserSignature() []byte {
	return []byte{}
}

func (i *input) MuunSignature() []byte {
	return i.muunSignature
}

func (i *input) SubmarineSwapV1() libwallet.InputSubmarineSwapV1 {
	return nil
}

func (i *input) SubmarineSwapV2() libwallet.InputSubmarineSwapV2 {
	return nil
}

func (i *input) IncomingSwap() libwallet.InputIncomingSwap {
	return nil
}

func (i *input) MuunPublicNonce() []byte {
	// Will always be nil in this context
	// Look at coinV5.signFirstWith for reasons.
	return nil
}

// outpoint is a minimal type that implements libwallet.Outpoint
type outpoint struct {
	utxo *scanner.Utxo
}

func (o *outpoint) TxId() []byte {
	raw, err := hex.DecodeString(o.utxo.TxID)
	if err != nil {
		panic(err) // we wrote this hex value ourselves, no input from anywhere else
	}

	return raw
}

func (o *outpoint) Index() int {
	return o.utxo.OutputIndex
}

func (o *outpoint) Amount() int64 {
	return o.utxo.Amount
}
