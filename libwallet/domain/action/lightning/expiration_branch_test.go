package lightning_test

import (
	"testing"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/domain/action/lightning"
	model "github.com/muun/libwallet/domain/model/lightning"
	"github.com/muun/libwallet/platform/test/testsuite"
)

func TestExpirationBranchEnforcer_Integration(t *testing.T) {
	testsuite.Run(t, new(ExpirationBranchEnforcerTestSuite))
}

// ExpirationBranchEnforcerTestSuite covers enforcement scenarios via the expiration path.
type ExpirationBranchEnforcerTestSuite struct {
	EnforcerTestSuite
}

// TestSettlesAfterSixConfirmations covers the full expiration-branch lifecycle:
// enforcement + EIS broadcast, six confirmations, batch deleted.
func (s *ExpirationBranchEnforcerTestSuite) TestSettlesAfterSixConfirmations() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// First run after revealing preimages: enforcement + EIS broadcast immediately.
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertEnforcementBothBroadcasted(batch.UUID, 0)

	// Mine 2 blocks. Batch should still exist.
	s.bitcoind.GenerateBlocks(2)
	s.electrum.WaitForIndexedTx(s.Ctx, enforcement.enforcementIncomingSuccessTx.GetID())
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertEnforcementBothBroadcasted(batch.UUID, 0)

	// Mine 4 more blocks to reach 6 total confirmations.
	s.bitcoind.GenerateBlocks(4)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// Enforcer should detect >= 6 confirmations and remove the batch.
	s.NoError(s.publishEnforcer.Run(s.Ctx))

	batch, err := s.batchRepository.FindByID(data.batch.UUID)
	s.NoError(err)
	s.Nil(batch, "batch should be deleted once EIS settles")
}

// TestRebroadcastsDroppedEnforcement covers the enforcement re-broadcast path.
func (s *ExpirationBranchEnforcerTestSuite) TestRebroadcastsDroppedEnforcement() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertEnforcementBothBroadcasted(batch.UUID, 0)

	// Drop enforcement (and EIS).
	s.bitcoind.DropMempoolEntry(enforcement.enforcementTx.GetID())
	// Force electrum to sync new mempool
	s.bitcoind.GenerateBlocks(1)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	s.assertEnforcementNoneBroadcasted(batch.UUID, 0)

	// Should detect the missing enforcement, re-broadcast it, then broadcast EIS.
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertEnforcementBothBroadcasted(batch.UUID, 0)
}

// TestRebroadcastsDroppedEIS covers the EIS re-broadcast path.
func (s *ExpirationBranchEnforcerTestSuite) TestRebroadcastsDroppedEIS() {
	data := s.registerBatchWith(1, 1)
	enforcement := data.enforcements[0]
	batch := data.batch

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertEnforcementBothBroadcasted(batch.UUID, 0)

	// Drop EIS.
	s.bitcoind.DropMempoolEntry(enforcement.enforcementIncomingSuccessTx.GetID())
	// Force electrum to sync new mempool
	s.bitcoind.GenerateBlocks(1)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	s.bitcoind.AssertTxIsBroadcasted(enforcement.enforcementTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementIncomingSuccessTx.GetID())

	// Should detect the missing EIS and re-broadcast it.
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertEnforcementBothBroadcasted(batch.UUID, 0)
}

// TestPivotsToSecondEnforcementAfterReplacement covers the enforcement replacement path.
// A higher-fee enforcement replaces the initial one via RBF, and the enforcer detects the
// replacement and broadcasts the second stage for the new enforcement.
func (s *ExpirationBranchEnforcerTestSuite) TestPivotsToSecondEnforcementAfterReplacement() {
	lowFee := 0.0001
	highFee := 0.0005

	fundingOutputs := s.createFundingOutputs(1)
	recall, _ := s.buildRecall(fundingOutputs, testDefaultTxFee)
	lowFeeEnforcement, _ := s.buildEnforcement(fundingOutputs, lowFee)
	highFeeEnforcement, highFeeData := s.buildEnforcement(fundingOutputs, highFee)

	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	batch, err := s.registerIncomingHTLCBatch.Run(
		[]*model.IncomingHTLCRecall{recall},
		[]*model.IncomingHTLCEnforcement{lowFeeEnforcement, highFeeEnforcement},
	)
	s.NoError(err)

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// Enforcer broadcasts the first enforcement (lower fee) and its EIS.
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertEnforcementBothBroadcasted(batch.UUID, 0)
	s.assertEnforcementNoneBroadcasted(batch.UUID, 1)

	// Higher-fee enforcement replaces the lower-fee one (and evicts its EIS).
	s.bitcoind.MustSendRawTransaction(highFeeData.enforcementTx.ToRawHex())
	s.bitcoind.GenerateBlocks(1)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	s.assertEnforcementNoneBroadcasted(batch.UUID, 0)

	// Enforcer detects the replacement and broadcasts EIS for the new enforcement.
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertEnforcementNoneBroadcasted(batch.UUID, 0)
	s.assertEnforcementBothBroadcasted(batch.UUID, 1)
}

