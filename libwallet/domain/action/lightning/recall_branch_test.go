package lightning_test

import (
	"testing"

	"github.com/muun/libwallet/domain/action/lightning"
	"github.com/muun/libwallet/platform/test/testsuite"
)

func TestRecallBranchEnforcer_Integration(t *testing.T) {
	testsuite.Run(t, new(RecallBranchEnforcerTestSuite))
}

// RecallBranchEnforcerTestSuite covers enforcement scenarios via the recall path.
type RecallBranchEnforcerTestSuite struct {
	EnforcerTestSuite
}

// TestSettlesAfterSixConfirmations covers the full recall-branch lifecycle: preimage
// revealed, recall on-chain, RIS broadcast, six confirmations, batch deleted.
func (s *RecallBranchEnforcerTestSuite) TestSettlesAfterSixConfirmations() {
	data := s.registerBatch()
	batch := data.batch
	recall := data.recalls[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, data.batch.UUID))

	s.bitcoind.MustSendRawTransaction(recall.recallTx.ToRawHex())
	s.electrum.WaitForIndexedTx(s.Ctx, recall.recallTx.GetID())

	// First run broadcasts the RIS.
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertRecallBothBroadcasted(batch.UUID, 0)

	// Mine 2 blocks. Batch should still exist.
	s.bitcoind.GenerateBlocks(2)
	s.electrum.WaitForIndexedTx(s.Ctx, recall.recallIncomingSuccessTx.GetID())
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertRecallBothBroadcasted(batch.UUID, 0)

	// Mine 4 more blocks to reach 6 total confirmations.
	s.bitcoind.GenerateBlocks(4)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// Enforcer should detect >= 6 confirmations and remove the batch.
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	batch, err := s.batchRepository.FindByID(data.batch.UUID)
	s.NoError(err, "FindByID failed")
	s.Nil(batch, "batch should be deleted once RIS settles")
}

// TestRebroadcastsDroppedRIS covers the RIS drop and re-broadcast path.
func (s *RecallBranchEnforcerTestSuite) TestRebroadcastsDroppedRIS() {
	data := s.registerBatch()
	batch := data.batch
	recall := data.recalls[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, data.batch.UUID))

	s.bitcoind.MustSendRawTransaction(recall.recallTx.ToRawHex())
	s.electrum.WaitForIndexedTx(s.Ctx, recall.recallTx.GetID())

	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertRecallBothBroadcasted(batch.UUID, 0)

	// Drop RIS
	s.bitcoind.DropMempoolEntry(recall.recallIncomingSuccessTx.GetID())
	// Force electrum to sync new mempool
	s.bitcoind.GenerateBlocks(1)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	s.bitcoind.AssertTxIsBroadcasted(recall.recallTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallIncomingSuccessTx.GetID())

	// Run enforcer. Should detect the missing RIS and re-broadcast it.
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertRecallBothBroadcasted(batch.UUID, 0)
}

// TestPivotsToSecondRecallAfterFirstDropped tests that when a broadcasted recall is dropped from
// the mempool, the enforcer detects it and pivots to the other recall variant.
func (s *RecallBranchEnforcerTestSuite) TestPivotsToSecondRecallAfterFirstDropped() {
	data := s.registerBatchWith(2, 1)
	batch := data.batch
	recall0 := data.recalls[0]
	recall1 := data.recalls[1]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// Broadcast recall0; enforcer finds it and broadcasts RIS0.
	s.bitcoind.MustSendRawTransaction(recall0.recallTx.ToRawHex())
	s.electrum.WaitForIndexedTx(s.Ctx, recall0.recallTx.GetID())

	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertRecallBothBroadcasted(batch.UUID, 0)
	s.assertRecallNoneBroadcasted(batch.UUID, 1)

	// Drop recall0 (and its child RIS0) from the mempool.
	s.bitcoind.DropMempoolEntry(recall0.recallTx.GetID())
	s.bitcoind.GenerateBlocks(1)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	s.bitcoind.AssertTxIsNotBroadcasted(recall0.recallTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(recall0.recallIncomingSuccessTx.GetID())

	// Broadcast recall1 so the enforcer can discover it.
	s.bitcoind.MustSendRawTransaction(recall1.recallTx.ToRawHex())
	s.electrum.WaitForIndexedTx(s.Ctx, recall1.recallTx.GetID())

	// Enforcer detects recall0 was dropped, resets its state, finds recall1, and broadcasts RIS1.
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertRecallNoneBroadcasted(batch.UUID, 0)
	s.assertRecallBothBroadcasted(batch.UUID, 1)
}

// TestErrorsOnFailedRISRebroadcast covers the RIS dropped and failed re-broadcast path.
func (s *RecallBranchEnforcerTestSuite) TestErrorsOnFailedRISRebroadcast() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	recall := data.recalls[0]

	// Broadcast recall
	s.bitcoind.MustSendRawTransaction(recall.recallTx.ToRawHex())
	s.electrum.WaitForIndexedTx(s.Ctx, recall.recallTx.GetID())

	s.NoError(s.revealPreimages.Run(s.Ctx, data.batch.UUID))

	// RIS broadcast
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertRecallBothBroadcasted(batch.UUID, 0)

	// Double spend RIS.
	conflictHex := s.createConflictingTx(
		recall.recallIncomingSuccessTx.ToRawHex(),
		0,
		recall.firstStageOutputs,
		0.0002,
	)
	s.bitcoind.MustSendRawTransaction(conflictHex)
	s.bitcoind.GenerateBlocks(1)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// Recall stays on-chain, RIS rebroadcast fails (output already spent).
	var eisErr *lightning.BroadcastRecallIncomingSuccessError
	s.ErrorAs(s.publishEnforcer.Run(s.Ctx), &eisErr)

	s.assertBatchExists(batch.UUID)
	s.bitcoind.AssertTxIsBroadcasted(recall.recallTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallIncomingSuccessTx.GetID())
}
