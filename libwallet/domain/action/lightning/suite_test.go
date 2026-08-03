package lightning_test

import (
	"bytes"
	"context"
	"encoding/hex"
	"math"
	"path"

	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"

	"github.com/muun/libwallet/btcsuitew/btcutilw"
	data "github.com/muun/libwallet/data/lightning"
	"github.com/muun/libwallet/domain/action/lightning"
	model "github.com/muun/libwallet/domain/model/lightning"
	electrum "github.com/muun/libwallet/electrum/v2"
	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/platform/test/testbitcoind"
	"github.com/muun/libwallet/platform/test/testelectrum"
	"github.com/muun/libwallet/platform/test/testsuite"
	"github.com/muun/libwallet/storage"
	"github.com/muun/libwallet/walletdb"
)

const (
	// testDefaultTxFee is the fee (in BTC) used by test transactions when no specific fee is needed.
	testDefaultTxFee = 0.0001
)

type (
	// taprootOutput holds the key material for a taproot output with two spending paths:
	// key-spend (internal key) and script-spend (leaf with OP_CHECKSIG).
	taprootOutput struct {
		internalKey   *secp256k1.PrivateKey
		scriptPathKey *secp256k1.PrivateKey
		leaf          txscript.TapLeaf
		tapTree       *txscript.IndexedTapScriptTree
		outputKey     *secp256k1.PublicKey
		pkScript      []byte
	}

	EnforcerTestSuite struct {
		testsuite.BaseIntegrationSuite
		locker                    *memlock.NamedLocker
		bitcoind                  *testbitcoind.Client
		electrum                  *testelectrum.Client
		kv                        *storage.KeyValueStorage
		batchRepository           data.IncomingHTLCBatchRepository
		registerIncomingHTLCBatch lightning.RegisterIncomingHTLCAction
		revealPreimages           lightning.RevealPreimagesAction
		completeIncomingHTLCBatch lightning.CompleteIncomingHTLCBatchAction
		publishEnforcer           lightning.PublishEnforcerAction
	}
)

func (s *EnforcerTestSuite) SetupSuite() {
	s.BaseIntegrationSuite.SetupSuite()

	s.locker = &memlock.NamedLocker{}

	s.bitcoind = testbitcoind.NewClient(s.T())
	s.electrum = testelectrum.NewClient(s.Ctx, s.T())

	pool, err := walletdb.NewPool(path.Join(s.T().TempDir(), "test.db"), nil)
	s.Require().NoError(err, "open walletdb")
	s.T().Cleanup(func() { pool.Close() })
	s.kv = storage.NewKeyValueStorage(
		pool.NewKeyValueRepository(),
		kvTestStorageSchema(),
	)

	s.batchRepository = data.NewIncomingHTLCBatchRepository(s.kv)

	s.registerIncomingHTLCBatch = lightning.NewRegisterIncomingHTLCAction(s.batchRepository)
	s.revealPreimages = lightning.NewRevealPreimagesAction(s.locker, s.batchRepository)
	s.completeIncomingHTLCBatch = lightning.NewCompleteIncomingHTLCAction(
		s.locker,
		s.batchRepository,
	)
	s.publishEnforcer = lightning.NewPublishEnforcerAction(
		s.locker,
		s.electrum.Client,
		s.batchRepository,
		lightning.NewExternalTxCheckerAction(s.electrum.Client),
	)
}

func (s *EnforcerTestSuite) SetupTest() {
	s.BaseIntegrationSuite.SetupTest()

	s.bitcoind.StartNewTest(s.T())
	s.electrum.StartNewTest(s.T())

	// Fresh kv storage per test
	s.resetKvStorage()

	s.bitcoind.EnsureMatureCoins()
}

/// Helpers ///

func kvTestStorageSchema() map[string]storage.Classification {
	return map[string]storage.Classification{
		storage.KeyLightningIncomingHTLCBatches: {ValueType: &storage.StringType{}},
	}
}

func (s *EnforcerTestSuite) resetKvStorage() {
	for key := range kvTestStorageSchema() {
		s.NoError(s.kv.Delete(key))
	}
}

