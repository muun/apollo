package v2_test

import (
	"bytes"
	"encoding/hex"
	"fmt"
	"testing"
	"time"

	"github.com/btcsuite/btcd/btcjson"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/txscript"
	"github.com/go-errors/errors"

	electrum "github.com/muun/libwallet/electrum/v2"
	"github.com/muun/libwallet/platform/test/testbitcoind"
	"github.com/muun/libwallet/platform/test/testsuite"
)

// dummyScriptHash is a valid-format scripthash with no history on chain.
const dummyScriptHash = "0000000000000000000000000000000000000000000000000000000000000000"

func TestElectrum_electrs_0_11_1_Integration(t *testing.T) {
	testsuite.Run(t, newElectrumTestSuite("electrs/0.11.1", "localhost:60002"))
}

func TestElectrum_fulcrum_2_0_0_Integration(t *testing.T) {
	testsuite.Run(t, newElectrumTestSuite("Fulcrum 2.0", "localhost:60003"))
}

func TestElectrum_electrumx_1_10_0_Integration(t *testing.T) {
	testsuite.Run(t, newElectrumTestSuite("ElectrumX 1.10.0", "localhost:60004"))
}

// ElectrumTestSuite is intended to be run against all the electrum implementations defined in
// the `electrum-test` group/directory to verify compatibility with the most common implementations
// in the wild. The implementation versions are chosen to be the lowest version of each popular
// electrum project with representative use in production.
// If any version is updated, then this test file should be updated accordingly.
type ElectrumTestSuite struct {
	testsuite.BaseIntegrationSuite
	serverImpl string
	serverAddr string
	bitcoind   *testbitcoind.Client
	electrum   electrum.Client
}

func newElectrumTestSuite(serverImpl, serverAddr string) *ElectrumTestSuite {
	return &ElectrumTestSuite{serverImpl: serverImpl, serverAddr: serverAddr}
}

func (s *ElectrumTestSuite) SetupSuite() {
	s.BaseIntegrationSuite.SetupSuite()

	s.bitcoind = testbitcoind.NewClient(s.T())
}

func (s *ElectrumTestSuite) SetupTest() {
	s.BaseIntegrationSuite.SetupTest()

	s.electrum = s.newElectrumClient()
	s.bitcoind.StartNewTest(s.T())

	s.bitcoind.EnsureMatureCoins()

	// Let's make sure electrum is caught up to bitcoind before we start
	s.waitForElectrumToCatchUp()
}

func (s *ElectrumTestSuite) TestServerFeatures() {
	features, err := s.electrum.ServerFeatures(s.Ctx)
	s.NoError(err)
	s.Equal(
		"0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206",
		features.GenesisHash,
	)
	s.Equal("sha256", features.HashFunction)
	s.Equal(0, features.Pruning)
	s.Equal(s.serverImpl, features.ServerVersion)
}

func (s *ElectrumTestSuite) TestServerPeers() {
	// In regtest we don't have peers.
	peers, err := s.electrum.ServerPeers(s.Ctx)
	s.NoError(err)
	s.Nil(peers)
}

func (s *ElectrumTestSuite) TestDisconnect() {
	client := s.newElectrumClient()
	s.True(client.IsConnected())

	client.Disconnect(s.Ctx)
	s.False(client.IsConnected())

	_, err := client.GetHeaders(s.Ctx)
	s.Error(err)
}

func (s *ElectrumTestSuite) TestBroadcast() {
	destAddr, destScriptHash := s.newAddressWithScriptHash()
	signedHex, expectedTxID := s.buildSignedTx(destAddr, btcutil.Amount(10_000))

	txHash, err := s.electrum.Broadcast(s.Ctx, signedHex)
	s.NoError(err)
	s.Equal(expectedTxID, txHash)

	s.bitcoind.GenerateBlocks(1)
	s.waitForElectrumToCatchUp()

	history, err := s.electrum.GetScriptHashHistory(s.Ctx, destScriptHash)
	s.NoError(err)
	s.Len(history, 1)
	s.Equal(txHash, history[0].TxHash)
	s.Greater(history[0].Height, int32(0), "transaction should be confirmed")
}