// TestErrorsOnFailedEISRebroadcast covers the EIS dropped and failed re-broadcast path.
func (s *ExpirationBranchEnforcerTestSuite) TestErrorsOnFailedEISRebroadcast() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	// enforcement + EIS broadcast
	s.NoError(s.publishEnforcer.Run(s.Ctx))
	s.assertEnforcementBothBroadcasted(batch.UUID, 0)

	// Double spend EIS.
	conflictHex := s.createConflictingTx(
		enforcement.enforcementIncomingSuccessTx.ToRawHex(),
		0,
		enforcement.firstStageOutputs,
		0.0002,
	)
	s.bitcoind.MustSendRawTransaction(conflictHex)
	s.bitcoind.GenerateBlocks(1)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	// Enforcement stays on-chain, EIS rebroadcast fails (output already spent).
	var eisErr *lightning.BroadcastEnforcementIncomingSuccessError
	s.ErrorAs(s.publishEnforcer.Run(s.Ctx), &eisErr)

	s.assertBatchExists(batch.UUID)
	s.bitcoind.AssertTxIsBroadcasted(enforcement.enforcementTx.GetID())
	s.bitcoind.AssertTxIsNotBroadcasted(enforcement.enforcementIncomingSuccessTx.GetID())
}

// TestErrorsWhenNoneCanBroadcast covers the error path when no enforcement can be broadcasted.
func (s *ExpirationBranchEnforcerTestSuite) TestErrorsWhenNoneCanBroadcast() {
	data := s.registerBatchWith(1, 1)
	batch := data.batch
	enforcement := data.enforcements[0]

	s.NoError(s.revealPreimages.Run(s.Ctx, data.batch.UUID))

	conflictHex := s.createConflictingTx(
		enforcement.enforcementTx.ToRawHex(),
		0,
		data.fundingOutputs,
		0.0005,
	)
	s.bitcoind.MustSendRawTransaction(conflictHex)
	s.bitcoind.GenerateBlocks(1)
	s.electrum.SyncWithBitcoind(s.Ctx, s.bitcoind)

	var noEnfErr *lightning.NoEnforcementBroadcastedError
	s.ErrorAs(s.publishEnforcer.Run(s.Ctx), &noEnfErr)
	s.assertEnforcementNoneBroadcasted(batch.UUID, 0)
}

// TestEarlyReturnOnNonElectrumBroadcastError verifies that a non-electrum error (e.g. connectivity)
// during enforcement broadcast causes an immediate return instead of trying remaining enforcements.
func (s *ExpirationBranchEnforcerTestSuite) TestEarlyReturnOnNonElectrumBroadcastError() {
	data := s.registerBatchWith(1, 2)
	batch := data.batch

	s.NoError(s.revealPreimages.Run(s.Ctx, batch.UUID))

	enforcementRawHex := data.enforcements[0].enforcementTx.ToRawHex()
	connectivityErr := errors.New("connection refused")
	failClient := &electrumFailingClient{
		Client:            s.electrum.Client,
		rawTxsToBroadcast: map[string]error{enforcementRawHex: connectivityErr},
	}
	failEnforcer := lightning.NewPublishEnforcerAction(
		s.locker,
		failClient,
		s.batchRepository,
		lightning.NewExternalTxCheckerAction(failClient),
	)

	err := failEnforcer.Run(s.Ctx)
	s.Require().Error(err)

	// Should NOT be NoEnforcementBroadcastedError — the enforcer returned early on the
	// non-electrum error instead of exhausting all enforcements.
	var noEnfErr *lightning.NoEnforcementBroadcastedError
	s.False(errors.As(err, &noEnfErr),
		"expected direct error, not NoEnforcementBroadcastedError")
	s.ErrorContains(err, "connection refused")
}
