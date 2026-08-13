package lightning_test

import (
	"testing"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/domain/action/lightning"
	"github.com/muun/libwallet/platform/test/testsuite"
)

func TestExternalSpendEnforcer_Integration(t *testing.T) {
	testsuite.Run(t, new(ExternalSpendEnforcerTestSuite))
}

// ExternalSpendEnforcerTestSuite cover the scenarios involved in deciding whether we should stop
// the enforcing process due to external Txs having double-spent the inputs used by our
// recall/enforcement Txs.
type ExternalSpendEnforcerTestSuite struct {
	EnforcerTestSuite
}

// TestBatchDeletedForSettledExternalTxDetected — settled competing Tx on the funding output.
func (s *ExternalSpendEnforcerTestSuite) TestBatchDeletedForSettledExternalTxDetected() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	recall := data.recalls[0]
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// A conflicting Tx double-spends the funding output, preventing both first-stage Txs.
	conflictHex := s.createConflictingTx(
		enforcement.enforcementTx.ToRawHex(),
		0,
		data.fundingOutputs,
		0.0005,
	)
	s.bitcoind.MustSendRawTransaction(conflictHex)
	s.bitcoind.GenerateBlocks(6)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// With 6 confirmations the spend is irrevocable: batch deleted, no enforcer Txs broadcast.
	var settledErr *lightning.SettledExternalSpendError
	s.ErrorAs(s.publishEnforcer.Run(s.Ctx), &settledErr)

	foundBatch, err := s.batchRepository.FindByID(batch.UUID)
	s.NoError(err)
	s.Nil(foundBatch)

	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallIncomingSuccessTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementIncomingSuccessTx.GetID())
}

// TestBatchKeptForUnsettledExternalTxDetected — unsettled competing Tx on the funding output.
func (s *ExternalSpendEnforcerTestSuite) TestBatchKeptForUnsettledExternalTxDetected() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	recall := data.recalls[0]
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// A conflicting Tx double-spends the funding output, preventing both first-stage Txs.
	conflictHex := s.createConflictingTx(
		enforcement.enforcementTx.ToRawHex(),
		0,
		data.fundingOutputs,
		0.0005,
	)
	s.bitcoind.MustSendRawTransaction(conflictHex)
	s.bitcoind.GenerateBlocks(3)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// Only 3 confirmations: spend could still be reversed, batch kept.
	var noEnfErr *lightning.NoEnforcementBroadcastedError
	s.ErrorAs(s.publishEnforcer.Run(s.Ctx), &noEnfErr)
	foundBatch, err := s.batchRepository.FindByID(batch.UUID)
	s.NoError(err)
	s.NotNil(foundBatch)

	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallIncomingSuccessTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementIncomingSuccessTx.GetID())
}

// TestBatchDeletedForSettledRecallOutputSpend — settled competing Tx on a recall output.
func (s *ExternalSpendEnforcerTestSuite) TestBatchDeletedForSettledRecallOutputSpend() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	recall := data.recalls[0]
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// Recall Tx is on-chain; an external Tx then spends the output RIS would use.
	s.bitcoind.MustSendRawTransaction(recall.recallTx.ToRawHex())
	s.bitcoind.GenerateBlocks(1)

	competingOutputs := s.createSpendTx(recall.firstStageOutputs[:1], 1, testDefaultTxFee)
	s.bitcoind.MustSendRawTransaction(competingOutputs[0].txHex)

	// With 6 confirmations the spend is irrevocable: batch deleted.
	s.bitcoind.GenerateBlocks(6)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	var settledErr *lightning.SettledExternalSpendError
	s.ErrorAs(s.publishEnforcer.Run(s.Ctx), &settledErr)

	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallIncomingSuccessTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementIncomingSuccessTx.GetID())

	foundBatch, err := s.batchRepository.FindByID(batch.UUID)
	s.NoError(err)
	s.Nil(foundBatch)
}

// TestBatchKeptForUnsettledRecallOutputSpend — unsettled competing Tx on a
// recall output (5 blocks).
func (s *ExternalSpendEnforcerTestSuite) TestBatchKeptForUnsettledRecallOutputSpend() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	recall := data.recalls[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// Broadcast the recall (first-stage) so its outputs exist on-chain.
	s.bitcoind.MustSendRawTransaction(recall.recallTx.ToRawHex())
	s.bitcoind.GenerateBlocks(1)

	// A competing transaction spends the same first-stage output that the RIS would use.
	competingOutputs := s.createSpendTx(recall.firstStageOutputs[:1], 1, testDefaultTxFee)
	s.bitcoind.MustSendRawTransaction(competingOutputs[0].txHex)

	// Mine only 5 blocks — competing Tx is not yet settled.
	s.bitcoind.GenerateBlocks(5)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// The enforcer enters the recall branch, which fails to broadcast the RIS because the output
	// is already spent. The external Tx check then finds no settled transactions.
	var noEnfErr *lightning.BroadcastRecallIncomingSuccessError
	s.ErrorAs(s.publishEnforcer.Run(s.Ctx), &noEnfErr)

	// Batch is kept — the competing Tx might still be reversed by a reorg.
	s.assertBatchExists(batch.UUID)
}

