package libwallet

import (
	"crypto/sha256"
	"fmt"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
)

func signNativeSegwitInput(index int, tx *wire.MsgTx, privateKey *HDPrivateKey, witnessScript []byte, amount btcutil.Amount) ([]byte, error) {

	privKey, err := privateKey.key.ECPrivKey()
	if err != nil {
		return nil, fmt.Errorf("failed to produce EC priv key for signing: %w", err)
	}

	sigHashes := txscript.NewTxSigHashes(tx)
	sig, err := txscript.RawTxInWitnessSignature(tx, sigHashes, index, int64(amount), witnessScript, txscript.SigHashAll, privKey)
	if err != nil {
		return nil, fmt.Errorf("failed to sign V4 input: %w", err)
	}

	return sig, nil
}

func createNonNativeSegwitRedeemScript(witnessScript []byte) ([]byte, error) {
	witnessScriptHash := sha256.Sum256(witnessScript)

	builder := txscript.NewScriptBuilder()
	builder.AddInt64(0)
	builder.AddData(witnessScriptHash[:])

	return builder.Script()
}

func signNonNativeSegwitInput(index int, tx *wire.MsgTx, privateKey *HDPrivateKey,
	redeemScript, witnessScript []byte, amount btcutil.Amount) ([]byte, error) {

	txInput := tx.TxIn[index]

	builder := txscript.NewScriptBuilder()
	builder.AddData(redeemScript)
	script, err := builder.Script()
	if err != nil {
		return nil, fmt.Errorf("failed to generate signing script: %w", err)
	}
	txInput.SignatureScript = script

	privKey, err := privateKey.key.ECPrivKey()
	if err != nil {
		return nil, fmt.Errorf("failed to produce EC priv key for signing: %w", err)
	}

	sigHashes := txscript.NewTxSigHashes(tx)
	sig, err := txscript.RawTxInWitnessSignature(
		tx, sigHashes, index, int64(amount), witnessScript, txscript.SigHashAll, privKey)
	if err != nil {
		return nil, fmt.Errorf("failed to sign V3 input: %w", err)
	}

	return sig, nil
}
