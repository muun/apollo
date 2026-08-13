package testbitcoind

import (
	"bytes"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcjson"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/wire"

	"github.com/btcsuite/btcd/rpcclient"
)

const (
	bitcoindHost = "localhost:18443"
	bitcoindUser = "user"
	bitcoindPass = "123"
)

// Client wraps an rpcclient.Client to provide test-friendly helpers
// for interacting with a local regtest bitcoind node. Methods that encounter
// RPC errors call t.Fatal, so tests do not need explicit error checks.
type Client struct {
	*rpcclient.Client
	*testing.T
	nodeAddress *btcutil.Address
}

// NewClient connects to the local regtest bitcoind and registers
// a cleanup that shuts down the RPC connection when the test finishes.
func NewClient(t *testing.T) *Client {
	t.Helper()

	client, err := rpcclient.New(&rpcclient.ConnConfig{
		Host:         bitcoindHost + "/wallet/",
		User:         bitcoindUser,
		Pass:         bitcoindPass,
		Params:       "regtest",
		DisableTLS:   true,
		HTTPPostMode: true,
	}, nil)
	if err != nil {
		t.Fatalf("Failed to create bitcoind RPC client: %v", err)
	}

	testClient := &Client{
		Client: client,
		T:      t,
	}
	t.Cleanup(func() { testClient.Shutdown() })

	return testClient
}

// Shutdown closes the underlying RPC connection.
func (b *Client) Shutdown() {
	b.Helper()

	b.Client.Shutdown()
}

// StartNewTest rebinds the client to a new sub-test's testing.T and restores
// the previous one on cleanup, allowing a shared client across tests.
func (b *Client) StartNewTest(t *testing.T) {
	prevT := b.T
	b.T = t
	t.Cleanup(func() { b.T = prevT })
}

// GetNewAddress returns a new native-segwit address for the given account.
func (b *Client) GetNewAddress(account string) btcutil.Address {
	b.Helper()
	return b.GetNewAddressType(account, BtcAddressTypeSegwit)
}

// GetNewAddressType returns a new address of the specified type for the given account.
func (b *Client) GetNewAddressType(account string, addressType BtcAddressType) btcutil.Address {
	b.Helper()

	address, err := b.Client.GetNewAddressType(account, string(addressType))
	if err != nil {
		b.Fatalf("Failed to get new address: %v", err)
	}
	return address
}

// NodeAddress returns a cached wallet address used as the default destination
// for block rewards when mining with GenerateBlocks.
func (b *Client) NodeAddress() btcutil.Address {
	if b.nodeAddress == nil {
		nodeAddress := b.GetNewAddress("node")
		b.nodeAddress = &nodeAddress
	}

	return *b.nodeAddress
}

// GenerateToAddress mines the given number of blocks with coinbase rewards
// sent to address, returning the hashes of the new blocks.
func (b *Client) GenerateToAddress(blocks int, address btcutil.Address) []*chainhash.Hash {
	b.Helper()

	hashes, err := b.Client.GenerateToAddress(int64(blocks), address, nil)
	if err != nil {
		b.Fatalf("Failed to mine %d blocks to %v: %v", blocks, address, err)
	}
	return hashes
}

// GenerateBlocks mines the given number of blocks to the node's own address.
func (b *Client) GenerateBlocks(blocks int) []*chainhash.Hash {
	b.Helper()
	return b.GenerateToAddress(blocks, b.NodeAddress())
}

// GetBalance returns the wallet's confirmed balance in BTC.
func (b *Client) GetBalance() float64 {
	b.Helper()
	return mustMakeRequest[float64](b, "getbalance")
}

// GetBlockCount returns the current block height of the regtest chain.
func (b *Client) GetBlockCount() int32 {
	b.Helper()
	return mustMakeRequest[int32](b, "getblockcount")
}

// SendRawTransaction broadcasts a serialized transaction and returns its txid.
func (b *Client) SendRawTransaction(txHex string) (string, error) {
	b.Helper()

	txID, err := makeRequest[string](b, "sendrawtransaction", txHex)
	if err != nil {
		return "", err
	}

	b.Logf("Broadcasted transaction: %s", txID)

	return txID, nil
}

// MustSendRawTransaction is like SendRawTransaction but fails the test on error.
func (b *Client) MustSendRawTransaction(txHex string) string {
	txID, err := b.SendRawTransaction(txHex)
	if err != nil {
		b.Fatal(err)
	}
	return txID
}

// GetRawTransaction returns the hex-encoded raw transaction for the given txid,
// or an empty string if the transaction is not found.
func (b *Client) GetRawTransaction(txID string) string {
	b.Helper()

	resultHex, err := makeRequest[string](b, "getrawtransaction", txID)
	if err != nil {
		if checkResponseCode(err, btcjson.ErrRPCInvalidAddressOrKey) {
			return ""
		}
		b.Fatal(err)
	}

	return resultHex
}

// SendToAddress sends amount BTC from the wallet to address and returns the txid.
func (b *Client) SendToAddress(address btcutil.Address, amount float64) string {
	b.Helper()
	return mustMakeRequest[string](b, "sendtoaddress", address.EncodeAddress(), amount)
}

