package txscriptw

import (
	"bytes"
	"encoding/binary"
	"fmt"

	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/muun/libwallet/btcsuitew/chainhashw"
)

// CalcTaprootSigHash crafts signature digest.
// It only supports SIGHASH_ALL without ANYONECANPAY, and no annex or script paths.
func CalcTaprootSigHash(
	tx *wire.MsgTx,
	sigHashes *TaprootSigHashes,
	index int,
	hashType txscript.SigHashType,
) ([]byte, error) {

	if index >= len(tx.TxIn) {
		return nil, fmt.Errorf("wanted index %d but found only %d inputs", index, len(tx.TxIn))
	}

	anyoneCanPay := hashType&txscript.SigHashAnyOneCanPay != 0
	hashType = hashType & 0x1f

	if hashType != txscript.SigHashAll {
		return nil, fmt.Errorf("only SIGHASH_ALL is supported")
	}

	if anyoneCanPay {
		return nil, fmt.Errorf("anyoneCanPay is not supported")
	}

	b := new(bytes.Buffer)

	// Epoch [1] (not technically part of the message, but every use-case adds this prefix later)
	b.WriteByte(0x00)

	// SigHash type [1]
	b.WriteByte(byte(hashType))

	// nVersion [4]
	b.Write(uInt32Le(uint32(tx.Version)))

	// nLockTime [4]
	b.Write(uInt32Le(tx.LockTime))

	// input data [128 per input] always included since we failed for anyoneCanPay
	if !anyoneCanPay {
		b.Write(sigHashes.HashPrevOuts[:])
		b.Write(sigHashes.HashAmounts[:])
		b.Write(sigHashes.HashScriptPubKeys[:])
		b.Write(sigHashes.HashSequence[:])
	}

	// output data [?] always included since we checked for SigHashAll
	if hashType != txscript.SigHashNone && hashType != txscript.SigHashSingle {
		b.Write(sigHashes.HashOutputs[:])
	}

	// Spend type [1] always 0x00 since we don't support annex or script path
	b.WriteByte(0x00)

	if anyoneCanPay {
		// MISSING: commit to the spent output and sequence (never since we failed for anyoneCanPay)
	} else {
		// Input index [4]
		b.Write(uInt32Le(uint32(index)))
	}

	// MISSING: do some more hashing and commit to the annex (not supported)

	if hashType == txscript.SigHashSingle {
		return nil, fmt.Errorf("SIGHASH_SINGLE is not supported")
	}

	// MISSING: encode extensions, such as the script path commitment from BIP-342 (not supported)
	// As with the epoch byte above, not technically part of the message, but used in all cases

	return chainhashw.TaggedHashB(chainhashw.TagTapSighash, b.Bytes()), nil
}

func uInt32Le(n uint32) []byte {
	var nBytes [4]byte
	binary.LittleEndian.PutUint32(nBytes[:], n)
	return nBytes[:]
}

func uInt64Le(n uint64) []byte {
	var nBytes [8]byte
	binary.LittleEndian.PutUint64(nBytes[:], n)
	return nBytes[:]
}

func calcHashPrevOuts(tx *wire.MsgTx) chainhash.Hash {
	b := new(bytes.Buffer)

	for _, txIn := range tx.TxIn {
		b.Write(txIn.PreviousOutPoint.Hash[:])
		b.Write(uInt32Le(txIn.PreviousOutPoint.Index))
	}

	return chainhash.HashH(b.Bytes())
}

func calcHashSequences(tx *wire.MsgTx) chainhash.Hash {
	b := new(bytes.Buffer)

	for _, txIn := range tx.TxIn {
		b.Write(uInt32Le(txIn.Sequence))
	}

	return chainhash.HashH(b.Bytes())
}

func calcHashOutputs(tx *wire.MsgTx) chainhash.Hash {
	b := new(bytes.Buffer)

	for _, txOut := range tx.TxOut {
		wire.WriteTxOut(b, 0, 0, txOut)
	}

	return chainhash.HashH(b.Bytes())
}

func calcHashScriptPubKeys(txOuts []*wire.TxOut) chainhash.Hash {
	b := new(bytes.Buffer)

	for _, txOut := range txOuts {
		wire.WriteVarInt(b, 0, uint64(len(txOut.PkScript)))
		b.Write(txOut.PkScript)
	}

	return chainhash.HashH(b.Bytes())
}

func calcHashAmounts(txOuts []*wire.TxOut) chainhash.Hash {
	b := new(bytes.Buffer)

	for _, txOut := range txOuts {
		b.Write(uInt64Le(uint64(txOut.Value)))
	}

	return chainhash.HashH(b.Bytes())
}