// fundingOutput represents a spendable taproot output from a confirmed parent transaction.
// Recall and enforcement transactions use these as inputs.
type fundingOutput struct {
	txHex  string
	vout   uint32
	amount float64
	tap    *taprootOutput
}

type batchTestData struct {
	batch          *model.IncomingHTLCBatch
	recalls        []recallTestData
	enforcements   []enforcementTestData
	fundingOutputs []fundingOutput
}

type recallTestData struct {
	recallTx                *model.Transaction
	recallIncomingSuccessTx *model.Transaction
	firstStageOutputs       []fundingOutput
}

type enforcementTestData struct {
	enforcementTx                *model.Transaction
	enforcementIncomingSuccessTx *model.Transaction
	firstStageOutputs            []fundingOutput
}

// registerBatch creates and registers the default batch: 1 recall, 1 enforcement.
func (s *EnforcerTestSuite) registerBatch() batchTestData {
	s.T().Helper()
	return s.registerBatchWith(1, 1)
}

// registerBatchWith creates and registers a batch with the given number of recalls and
// enforcements. All recalls and enforcements share the same funding outputs so they are
// competing transactions.
// Synchronizes electrum with bitcoind first so the enforcer's height check agrees with the
// registration height.
func (s *EnforcerTestSuite) registerBatchWith(
	numRecalls, numEnforcements int,
) batchTestData {
	s.T().Helper()

	fundingOutputs := s.createFundingOutputs(1)

	recalls := make([]*model.IncomingHTLCRecall, numRecalls)
	recallData := make([]recallTestData, numRecalls)
	for i := range numRecalls {
		recalls[i], recallData[i] = s.buildRecall(fundingOutputs, testDefaultTxFee)
	}

	enforcements := make([]*model.IncomingHTLCEnforcement, numEnforcements)
	enforcementData := make([]enforcementTestData, numEnforcements)
	for i := range numEnforcements {
		enforcements[i], enforcementData[i] = s.buildEnforcement(fundingOutputs, testDefaultTxFee)
	}

	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	batch, err := s.registerIncomingHTLCBatch.Run(
		recalls,
		enforcements,
	)
	s.NoError(err, "RegisterIncomingHTLCBatch failed")

	return batchTestData{
		batch:          batch,
		recalls:        recallData,
		enforcements:   enforcementData,
		fundingOutputs: fundingOutputs,
	}
}

// buildRecall creates a recall transaction pair spending from the given funding outputs.
func (s *EnforcerTestSuite) buildRecall(
	fundingOutputs []fundingOutput,
	fee float64,
) (*model.IncomingHTLCRecall, recallTestData) {
	s.T().Helper()

	recallTxHex, recallIncomingSuccessTxHex, firstStageOutputs := s.createTxPair(
		fundingOutputs, fee,
	)
	recallTx, err := model.NewTransaction(recallTxHex)
	s.NoError(err, "deserialize recall tx")
	recallIncomingSuccessTx, err := model.NewTransaction(recallIncomingSuccessTxHex)
	s.NoError(err, "deserialize recall-incoming-success tx")

	recall := model.NewIncomingHTLCRecall(*recallTx, *recallIncomingSuccessTx)
	return recall, recallTestData{
		recallTx:                recallTx,
		recallIncomingSuccessTx: recallIncomingSuccessTx,
		firstStageOutputs:       firstStageOutputs,
	}
}

// buildEnforcement creates an enforcement transaction pair spending from the given funding outputs.
func (s *EnforcerTestSuite) buildEnforcement(
	fundingOutputs []fundingOutput,
	fee float64,
) (
	*model.IncomingHTLCEnforcement,
	enforcementTestData,
) {
	s.T().Helper()

	enforcementTxHex, enforcementIncomingSuccessTxHex, firstStageOutputs := s.createTxPair(
		fundingOutputs, fee,
	)
	enforcementTx, err := model.NewTransaction(enforcementTxHex)
	s.NoError(err, "deserialize enforcement tx")
	enforcementIncomingSuccessTx, err := model.NewTransaction(
		enforcementIncomingSuccessTxHex,
	)
	s.NoError(err, "deserialize enforcement-incoming-success tx")

	enforcement := model.NewIncomingHTLCEnforcement(
		*enforcementTx,
		*enforcementIncomingSuccessTx,
	)
	return enforcement, enforcementTestData{
		enforcementTx:                enforcementTx,
		enforcementIncomingSuccessTx: enforcementIncomingSuccessTx,
		firstStageOutputs:            firstStageOutputs,
	}
}