func (s *ElectrumTestSuite) TestBroadcastBatch() {
	addr1, hash1 := s.newAddressWithScriptHash()
	addr2, hash2 := s.newAddressWithScriptHash()
	hex1, txID1 := s.buildSignedTx(addr1, btcutil.Amount(10_000))
	hex2, txID2 := s.buildSignedTx(addr2, btcutil.Amount(10_000))

	broadcastResults, err := s.electrum.BroadcastBatch(s.Ctx, []string{hex1, hex2})
	s.NoError(err)
	s.Len(broadcastResults, 2)
	s.NoError(broadcastResults[hex1].Err)
	s.Equal(txID1, broadcastResults[hex1].Value)
	s.NoError(broadcastResults[hex2].Err)
	s.Equal(txID2, broadcastResults[hex2].Value)

	s.bitcoind.GenerateBlocks(1)
	s.waitForElectrumToCatchUp()

	historyResults, err := s.electrum.GetScriptHashHistoryBatch(s.Ctx, []string{hash1, hash2})
	s.NoError(err)
	s.Len(historyResults, 2)

	s.NoError(historyResults[hash1].Err)
	s.Len(historyResults[hash1].Value, 1)
	s.Equal(txID1, historyResults[hash1].Value[0].TxHash)
	s.Greater(historyResults[hash1].Value[0].Height, int32(0))

	s.NoError(historyResults[hash2].Err)
	s.Len(historyResults[hash2].Value, 1)
	s.Equal(txID2, historyResults[hash2].Value[0].TxHash)
	s.Greater(historyResults[hash2].Value[0].Height, int32(0))
}

// TestBroadcastBatchConflicting broadcasts multiple transactions that all spend
// the same UTXO and verifies that exactly one result is Ok and the rest carry Electrum errors.
func (s *ElectrumTestSuite) TestBroadcastBatchConflicting() {
	const n = 3
	txs := s.buildConflictingTxs(n)

	results, err := s.electrum.BroadcastBatch(s.Ctx, txs)
	s.NoError(err)
	s.Len(results, n)

	okCount := 0
	for _, hexTx := range txs {
		r := results[hexTx]
		if r.IsOk() {
			okCount++
			continue
		}
		s.NotNil(r.ElectrumError(), "expected Electrum error on conflict")
	}
	s.Equal(1, okCount, "exactly one of the conflicting txs should succeed")
}

func (s *ElectrumTestSuite) TestGetTransaction() {
	coinbaseTxID := s.getTxIDFromHash(s.bitcoind.GenerateBlocks(1)[0])
	s.waitForElectrumToCatchUp()

	txHex, err := s.electrum.GetTransaction(s.Ctx, coinbaseTxID)
	s.NoError(err)
	s.NotEmpty(txHex)

	tx := s.bitcoind.DecodeTx(txHex)
	s.Equal(coinbaseTxID, tx.TxID())
}

func (s *ElectrumTestSuite) TestGetTransactionBatch() {
	coinbaseTxID1 := s.getTxIDFromHash(s.bitcoind.GenerateBlocks(1)[0])
	coinbaseTxID2 := s.getTxIDFromHash(s.bitcoind.GenerateBlocks(1)[0])
	s.waitForElectrumToCatchUp()

	// dummyScriptHash is a valid 32-byte hex format that doubles as an unindexed txid
	results, err := s.electrum.GetTransactionBatch(
		s.Ctx, []string{coinbaseTxID1, coinbaseTxID2, dummyScriptHash},
	)
	s.NoError(err)
	s.Len(results, 3)

	s.True(results[coinbaseTxID1].IsOk())
	tx0 := s.bitcoind.DecodeTx(results[coinbaseTxID1].Value)
	s.Equal(coinbaseTxID1, tx0.TxID())

	s.True(results[coinbaseTxID2].IsOk())
	tx1 := s.bitcoind.DecodeTx(results[coinbaseTxID2].Value)
	s.Equal(coinbaseTxID2, tx1.TxID())

	s.True(results[dummyScriptHash].IsErr())
	electrumErr := results[dummyScriptHash].ElectrumError()
	s.NotNil(electrumErr, "expected an Electrum error for unknown txid")
	s.NotEmpty(electrumErr.Message)
}

