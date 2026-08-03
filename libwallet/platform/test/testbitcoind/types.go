package testbitcoind

import "github.com/btcsuite/btcd/btcutil"

// BtcAddressType is the address type parameter for Bitcoin Core's `getnewaddress` RPC.
type BtcAddressType string

const (
	BtcAddressTypeLegacy        BtcAddressType = "legacy"
	BtcAddressTypeWrappedSegwit BtcAddressType = "p2sh-segwit"
	BtcAddressTypeSegwit        BtcAddressType = "bech32"
	BtcAddressTypeTaproot       BtcAddressType = "bech32m"
)

type Outpoint struct {
	TxID string `json:"txid"`
	Vout uint32 `json:"vout"`
}

// ListUnspentResult models a single UTXO entry of the list returned by the `listunspent` Bitcoin
// Core RPC call.
type ListUnspentResult struct {
	Outpoint
	Amount       float64 `json:"amount"`
	ScriptPubKey string  `json:"scriptPubKey"`
	// More fields are available
}

// CreateRawTxOutput models a single entry of the list received as second param
// by the `createrawtransaction` Bitcoin Core RPC call.
type CreateRawTxOutput struct {
	Address btcutil.Address
	Amount  float64
}

// SignTransactionPrevOut models a single entry of the list received as second param
// by the `signrawtransactionwithwallet` Bitcoin Core RPC call.
// It describes a previous output that the transaction spends, used when
// the output may not yet be in the blockchain.
type SignTransactionPrevOut struct {
	Outpoint
	ScriptPubKey string  `json:"scriptPubKey"`
	Amount       float64 `json:"amount"`
}

// ScriptSig represents the unlocking script of a transaction input.
type ScriptSig struct {
	Asm string `json:"asm"`
	Hex string `json:"hex"`
}

// ScriptPubKey represents the locking script of a transaction output.
type ScriptPubKey struct {
	Asm     string `json:"asm"`
	Hex     string `json:"hex"`
	Desc    string `json:"desc"`
	Type    string `json:"type"`
	Address string `json:"address,omitempty"`
}

// Vin represents a single transaction input.
type Vin struct {
	Outpoint
	ScriptSig   ScriptSig `json:"scriptSig"`
	TxInWitness []string  `json:"txinwitness,omitempty"`
	Sequence    uint32    `json:"sequence"`
}

// Vout represents a single transaction output.
type Vout struct {
	Value        float64      `json:"value"`
	N            uint32       `json:"n"`
	ScriptPubKey ScriptPubKey `json:"scriptPubKey"`
}

// DecodeRawTransactionResult models the response from the `decoderawtransaction` Bitcoin Core RPC
// call.
type DecodeRawTransactionResult struct {
	TxID     string `json:"txid"`
	Hash     string `json:"hash"`
	Version  int32  `json:"version"`
	Size     int64  `json:"size"`
	VSize    int64  `json:"vsize"`
	Weight   int64  `json:"weight"`
	LockTime uint32 `json:"locktime"`
	Vin      []Vin  `json:"vin"`
	Vout     []Vout `json:"vout"`
}