func (s *EnforcerTestSuite) newTaprootOutput() *taprootOutput {
	s.T().Helper()

	internalKey, err := secp256k1.GeneratePrivateKey()
	s.NoError(err, "generate internal key")

	scriptPathKey, err := secp256k1.GeneratePrivateKey()
	s.NoError(err, "generate script path key")

	builder := txscript.NewScriptBuilder()
	builder.AddData(schnorr.SerializePubKey(scriptPathKey.PubKey()))
	builder.AddOp(txscript.OP_CHECKSIG)
	leafScript, err := builder.Script()
	s.NoError(err, "build leaf script")

	leaf := txscript.NewBaseTapLeaf(leafScript)
	tapTree := txscript.AssembleTaprootScriptTree(leaf)
	rootHash := tapTree.RootNode.TapHash()

	outputKey := txscript.ComputeTaprootOutputKey(internalKey.PubKey(), rootHash[:])
	pkScript, err := txscript.PayToTaprootScript(outputKey)
	s.NoError(err, "build P2TR script")

	return &taprootOutput{
		internalKey:   internalKey,
		scriptPathKey: scriptPathKey,
		leaf:          leaf,
		tapTree:       tapTree,
		outputKey:     outputKey,
		pkScript:      pkScript,
	}
}

// toSats converts the amount of received bitcoins to satoshis.
func toSats(btc float64) int64 {
	return int64(math.Round(btc * 1e8))
}

// createTxPair creates a first-stage transaction spending from the given funding outputs with
// 2 taproot outputs, and a second-stage transaction that spends from the first stage's output 0
// via key-spend.
func (s *EnforcerTestSuite) createTxPair(
	fundingOutputs []fundingOutput,
	fee float64,
) (string, string, []fundingOutput) {
	s.T().Helper()

	firstStageOutputs := s.createSpendTx(fundingOutputs, 2, fee)
	secondStageOutputs := s.createSpendTx(firstStageOutputs[:1], 1, fee)

	return firstStageOutputs[0].txHex, secondStageOutputs[0].txHex, firstStageOutputs
}

// createFundingOutputs creates a confirmed transaction with `count` taproot outputs and returns
// them as funding outputs that recall/enforcement transactions can spend.
func (s *EnforcerTestSuite) createFundingOutputs(count int) []fundingOutput {
	s.T().Helper()

	sourceAddr := s.bitcoind.GetNewAddress("funding-source")
	s.bitcoind.SendToAddress(sourceAddr, 0.01)
	s.bitcoind.GenerateBlocks(1)

	utxos := s.bitcoind.ListUnspentForAddress(sourceAddr)
	s.NotEmpty(utxos, "No UTXOs found for address %s", sourceAddr.EncodeAddress())
	utxo := utxos[0]

	inputs := []testbitcoind.Outpoint{utxo.Outpoint}
	s.bitcoind.LockUnspent(inputs)
	s.T().Cleanup(func() { s.bitcoind.UnlockUnspent(inputs) })

	totalAfterFee := utxo.Amount - 0.0001
	perOutput := totalAfterFee / float64(count)

	tapOutputs := make([]*taprootOutput, count)
	txOutputs := make([]testbitcoind.CreateRawTxOutput, count)
	for i := range count {
		tap := s.newTaprootOutput()
		tapOutputs[i] = tap

		destAddr, err := btcutilw.NewAddressTaprootKey(
			schnorr.SerializePubKey(tap.outputKey),
			&chaincfg.RegressionNetParams,
		)
		s.NoError(err, "create taproot address for funding output %d", i)
		txOutputs[i] = testbitcoind.CreateRawTxOutput{Address: destAddr, Amount: perOutput}
	}

	rawTxHex := s.bitcoind.CreateRawTransaction(inputs, txOutputs)
	signedTxHex := s.bitcoind.SignTransaction(rawTxHex, nil)

	s.bitcoind.MustSendRawTransaction(signedTxHex)
	s.bitcoind.GenerateBlocks(1)

	result := make([]fundingOutput, count)
	for i, tap := range tapOutputs {
		result[i] = fundingOutput{
			txHex:  signedTxHex,
			vout:   uint32(i),
			amount: perOutput,
			tap:    tap,
		}
	}
	return result
}