func (s *ElectrumTestSuite) TestListUnspent() {
	// Empty for unused address
	unspent, err := s.electrum.ListUnspent(s.Ctx, dummyScriptHash)
	s.NoError(err)
	s.Empty(unspent)

	// Fund an address and verify the UTXO appears
	addr, scriptHash := s.newAddressWithScriptHash()
	s.bitcoind.GenerateToAddress(1, addr)
	s.waitForElectrumToCatchUp()

	height := s.bitcoind.GetBlockCount()

	unspent, err = s.electrum.ListUnspent(s.Ctx, scriptHash)
	s.NoError(err)
	s.Len(unspent, 1)

	s.NotEmpty(unspent[0].TxHash)
	s.Equal(0, unspent[0].TxPos)
	s.Greater(unspent[0].Value, int64(0))
	s.Equal(int(height), unspent[0].Height)
}

func (s *ElectrumTestSuite) TestListUnspentBatch() {
	addr, fundedHash := s.newAddressWithScriptHash()
	coinbaseTxID := s.getTxIDFromHash(s.bitcoind.GenerateToAddress(1, addr)[0])
	s.waitForElectrumToCatchUp()

	height := s.bitcoind.GetBlockCount()

	results, err := s.electrum.ListUnspentBatch(s.Ctx, []string{fundedHash, dummyScriptHash})
	s.NoError(err)
	s.Len(results, 2)

	// Funded address should have one UTXO matching the coinbase
	fundedResult := results[fundedHash]
	s.NoError(fundedResult.Err)
	s.Len(fundedResult.Value, 1)
	s.Equal(coinbaseTxID, fundedResult.Value[0].TxHash)
	s.Greater(fundedResult.Value[0].Value, int64(0))
	s.Equal(int(height), fundedResult.Value[0].Height)

	// Unused address should be empty
	dummyResult := results[dummyScriptHash]
	s.NoError(dummyResult.Err)
	s.Empty(dummyResult.Value)

	// Empty batch input should fail
	_, err = s.electrum.ListUnspentBatch(s.Ctx, nil)
	s.Error(err)
}

func (s *ElectrumTestSuite) TestGetHeaders() {
	bitcoindHeight := s.bitcoind.GetBlockCount()

	result, err := s.electrum.GetHeaders(s.Ctx)
	s.NoError(err)
	s.Equal(bitcoindHeight, result.Height)
	s.Len(result.HeaderHex, 160, "80-byte block header = 160 hex chars")
}

func (s *ElectrumTestSuite) TestGetHeadersTracksNewBlocks() {
	initial, err := s.electrum.GetHeaders(s.Ctx)
	s.NoError(err)

	// Single block
	s.bitcoind.GenerateBlocks(1)
	s.waitForElectrumToCatchUp()

	afterOne, err := s.electrum.GetHeaders(s.Ctx)
	s.NoError(err)
	s.Equal(initial.Height+1, afterOne.Height)
	s.NotEqual(initial.HeaderHex, afterOne.HeaderHex)

	// Multiple blocks in quick succession
	const blocksToMine = 3
	for range blocksToMine {
		s.bitcoind.GenerateBlocks(1)
	}
	s.waitForElectrumToCatchUp()

	afterMany, err := s.electrum.GetHeaders(s.Ctx)
	s.NoError(err)
	s.Equal(afterOne.Height+int32(blocksToMine), afterMany.Height)
}

func (s *ElectrumTestSuite) TestGetScriptHashHistory() {
	addr, scriptHash := s.newAddressWithScriptHash()

	// Empty history for unused address
	history, err := s.electrum.GetScriptHashHistory(s.Ctx, scriptHash)
	s.NoError(err)
	s.Empty(history)

	// Fund and check confirmed history appears
	coinbaseTxID := s.getTxIDFromHash(s.bitcoind.GenerateToAddress(1, addr)[0])
	s.waitForElectrumToCatchUp()

	height := s.bitcoind.GetBlockCount()

	history, err = s.electrum.GetScriptHashHistory(s.Ctx, scriptHash)
	s.NoError(err)
	s.Len(history, 1)
	s.Equal(coinbaseTxID, history[0].TxHash)
	s.Equal(height, history[0].Height)
}

