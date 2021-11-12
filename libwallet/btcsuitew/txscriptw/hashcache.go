package txscriptw

import (
	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/wire"
)

// TaprootSigHashes contains the sigHash parts for a PayToTaproot signature
type TaprootSigHashes struct {
	HashPrevOuts      chainhash.Hash
	HashSequence      chainhash.Hash
	HashOutputs       chainhash.Hash
	HashAmounts       chainhash.Hash
	HashScriptPubKeys chainhash.Hash
}

// NewTaprootSigHashes calculates and returns the TaprootSigHashes
func NewTaprootSigHashes(tx *wire.MsgTx, prevOuts []*wire.TxOut) *TaprootSigHashes {
	return &TaprootSigHashes{
		HashPrevOuts:      calcHashPrevOuts(tx),
		HashSequence:      calcHashSequences(tx),
		HashOutputs:       calcHashOutputs(tx),
		HashAmounts:       calcHashAmounts(prevOuts),
		HashScriptPubKeys: calcHashScriptPubKeys(prevOuts),
	}
}