// createSpendTx creates a transaction that spends all the given funding outputs via key-spend
// and produces numOutputs new taproot outputs. Returns the outputs as fundingOutputs.
func (s *EnforcerTestSuite) createSpendTx(
	inputs []fundingOutput,
	numOutputs int,
	fee float64,
) []fundingOutput {
	s.T().Helper()

	tx := wire.NewMsgTx(2)
	prevFetcher := txscript.NewMultiPrevOutFetcher(nil)
	var totalInput float64

	// Add all the funding outputs that this Tx will spend from.
	for _, fo := range inputs {
		parentTx := s.bitcoind.DecodeTx(fo.txHex)
		outpoint := wire.OutPoint{Hash: parentTx.TxHash(), Index: fo.vout}

		tx.AddTxIn(&wire.TxIn{
			PreviousOutPoint: outpoint,
			Sequence:         wire.MaxTxInSequenceNum,
		})
		prevFetcher.AddPrevOut(outpoint, &wire.TxOut{
			Value:    toSats(fo.amount),
			PkScript: fo.tap.pkScript,
		})
		totalInput += fo.amount
	}

	// Add all the outputs for this Tx.
	totalAfterFee := totalInput - fee
	perOutput := totalAfterFee / float64(numOutputs)
	tapOutputs := make([]*taprootOutput, numOutputs)
	for i := range numOutputs {
		tap := s.newTaprootOutput()
		tapOutputs[i] = tap
		tx.AddTxOut(&wire.TxOut{Value: toSats(perOutput), PkScript: tap.pkScript})
	}

	// Traverse the inputs again to sign them now that we have the outputs in place.
	sigHashes := txscript.NewTxSigHashes(tx, prevFetcher)
	for i, fo := range inputs {
		sigHash, err := txscript.CalcTaprootSignatureHash(
			sigHashes, txscript.SigHashDefault, tx, i, prevFetcher,
		)
		s.NoError(err, "calc sighash for input %d", i)

		rootHash := fo.tap.tapTree.RootNode.TapHash()
		tweakedKey := txscript.TweakTaprootPrivKey(*fo.tap.internalKey, rootHash[:])
		sig, err := schnorr.Sign(tweakedKey, sigHash)
		s.NoError(err, "sign input %d", i)

		tx.TxIn[i].Witness = wire.TxWitness{sig.Serialize()}
	}

	// We will return the outputs of this Tx that can be used for new news to spend from them.
	var buf bytes.Buffer
	s.NoError(tx.Serialize(&buf), "serialize spend tx")
	txHex := hex.EncodeToString(buf.Bytes())
	result := make([]fundingOutput, numOutputs)
	for i, tap := range tapOutputs {
		result[i] = fundingOutput{txHex: txHex, vout: uint32(i), amount: perOutput, tap: tap}
	}
	return result
}