func (s *ElectrumTestSuite) TestGetScriptHashHistoryMempool() {
	addr, scriptHash := s.newAddressWithScriptHash()

	// Send a tx to the address WITHOUT mining → mempool entry
	mempoolTxID := s.bitcoind.SendToAddress(addr, 0.0001)

	// Wait until electrs indexes the mempool tx
	err := s.waitFor(func() (bool, error) {
		history, err := s.electrum.GetScriptHashHistory(s.Ctx, scriptHash)
		if err != nil {
			return false, err
		}
		for _, entry := range history {
			if entry.TxHash == mempoolTxID {
				return true, nil
			}
		}
		return false, nil
	})
	s.NoError(err, "Electrum did not index mempool tx %s", mempoolTxID)

	history, err := s.electrum.GetScriptHashHistory(s.Ctx, scriptHash)
	s.NoError(err)
	s.Len(history, 1)
	s.Equal(mempoolTxID, history[0].TxHash)
	s.LessOrEqual(history[0].Height, int32(0), "mempool tx should have non-positive height")

	// Mine a block: the entry should transition to confirmed
	s.bitcoind.GenerateBlocks(1)
	s.waitForElectrumToCatchUp()

	history, err = s.electrum.GetScriptHashHistory(s.Ctx, scriptHash)
	s.NoError(err)
	s.Len(history, 1)
	s.Equal(mempoolTxID, history[0].TxHash)
	s.Greater(history[0].Height, int32(0), "tx should be confirmed after mining")
}

func (s *ElectrumTestSuite) TestMixedWorkload() {
	t := s.T()

	// Round 1: check height
	h1, err := s.electrum.GetHeaders(s.Ctx)
	s.NoError(err)
	t.Logf("Round 1: height %d", h1.Height)

	// Round 2: mine + ListUnspent + check height
	s.bitcoind.GenerateBlocks(1)
	s.waitForElectrumToCatchUp()

	unspent, err := s.electrum.ListUnspent(s.Ctx, dummyScriptHash)
	s.NoError(err)
	s.Empty(unspent)

	h2, err := s.electrum.GetHeaders(s.Ctx)
	s.NoError(err)
	s.Equal(h1.Height+1, h2.Height)
	t.Logf("Round 2: height %d", h2.Height)

	// Round 3: mine + GetTransaction + check height
	coinbase := s.getTxIDFromHash(s.bitcoind.GenerateBlocks(1)[0])
	s.waitForElectrumToCatchUp()

	txHex, err := s.electrum.GetTransaction(s.Ctx, coinbase)
	s.NoError(err)
	tx := s.bitcoind.DecodeTx(txHex)
	s.Equal(coinbase, tx.TxID())

	h3, err := s.electrum.GetHeaders(s.Ctx)
	s.NoError(err)
	s.Equal(h2.Height+1, h3.Height)
	t.Logf("Round 3: height %d", h3.Height)

	// Round 4: mine multiple blocks + batch call + check height
	for range 2 {
		s.bitcoind.GenerateBlocks(1)
	}
	s.waitForElectrumToCatchUp()

	results, err := s.electrum.ListUnspentBatch(s.Ctx, []string{dummyScriptHash})
	s.NoError(err)
	s.Len(results, 1)

	h4, err := s.electrum.GetHeaders(s.Ctx)
	s.NoError(err)
	s.Equal(h3.Height+2, h4.Height)
	t.Logf("Round 4: height %d", h4.Height)
}

// --- Helpers ---

func (s *ElectrumTestSuite) newElectrumClient() electrum.Client {
	s.T().Helper()

	client, err := electrum.NewClient(s.Ctx, s.serverAddr, electrum.RequireTCP)
	s.NoError(err)
	s.T().Cleanup(func() { client.Disconnect(s.Ctx) })

	return client
}

// newAddressWithScriptHash generates a new bitcoind address and returns both the
// address (for funding via bitcoind RPC) and its Electrum scripthash.
func (s *ElectrumTestSuite) newAddressWithScriptHash() (btcutil.Address, string) {
	s.T().Helper()

	addr := s.bitcoind.GetNewAddress("test")

	decoded, err := btcutil.DecodeAddress(addr.EncodeAddress(), &chaincfg.RegressionNetParams)
	s.NoError(err, "Failed to decode address")

	pkScript, err := txscript.PayToAddrScript(decoded)
	s.NoError(err, "Failed to create pkScript")

	return decoded, electrum.GetScriptHash(pkScript)
}

func (s *ElectrumTestSuite) getTxIDFromHash(hash *chainhash.Hash) string {
	s.T().Helper()

	block, err := s.bitcoind.GetBlock(hash)
	s.NoError(err)

	return block.Transactions[0].TxHash().String()
}

