package addresses

import (
	"bytes"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	secp "github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/platform/preconditions"
)

// MaxPayments caps the payment hashes a preimage leaf commits to: the most whose
// leaf fits txscript.ScriptBuilder's 10,000-byte script limit.
const MaxPayments = 255

// keyPathInputDataToSign returns the sighash for a taproot key path spend of
// the input at index. It is the same for every taproot output: the key path
// signs over the whole input and commits to no script.
func keyPathInputDataToSign(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
) ([]byte, error) {
	// SigHashDefault commits to every input, so it needs no prevout fetcher.
	return keyPathInputDataToSignWithSigHashType(
		index, tx, sigHashes, txscript.SigHashDefault, nil,
	)
}

// keyPathInputDataToSignWithSigHashType returns the sighash for a taproot key
// path spend of the input at index under an explicit sighash type.
// Panics if prevOuts is nil with SIGHASH_ANYONECANPAY, which commits to this
// input's prevout instead of to every input.
func keyPathInputDataToSignWithSigHashType(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	sigHashType txscript.SigHashType,
	prevOuts txscript.PrevOutputFetcher,
) ([]byte, error) {
	preconditions.CheckStatef(
		sigHashType&txscript.SigHashAnyOneCanPay == 0 || prevOuts != nil,
		"prevOuts is required for SIGHASH_ANYONECANPAY",
	)

	sigHash, err := txscript.CalcTaprootSignatureHash(
		sigHashes, sigHashType, tx, index, prevOuts,
	)
	if err != nil {
		return nil, errors.Errorf("calc taproot signature hash: %w", err)
	}
	return sigHash, nil
}

// leafInputDataToSign returns the sighash for a spend through the tapscript
// leaf at scriptLeafIndex of tapScriptTree.
func leafInputDataToSign(
	tapScriptTree *txscript.IndexedTapScriptTree,
	scriptLeafIndex int,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
) ([]byte, error) {
	// SigHashDefault commits to every input, so it needs no prevout fetcher.
	return leafInputDataToSignWithSigHashType(
		tapScriptTree, scriptLeafIndex, index, tx, sigHashes,
		txscript.SigHashDefault, nil,
	)
}

// leafInputDataToSignWithSigHashType returns the sighash for a spend through the
// tapscript leaf at scriptLeafIndex under an explicit sighash type.
// Panics if prevOuts is nil with SIGHASH_ANYONECANPAY, which commits to this
// input's prevout instead of to every input.
func leafInputDataToSignWithSigHashType(
	tapScriptTree *txscript.IndexedTapScriptTree,
	scriptLeafIndex int,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	sigHashType txscript.SigHashType,
	prevOuts txscript.PrevOutputFetcher,
) ([]byte, error) {
	preconditions.CheckStatef(
		sigHashType&txscript.SigHashAnyOneCanPay == 0 || prevOuts != nil,
		"prevOuts is required for SIGHASH_ANYONECANPAY",
	)

	scriptLeaf := tapScriptTree.LeafMerkleProofs[scriptLeafIndex].TapLeaf

	sigHash, err := txscript.CalcTapscriptSignaturehash(
		sigHashes, sigHashType, tx, index, prevOuts, scriptLeaf,
	)
	if err != nil {
		return nil, errors.Errorf("calc tapscript signature hash: %w", err)
	}
	return sigHash, nil
}

// siblingControlBlock builds the control block proving a tapleaf whose only
// sibling subtree is known by its hash.
func siblingControlBlock(
	internalKey *btcec.PublicKey,
	leaf txscript.TapLeaf,
	siblingHash []byte,
) ([]byte, error) {
	// BIP341 sorts a branch's children before hashing them.
	leafHash := leaf.TapHash()
	left, right := leafHash[:], siblingHash
	if bytes.Compare(left, right) > 0 {
		left, right = right, left
	}
	rootHash := chainhash.TaggedHash(chainhash.TagTapBranch, left, right)
	outputKey := txscript.ComputeTaprootOutputKey(internalKey, rootHash[:])

	controlBlock := txscript.ControlBlock{
		InternalKey:     internalKey,
		OutputKeyYIsOdd: outputKey.SerializeCompressed()[0] == secp.PubKeyFormatCompressedOdd,
		LeafVersion:     txscript.BaseLeafVersion,
		InclusionProof:  siblingHash,
	}
	controlBlockBytes, err := controlBlock.ToBytes()
	if err != nil {
		return nil, errors.Errorf("serialize control block: %w", err)
	}
	return controlBlockBytes, nil
}
