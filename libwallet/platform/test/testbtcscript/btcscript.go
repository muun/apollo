// Package testbtcscript provides helpers to build and verify spending scripts against
// btcd engine in tests.
package testbtcscript

import (
	"bytes"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/stretchr/testify/require"
)

// PrivKeyFromByte builds a deterministic private key from a single repeated byte.
func PrivKeyFromByte(fill byte) *btcec.PrivateKey {
	privKey, _ := btcec.PrivKeyFromBytes(bytes.Repeat([]byte{fill}, 32))
	return privKey
}

// SigHashesFor returns the sighash cache for spending prevOut with spendingTx.
func SigHashesFor(spendingTx *wire.MsgTx, prevOut *wire.TxOut) *txscript.TxSigHashes {
	fetcher := txscript.NewCannedPrevOutputFetcher(prevOut.PkScript, prevOut.Value)
	return txscript.NewTxSigHashes(spendingTx, fetcher)
}

// VerifyInput runs the script engine for one input of a spending tx.
func VerifyInput(
	t *testing.T, spendingTx *wire.MsgTx, prevOut *wire.TxOut, index int,
) error {
	t.Helper()

	fetcher := txscript.NewCannedPrevOutputFetcher(prevOut.PkScript, prevOut.Value)
	return VerifyInputWithFetcher(t, spendingTx, fetcher, index)
}

// VerifyInputWithFetcher runs the script engine for one input of a spending
// tx, resolving prevouts through the given fetcher.
func VerifyInputWithFetcher(
	t *testing.T, spendingTx *wire.MsgTx, fetcher txscript.PrevOutputFetcher, index int,
) error {
	t.Helper()

	txscript.DisableLog()
	// Uncomment the next block if you need to see what the script engine outputs
	// logger := btclog.NewBackend(os.Stderr).Logger("test")
	// logger.SetLevel(btclog.LevelTrace)
	// txscript.UseLogger(logger)

	flags := txscript.ScriptBip16 | txscript.ScriptVerifyDERSignatures |
		txscript.ScriptStrictMultiSig | txscript.ScriptDiscourageUpgradableNops |
		txscript.ScriptVerifyStrictEncoding | txscript.ScriptVerifyLowS |
		txscript.ScriptVerifyWitness | txscript.ScriptVerifyCheckLockTimeVerify |
		txscript.ScriptVerifyCheckSequenceVerify | txscript.ScriptVerifyTaproot

	prevOut := fetcher.FetchPrevOutput(spendingTx.TxIn[index].PreviousOutPoint)
	vm, err := txscript.NewEngine(
		prevOut.PkScript,
		spendingTx,
		index,
		flags,
		nil,
		txscript.NewTxSigHashes(spendingTx, fetcher),
		prevOut.Value,
		fetcher,
	)
	// A NewEngine error is a broken test setup, not an invalid spend: fail now
	// so tests expecting a failure can't hide a bad setup.
	require.NoError(t, err)
	return vm.Execute()
}