// createConflictingTx builds a transaction that double-spends the input at inputIndex of the
// given transaction, signing with the taproot key material from the matching funding output.
func (s *EnforcerTestSuite) createConflictingTx(
	conflictingRawTx string,
	conflictingInputIndex int,
	parentOutputs []fundingOutput,
	fee float64,
) string {
	s.T().Helper()

	decoded := s.bitcoind.DecodeRawTransaction(conflictingRawTx)
	input := decoded.Vin[conflictingInputIndex]

	var fo fundingOutput
	for _, candidate := range parentOutputs {
		if candidate.vout == input.Vout {
			fo = candidate
			break
		}
	}
	s.NotNil(fo.tap, "no funding output with vout %d", input.Vout)

	parentTx := s.bitcoind.DecodeTx(fo.txHex)

	destAddr := s.bitcoind.GetNewAddress("conflicting-dest")
	destScript, err := txscript.PayToAddrScript(destAddr)
	s.NoError(err, "build conflicting dest script")

	conflictTx := wire.NewMsgTx(2)
	conflictTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{
			Hash:  parentTx.TxHash(),
			Index: fo.vout,
		},
		Sequence: wire.MaxTxInSequenceNum,
	})
	conflictTx.AddTxOut(&wire.TxOut{
		Value:    toSats(fo.amount - fee),
		PkScript: destScript,
	})

	prevOut := &wire.TxOut{
		Value:    toSats(fo.amount),
		PkScript: fo.tap.pkScript,
	}
	prevFetcher := txscript.NewCannedPrevOutputFetcher(prevOut.PkScript, prevOut.Value)
	sigHashes := txscript.NewTxSigHashes(conflictTx, prevFetcher)

	sigHash, err := txscript.CalcTaprootSignatureHash(
		sigHashes, txscript.SigHashDefault, conflictTx, 0, prevFetcher,
	)
	s.NoError(err, "calc conflicting tx sighash")

	rootHash := fo.tap.tapTree.RootNode.TapHash()
	tweakedKey := txscript.TweakTaprootPrivKey(*fo.tap.internalKey, rootHash[:])
	sig, err := schnorr.Sign(tweakedKey, sigHash)
	s.NoError(err, "schnorr sign conflicting tx")

	conflictTx.TxIn[0].Witness = wire.TxWitness{sig.Serialize()}

	var buf bytes.Buffer
	s.NoError(conflictTx.Serialize(&buf), "serialize conflicting tx")
	return hex.EncodeToString(buf.Bytes())
}

func (s *EnforcerTestSuite) assertBatchExists(batchID string) *model.IncomingHTLCBatch {
	s.T().Helper()

	batch, err := s.batchRepository.FindByID(batchID)
	s.NoError(err)
	s.NotNil(batch)

	return batch
}

func (s *EnforcerTestSuite) assertRecallNoneBroadcasted(batchID string, recallIdx int) {
	s.T().Helper()

	batch := s.assertBatchExists(batchID)

	recall := batch.Recalls[recallIdx]
	s.bitcoind.AssertTxIsNotBroadcasted(recall.Recall.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(recall.RecallIncomingSuccess.GetID())
}

func (s *EnforcerTestSuite) assertRecallBothBroadcasted(batchID string, recallIdx int) {
	s.T().Helper()

	batch := s.assertBatchExists(batchID)
	recall := batch.Recalls[recallIdx]

	s.bitcoind.AssertTxIsBroadcasted(recall.Recall.GetID())
	s.bitcoind.AssertTxIsBroadcasted(recall.RecallIncomingSuccess.GetID())
}

func (s *EnforcerTestSuite) assertEnforcementNoneBroadcasted(batchID string, enforcementIdx int) {
	s.T().Helper()

	batch := s.assertBatchExists(batchID)
	enforcement := batch.Enforcements[enforcementIdx]

	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.Enforcement.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.EnforcementIncomingSuccess.GetID())
}

func (s *EnforcerTestSuite) assertEnforcementBothBroadcasted(batchID string, enforcementIdx int) {
	s.T().Helper()

	batch := s.assertBatchExists(batchID)
	enforcement := batch.Enforcements[enforcementIdx]

	s.bitcoind.AssertTxIsBroadcasted(enforcement.Enforcement.GetID())
	s.bitcoind.AssertTxIsBroadcasted(enforcement.EnforcementIncomingSuccess.GetID())
}

// electrumFailingClient wraps an electrum.Client, intercepting Broadcast calls. If the raw Tx
// hex matches an entry in rawTxsToBroadcast, the mapped error is returned. Otherwise the call
// is delegated to the embedded client.
type electrumFailingClient struct {
	electrum.Client
	rawTxsToBroadcast map[string]error
}

func (e *electrumFailingClient) Broadcast(ctx context.Context, rawTx string) (string, error) {
	if err, ok := e.rawTxsToBroadcast[rawTx]; ok {
		return "", err
	}
	return e.Client.Broadcast(ctx, rawTx)
}