// ListUnspentForAddress returns confirmed UTXOs belonging to address.
func (b *Client) ListUnspentForAddress(address btcutil.Address) []ListUnspentResult {
	b.Helper()
	return mustMakeRequest[[]ListUnspentResult](b, "listunspent",
		1,                                 // minconfs
		9999999,                           // maxconfs
		[]string{address.EncodeAddress()}, // addresses
	)
}

// LockUnspent locks UTXOs so the wallet will not use them in automatic coin selection
// (e.g. sendtoaddress). This prevents the wallet from spending UTXOs that are reserved
// for pre-signed but not-yet-broadcast transactions.
func (b *Client) LockUnspent(outpoints []Outpoint) {
	b.Helper()
	mustMakeRequest[bool](b, "lockunspent", false, outpoints)
}

// UnlockUnspent releases previously locked UTXOs so the wallet can use them again.
// This variant ignores errors to allow being called as test cleanup, making sure that the UTXOs are
// released, but not caring if the test already spent them.
// See MustUnlockUnspent for an enforcing variant.
func (b *Client) UnlockUnspent(outpoints []Outpoint) {
	b.Helper()
	_, _ = makeRequest[bool](b, "lockunspent", true, outpoints)
}

// MustUnlockUnspent releases previously locked UTXOs so the wallet can use them again.
// Fails on error.
func (b *Client) MustUnlockUnspent(outpoints []Outpoint) {
	b.Helper()
	mustMakeRequest[bool](b, "lockunspent", true, outpoints)
}

// CreateRawTransaction builds an unsigned transaction spending the given inputs
// to the given outputs and returns its hex encoding.
func (b *Client) CreateRawTransaction(
	inputs []Outpoint,
	outputs []CreateRawTxOutput,
) string {
	b.Helper()

	rawOutputs := make([]map[string]float64, len(outputs))
	for i, output := range outputs {
		rawOutputs[i] = map[string]float64{output.Address.EncodeAddress(): output.Amount}
	}

	return mustMakeRequest[string](b, "createrawtransaction", inputs, rawOutputs)
}

// SignTransaction signs a raw transaction using the wallet's keys.
func (b *Client) SignTransaction(
	rawTxHex string,
	prevs []SignTransactionPrevOut,
) string {
	b.Helper()

	if len(prevs) == 0 {
		prevs = nil
	}

	type response struct {
		Hex      string `json:"hex"`
		Complete bool   `json:"complete"`
	}

	signResponse := mustMakeRequest[response](b, "signrawtransactionwithwallet", rawTxHex, prevs)

	if !signResponse.Complete {
		b.Fatalf("Transaction signing not complete")
	}

	return signResponse.Hex
}

// DecodeRawTransaction deserializes a hex-encoded transaction without broadcasting it.
func (b *Client) DecodeRawTransaction(rawTxHex string) DecodeRawTransactionResult {
	b.Helper()
	return mustMakeRequest[DecodeRawTransactionResult](b, "decoderawtransaction", rawTxHex)
}

// DropMempoolEntry drops an entry from the mempool given its transaction id.
func (b *Client) DropMempoolEntry(txID string) bool {
	b.Helper()
	return mustMakeRequest[bool](b, "dropmempoolentry", txID)
}

// DecodeTx deserializes a raw bitcoin transaction.
func (b *Client) DecodeTx(rawTx string) wire.MsgTx {
	b.Helper()

	rawBytes, err := hex.DecodeString(rawTx)
	if err != nil {
		b.Fatal(err, "failed to decode hex")
	}

	var tx wire.MsgTx
	err = tx.Deserialize(bytes.NewReader(rawBytes))
	if err != nil {
		b.Fatal(err, "failed to deserialize tx")
	}

	return tx
}

// EnsureMatureCoins mines 101 blocks if the wallet balance is below 1 BTC,
// guaranteeing at least one spendable coinbase output.
func (b *Client) EnsureMatureCoins() {
	b.Helper()

	// Check current balance.
	balance := b.GetBalance()

	if balance < 1.0 {
		// Mine 101 blocks to get mature coinbase outputs.
		b.GenerateBlocks(101)
	}
}

// AssertTxIsBroadcasted checks that a tx is currently broadcasted (mempool or confirmed).
func (b *Client) AssertTxIsBroadcasted(txID string) {
	b.Helper()

	// Check if the tx exists in bitcoind's mempool or chain.
	rawTx := b.GetRawTransaction(txID)
	if rawTx == "" {
		b.Fatalf("Expected transaction %s to be broadcast, but it was not found", txID)
	}

	b.Logf("Transaction %s confirmed as broadcast", txID)
}

// AssertTxIsNotBroadcasted checks that a tx is NOT currently broadcasted (mempool or confirmed).
func (b *Client) AssertTxIsNotBroadcasted(txID string) {
	b.Helper()

	rawTx := b.GetRawTransaction(txID)
	if rawTx != "" {
		b.Fatalf("Expected transaction %s to NOT be broadcast, but it was found", txID)
	}
}