// buildSignedTx creates a funded and signed transaction paying amount to dest.
func (s *ElectrumTestSuite) buildSignedTx(
	dest btcutil.Address, amount btcutil.Amount,
) (string, string) {
	s.T().Helper()

	rawTx, err := s.bitcoind.Client.CreateRawTransaction(
		nil,
		map[btcutil.Address]btcutil.Amount{dest: amount},
		nil,
	)
	s.NoError(err)

	lockUnspents := true
	fundedTx, err := s.bitcoind.FundRawTransaction(
		rawTx,
		btcjson.FundRawTransactionOpts{LockUnspents: &lockUnspents},
		nil,
	)
	s.NoError(err)

	outpoints := make([]testbitcoind.Outpoint, 0, len(fundedTx.Transaction.TxIn))
	for _, in := range fundedTx.Transaction.TxIn {
		outpoints = append(outpoints, testbitcoind.Outpoint{
			TxID: in.PreviousOutPoint.Hash.String(),
			Vout: in.PreviousOutPoint.Index,
		})
	}
	s.T().Cleanup(func() { s.bitcoind.UnlockUnspent(outpoints) })

	signedTx, complete, err := s.bitcoind.SignRawTransactionWithWallet(fundedTx.Transaction)
	s.NoError(err)
	s.True(complete, "transaction should be fully signed")

	var buf bytes.Buffer
	err = signedTx.Serialize(&buf)
	s.NoError(err)

	return hex.EncodeToString(buf.Bytes()), signedTx.TxID()
}

// buildConflictingTxs creates n signed transactions that all spend the same
// confirmed UTXO to different fresh addresses.
func (s *ElectrumTestSuite) buildConflictingTxs(n int) []string {
	s.T().Helper()

	utxos, err := s.bitcoind.ListUnspentMin(1)
	s.NoError(err)
	s.NotEmpty(utxos, "wallet must have at least one spendable UTXO")
	picked := utxos[0]

	pickedOutpoint := []testbitcoind.Outpoint{{TxID: picked.TxID, Vout: picked.Vout}}
	s.bitcoind.LockUnspent(pickedOutpoint)
	s.T().Cleanup(func() { s.bitcoind.UnlockUnspent(pickedOutpoint) })

	inputAmount, err := btcutil.NewAmount(picked.Amount)
	s.NoError(err)
	outputAmount := inputAmount - btcutil.Amount(1_000) // 1000 sat fee

	inputs := []btcjson.TransactionInput{{Txid: picked.TxID, Vout: picked.Vout}}

	txs := make([]string, n)
	for i := range n {
		dest := s.bitcoind.GetNewAddress(fmt.Sprintf("conflict-%d", i))

		rawTx, err := s.bitcoind.Client.CreateRawTransaction(
			inputs,
			map[btcutil.Address]btcutil.Amount{dest: outputAmount},
			nil,
		)
		s.NoError(err)

		signedTx, complete, err := s.bitcoind.SignRawTransactionWithWallet(rawTx)
		s.NoError(err)
		s.True(complete, "conflicting tx %d should be fully signed", i)

		var buf bytes.Buffer
		s.NoError(signedTx.Serialize(&buf))
		txs[i] = hex.EncodeToString(buf.Bytes())
	}

	return txs
}

func (s *ElectrumTestSuite) waitForElectrumToCatchUp() {
	s.T().Helper()

	bitcoindHeight := s.bitcoind.GetBlockCount()

	_, err := s.electrum.GetHeaders(s.Ctx)
	s.NoError(err, "Poll client GetHeaders failed")

	err = s.waitFor(func() (bool, error) {
		result, err := s.electrum.GetHeaders(s.Ctx)
		return result.Height >= bitcoindHeight, err
	})
	s.NoError(err, "Electrum did not catch up to bitcoind height %d", bitcoindHeight)
}

func (s *ElectrumTestSuite) waitFor(f func() (bool, error)) error {
	s.T().Helper()

	const pollInterval = 100 * time.Millisecond
	const maxWait = 30 * time.Second
	deadline := time.Now().Add(maxWait)

	var err error
	var ok bool
	for time.Now().Before(deadline) {
		ok, err = f()
		if err == nil && ok {
			return nil
		}
		time.Sleep(pollInterval)
	}

	if err != nil {
		return errors.Errorf("timed out after %f seconds with error: %w", maxWait.Seconds(), err)
	}

	return errors.Errorf("timed out after %f seconds", maxWait.Seconds())
}