// TestBatchDeletedForSettledEnforcementOutputSpend — settled competing Tx on an enforcement output.
func (s *ExternalSpendEnforcerTestSuite) TestBatchDeletedForSettledEnforcementOutputSpend() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	recall := data.recalls[0]
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// Enforcement Tx is on-chain; an external Tx then spends the output EIS would use.
	s.bitcoind.MustSendRawTransaction(enforcement.enforcementTx.ToRawHex())
	s.bitcoind.GenerateBlocks(1)

	competingOutputs := s.createSpendTx(enforcement.firstStageOutputs[:1], 1, testDefaultTxFee)
	s.bitcoind.MustSendRawTransaction(competingOutputs[0].txHex)

	// With 6 confirmations the spend is irrevocable: batch deleted.
	s.bitcoind.GenerateBlocks(6)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	var settledErr *lightning.SettledExternalSpendError
	s.ErrorAs(s.publishEnforcer.Run(s.Ctx), &settledErr)

	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallIncomingSuccessTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementIncomingSuccessTx.GetID())

	foundBatch, err := s.batchRepository.FindByID(batch.UUID)
	s.NoError(err)
	s.Nil(foundBatch)
}

// TestBatchKeptForUnsettledEnforcementOutputSpend — unsettled competing Tx on an
// enforcement output (5 blocks).
func (s *ExternalSpendEnforcerTestSuite) TestBatchKeptForUnsettledEnforcementOutputSpend() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// Broadcast the recall (first-stage) so its outputs exist on-chain.
	s.bitcoind.MustSendRawTransaction(enforcement.enforcementTx.ToRawHex())
	s.bitcoind.GenerateBlocks(1)

	// A competing transaction spends the same first-stage output that the EIS would use.
	competingOutputs := s.createSpendTx(enforcement.firstStageOutputs[:1], 1, testDefaultTxFee)
	s.bitcoind.MustSendRawTransaction(competingOutputs[0].txHex)

	// Mine only 5 blocks — competing Tx is not yet settled.
	s.bitcoind.GenerateBlocks(5)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// The enforcer enters the expiration branch, which fails to broadcast the EIS because the output
	// is already spent. The external Tx check then finds no settled transactions.
	var noEnfErr *lightning.BroadcastEnforcementIncomingSuccessError
	s.ErrorAs(s.publishEnforcer.Run(s.Ctx), &noEnfErr)

	// Batch is kept — the competing Tx might still be reversed by a reorg.
	s.assertBatchExists(batch.UUID)
}

// TestBatchKeptForSomeTxsDontProduceBroadcastError — checks that the batch is kept
// on those situations where a path produced a broadcast error (for example recall), but
// not the other (for example enforcement Txs).
func (s *ExternalSpendEnforcerTestSuite) TestBatchKeptForSomeTxsDontProduceBroadcastError() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	recall := data.recalls[0]
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// Recall Tx is on-chain; an external Tx then spends the output RIS would use.
	s.bitcoind.MustSendRawTransaction(recall.recallTx.ToRawHex())
	s.bitcoind.GenerateBlocks(1)

	competingOutputs := s.createSpendTx(recall.firstStageOutputs[:1], 1, testDefaultTxFee)
	s.bitcoind.MustSendRawTransaction(competingOutputs[0].txHex)

	// With 6 confirmations the spend is irrevocable but we should still keep the batch because enforcement
	// broadcast will fail with a non-broadcast error.
	s.bitcoind.GenerateBlocks(6)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// Use a dummy electrum client to be able to test scenarios that produce custom errors. In this case
	// we want to fail the broadcast of an enforcement Txs with a non-electrum error, so that the batch
	// is kept alive to keep retrying.
	enforcementRawHex := enforcement.enforcementTx.ToRawHex()
	enforcementForcedError := errors.New("dummy enforcer error")
	electrumClient := &electrumFailingClient{
		Client:            s.electrum.Client,
		rawTxsToBroadcast: map[string]error{enforcementRawHex: enforcementForcedError},
	}
	publishEnforcer := lightning.NewPublishEnforcerAction(
		s.locker,
		electrumClient,
		s.batchRepository,
		lightning.NewExternalTxCheckerAction(s.electrum.Client),
	)

	var broadcastError *lightning.BroadcastRecallIncomingSuccessError
	publishEnforcerErr := publishEnforcer.Run(s.Ctx)
	s.ErrorAs(publishEnforcerErr, &broadcastError)
	s.ErrorIs(publishEnforcerErr, enforcementForcedError)

	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallIncomingSuccessTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementIncomingSuccessTx.GetID())

	foundBatch, err := s.batchRepository.FindByID(batch.UUID)
	s.NoError(err)
	s.NotNil(foundBatch)
}
