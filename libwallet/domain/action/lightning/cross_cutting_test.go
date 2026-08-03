package lightning_test

import (
	"testing"

	"github.com/muun/libwallet/platform/test/testsuite"
)

func TestCrossCuttingEnforcer_Integration(t *testing.T) {
	testsuite.Run(t, new(CrossCuttingEnforcerTestSuite))
}

// CrossCuttingEnforcerTestSuite covers scenarios that involve both the recall and expiration paths.
type CrossCuttingEnforcerTestSuite struct {
	EnforcerTestSuite
}

// TestBatchDeletedWhenServerCooperates covers the happy path where the server cooperates: both
// branches' triggers are met (recall on-chain + preimages revealed), but Complete has already
// deleted the batch, so the enforcer must not act on either branch.
func (s *CrossCuttingEnforcerTestSuite) TestBatchDeletedWhenServerCooperates() {
	data := s.registerBatchWith(1, 1)
	recall := data.recalls[0]
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, data.batch.UUID))

	s.bitcoind.MustSendRawTransaction(recall.recallTx.ToRawHex())
	s.electrum.WaitForIndexedTx(s.Ctx, recall.recallTx.GetID())

	s.NoError(s.completeIncomingHTLCBatch.Run(s.Ctx, data.batch.UUID))

	s.NoError(s.publishEnforcer.Run(s.Ctx))

	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallIncomingSuccessTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementIncomingSuccessTx.GetID())

	batch, err := s.batchRepository.FindByID(data.batch.UUID)
	s.NoError(err)
	s.Nil(batch)
}

// TestNoOpWithoutPreimage asserts the enforcer does not act on either branch when the preimage
// has not been revealed, even when both branches' triggers are met (recall on-chain).
func (s *CrossCuttingEnforcerTestSuite) TestNoOpWithoutPreimage() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	recall := data.recalls[0]

	s.bitcoind.MustSendRawTransaction(recall.recallTx.ToRawHex())
	s.electrum.WaitForIndexedTx(s.Ctx, recall.recallTx.GetID())

	s.NoError(s.publishEnforcer.Run(s.Ctx))

	s.bitcoind.AssertTxIsNotBroadcasted(recall.recallIncomingSuccessTx.GetID())
	s.assertEnforcementNoneBroadcasted(batch.UUID, 0)
}

// TestRecallTakesPrecedenceAfterEnforcementBroadcast asserts that when a recall appears on-chain
// after the enforcer has already broadcasted the enforcement pair, the next run pivots to the
// recall branch.
func (s *CrossCuttingEnforcerTestSuite) TestRecallTakesPrecedenceAfterEnforcementBroadcast() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	recall := data.recalls[0]
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// First run: expiration branch broadcasts enforcement + EIS.
	s.NoError(s.publishEnforcer.Run(s.Ctx))

	s.assertRecallNoneBroadcasted(batch.UUID, 0)
	s.assertEnforcementBothBroadcasted(batch.UUID, 0)

	// Drop enforcement (and EIS).
	s.bitcoind.DropMempoolEntry(enforcement.enforcementTx.GetID())
	// Force electrum to sync new mempool
	s.bitcoind.GenerateBlocks(1)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// Server broadcasts the recall after the enforcement has already been dropped.
	s.bitcoind.MustSendRawTransaction(recall.recallTx.ToRawHex())
	s.electrum.WaitForIndexedTx(s.Ctx, recall.recallTx.GetID())

	// Next enforcer run: recall branch takes precedence, RIS is broadcast, expiration branch is
	// not re-entered.
	s.NoError(s.publishEnforcer.Run(s.Ctx))

	s.assertEnforcementNoneBroadcasted(batch.UUID, 0)
	s.assertRecallBothBroadcasted(batch.UUID, 0)
}
