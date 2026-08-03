package lightning

import (
	"context"
	"time"

	"github.com/go-errors/errors"

	data "github.com/muun/libwallet/data/lightning"
	model "github.com/muun/libwallet/domain/model/lightning"
	electrum "github.com/muun/libwallet/electrum/v2"
	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/platform/observability/slogctx"
)

const settledConfirmations = 6

// PublishEnforcerAction enforces every tracked incoming HTLC batch.
//
// It has two branches that can trigger:
//   - Recall branch: a recall transaction is detected on-chain -> the enforcer broadcasts the
//     corresponding recall-incoming-success second stage and waits for it to settle.
//   - Expiration branch: no recall is on-chain -> the enforcer broadcasts the enforcement
//     transaction and its enforcement-incoming-success second stage.
//
// In both branches the batch is considered settled once the second-stage transaction reaches
// settledConfirmations, and a settled batch is deleted.
// The recall branch takes precedence whenever a recall is on-chain: the expiration branch is only
// attempted if no recall is visible, or if the recall branch errored.
type PublishEnforcerAction interface {
	Run(ctx context.Context) error
}

type publishEnforcerAction struct {
	locker            *memlock.NamedLocker
	electrumClient    electrum.Client
	batchRepository   data.IncomingHTLCBatchRepository
	externalTxChecker ExternalTxCheckerAction
}

func NewPublishEnforcerAction(
	locker *memlock.NamedLocker,
	electrumClient electrum.Client,
	batchRepository data.IncomingHTLCBatchRepository,
	externalTxChecker ExternalTxCheckerAction,
) PublishEnforcerAction {
	return &publishEnforcerAction{
		locker:            locker,
		electrumClient:    electrumClient,
		batchRepository:   batchRepository,
		externalTxChecker: externalTxChecker,
	}
}

func (a *publishEnforcerAction) Run(ctx context.Context) error {
	allBatches, err := a.batchRepository.FindAll()
	if err != nil {
		return errors.Errorf("find all HTLCs: %w", err)
	}

	currentHeight, err := a.electrumClient.GetBestBlockHeight(ctx)
	if err != nil {
		return errors.Errorf("get current block height: %w", err)
	}

	var errs []error
	for batchID := range allBatches {
		if err = a.processHTLCBatch(ctx, currentHeight, batchID); err != nil {
			errs = append(errs, errors.Errorf("HTLC batch %s: %w", batchID, err))
		}
	}

	return errors.Join(errs...)
}

// processHTLCBatch enforces a single HTLC batch under a lock.
// On successful settlement the batch is deleted.
// Otherwise, any batch state modification produced during the run is persisted,
// including on error paths.
func (a *publishEnforcerAction) processHTLCBatch(
	ctx context.Context,
	currentHeight int,
	batchID string,
) error {
	lock, err := a.locker.Acquire(ctx, "Enforcer:IncomingHTLCBatch:"+batchID, 200*time.Millisecond)
	defer lock.Release()
	if err != nil {
		return err
	}

	batch, err := a.batchRepository.FindByID(batchID)

	// We can only continue if we have a batch for which we've revealed its pre-images
	if err != nil || batch == nil || !batch.PreimagesRevealed {
		return err
	}

	chainStatus, err := a.buildBatchChainStatus(ctx, currentHeight, batch)
	if err != nil {
		return err
	}

	settled, err := a.enforceHTLCBatch(ctx, chainStatus, batch)
	if settled {
		deleteErr := a.batchRepository.DeleteByID(batch.UUID)
		return errors.Join(err, deleteErr)
	}

	// Save any state modifications produced, even if there was an error midway.
	if saveErr := a.batchRepository.Save(batch); saveErr != nil {
		if err != nil {
			return errors.Errorf(
				"save HTLC batch failed (%w) while handling previous error: %w",
				saveErr, err,
			)
		}
		return errors.Errorf("save HTLC batch failed: %w", saveErr)
	}

	return err
}

// buildBatchChainStatus builds the current chain status for the given batch
func (a *publishEnforcerAction) buildBatchChainStatus(
	ctx context.Context,
	currentHeight int,
	batch *model.IncomingHTLCBatch,
) (*chainStatus, error) {
	histories, err := a.fetchBatchScriptHashHistories(ctx, batch)
	if err != nil {
		return nil, err
	}

	chainStatus := newChainStatus(currentHeight)

	for _, history := range histories {
		chainStatus.addScriptHashHistory(history)
	}

	return chainStatus, nil
}

// fetchBatchScriptHashHistories makes a batched request to Electrum to get all the
// missing script hash histories for the given batch.
func (a *publishEnforcerAction) fetchBatchScriptHashHistories(
	ctx context.Context,
	batch *model.IncomingHTLCBatch,
) (map[string][]electrum.ScriptHashHistoryEntry, error) {
	outputScripts := batch.GetAllOutputScripts()

	if len(outputScripts) == 0 {
		return nil, nil
	}

	scriptHashes := make([]string, len(outputScripts))
	for i, script := range outputScripts {
		scriptHashes[i] = electrum.GetScriptHash(script)
	}

	results, err := a.electrumClient.GetScriptHashHistoryBatch(ctx, scriptHashes)
	if err != nil {
		return nil, errors.Errorf(
			"get script hash history (batched) for transactions of batch %s failed: %w",
			batch.UUID, err,
		)
	}

	histories := make(map[string][]electrum.ScriptHashHistoryEntry, len(results))
	for key, result := range results {
		entries, err := result.Unwrap()
		if err != nil {
			return nil, errors.Errorf(
				"script hash history entry for %s in batch %s failed: %w",
				key, batch.UUID, err,
			)
		}
		histories[key] = entries
	}

	return histories, nil
}

// enforceHTLCBatch drives the enforcement, running the recall and expiration branches for a single
// batch. Returns whether the batch has settled, meaning any second stage is settled or
// that enforcement of this batch is no longer possible.
func (a *publishEnforcerAction) enforceHTLCBatch(
	ctx context.Context,
	chainStatus *chainStatus,
	batch *model.IncomingHTLCBatch,
) (bool, error) {
	// Enforce recall path
	settled, enforceRecallsErr := a.enforceRecall(ctx, chainStatus, batch)
	if enforceRecallsErr == nil && settled {
		return settled, nil
	}

	// Recall path takes precedence over the expiration path.
	// If a recall was found, we are already enforcing that path, so we finish.
	// If there was an error, then we can't be sure, so we try to enforce anyway.
	if enforceRecallsErr == nil {
		for _, recall := range batch.Recalls {
			if chainStatus.isPresent(recall.Recall) {
				return false, nil
			}
		}
	}

	// Enforce expiration path
	settled, enforceExpirationErr := a.enforceExpiration(ctx, chainStatus, batch)
	if enforceExpirationErr == nil {
		// The expiration branch succeeded, so we discard any lingering recall branch errors.
		// The enforcer made forward progress, and retrying the recall branch on the next run will
		// surface the error again if it's still relevant.
		return settled, nil
	}

	enforcementFailed, noEnforcementPossibleErr := a.checkNoEnforcementPossible(
		ctx, chainStatus, batch, enforceRecallsErr, enforceExpirationErr,
	)
	if noEnforcementPossibleErr != nil {
		return false, noEnforcementPossibleErr
	}
	if enforcementFailed {
		// We can no longer enforce this batch.
		return true, newSettledExternalSpendError(batch)
	}

	return false, errors.Join(enforceRecallsErr, enforceExpirationErr)
}

// enforceRecall drives the recall branch state machine.
func (a *publishEnforcerAction) enforceRecall(
	ctx context.Context,
	chainStatus *chainStatus,
	batch *model.IncomingHTLCBatch,
) (bool, error) {
	var broadcastedRecall *model.IncomingHTLCRecall
	for _, recall := range batch.Recalls {
		if chainStatus.isPresent(recall.Recall) {
			broadcastedRecall = recall
		}
	}

	if broadcastedRecall == nil {
		// No recall broadcasted
		return false, nil
	}

	recallConfirmations := chainStatus.getConfirmations(broadcastedRecall.Recall)

	recallIncomingSuccessPresent := chainStatus.isPresent(
		broadcastedRecall.RecallIncomingSuccess,
	)
	recallIncomingSuccessConfirmations := chainStatus.getConfirmations(
		broadcastedRecall.RecallIncomingSuccess,
	)

	slogctx.Info(ctx,
		"Recall transaction found on-chain. Executing enforcer's recall branch.",
		"batch", batch.UUID,
		"recall", broadcastedRecall.Recall.GetID(),
		"recallConfirmations", recallConfirmations,
		"recallIncomingSuccess", broadcastedRecall.RecallIncomingSuccess.GetID(),
		"recallIncomingSuccessConfirmations", recallIncomingSuccessConfirmations,
	)

	if recallIncomingSuccessConfirmations >= settledConfirmations {
		// Recall Incoming Success settled
		return true, nil
	}

	if !recallIncomingSuccessPresent {
		// Recall Incoming Success needs to be broadcasted
		_, err := a.electrumClient.Broadcast(
			ctx, broadcastedRecall.RecallIncomingSuccess.ToRawHex(),
		)
		if err != nil {
			return false, newBroadcastRecallIncomingSuccessError(
				err, broadcastedRecall.RecallIncomingSuccess,
			)
		}
	}

	return false, nil
}

// enforceExpiration drives the expiration branch state machine.
func (a *publishEnforcerAction) enforceExpiration(
	ctx context.Context,
	chainStatus *chainStatus,
	batch *model.IncomingHTLCBatch,
) (bool, error) {
	var broadcastedEnforcement *model.IncomingHTLCEnforcement
	for _, enforcement := range batch.Enforcements {
		if chainStatus.isPresent(enforcement.Enforcement) {
			broadcastedEnforcement = enforcement
		}
	}

	if broadcastedEnforcement == nil {
		var err error
		if broadcastedEnforcement, err = a.broadcastValidEnforcement(ctx, batch); err != nil {
			return false, err
		}
	}

	enforcementConfirmations := chainStatus.getConfirmations(broadcastedEnforcement.Enforcement)

	enforcementIncomingSuccessPresent := chainStatus.isPresent(
		broadcastedEnforcement.EnforcementIncomingSuccess,
	)
	enforcementIncomingSuccessConfirmations := chainStatus.getConfirmations(
		broadcastedEnforcement.EnforcementIncomingSuccess,
	)

	slogctx.Info(ctx, "Executing enforcer's expiration branch.",
		"batch", batch.UUID,
		"enforcement", broadcastedEnforcement.Enforcement.GetID(),
		"enforcementConfirmations", enforcementConfirmations,
		"enforcementIncomingSuccess", broadcastedEnforcement.EnforcementIncomingSuccess.GetID(),
		"enforcementIncomingSuccessConfirmations", enforcementIncomingSuccessConfirmations,
	)

	if enforcementIncomingSuccessConfirmations >= settledConfirmations {
		// Recall Incoming Success settled
		return true, nil
	}

	if !enforcementIncomingSuccessPresent {
		// Enforcement Incoming Success needs to be broadcasted
		_, err := a.electrumClient.Broadcast(
			ctx, broadcastedEnforcement.EnforcementIncomingSuccess.ToRawHex(),
		)
		if err != nil {
			return false, newBroadcastEnforcementIncomingSuccessError(
				err, broadcastedEnforcement.EnforcementIncomingSuccess,
			)
		}
	}

	return false, nil
}

// broadcastValidEnforcement iterates the enforcement transactions in order and broadcasts
// them until one is accepted by the network. If all enforcements fail to broadcast, we fail.
func (a *publishEnforcerAction) broadcastValidEnforcement(
	ctx context.Context,
	batch *model.IncomingHTLCBatch,
) (*model.IncomingHTLCEnforcement, error) {
	var broadcastErrs []error
	var failedTxs []model.Transaction
	for _, enforcement := range batch.Enforcements {
		_, err := a.electrumClient.Broadcast(ctx, enforcement.Enforcement.ToRawHex())
		if err == nil {
			return enforcement, nil
		}

		if !errors.As(err, &electrum.ElectrumError{}) {
			// When the error is not an electrum one it signals a deeper problem, such as a connectivity
			// issue. In this scenario, don't attempt to continue with the remaining enforcements
			// but rather make an early return.
			return nil, err
		}

		broadcastErrs = append(broadcastErrs, errors.Errorf("broadcast enforcement: %w", err))
		failedTxs = append(failedTxs, enforcement.Enforcement)
	}

	return nil, newNoEnforcementBroadcastedError(
		errors.Join(broadcastErrs...), failedTxs,
	)
}

// checkNoEnforcementPossible checks if we can't possibly execute the enforcer process
// due to our Txs (RIS/Enforcement/EIS/) having had any inputs irrevocably double-spent.
func (a *publishEnforcerAction) checkNoEnforcementPossible(
	ctx context.Context,
	chainStatus *chainStatus,
	batch *model.IncomingHTLCBatch,
	errs ...error,
) (bool, error) {
	var failedTxs []model.Transaction

	for _, err := range errs {
		if err == nil {
			continue
		}

		var broadcastErr *EnforcementBroadcastError
		if errors.As(err, &broadcastErr) {
			failedTxs = append(failedTxs, broadcastErr.FailedTxs...)
		} else {
			// Got error that is not EnforcementBroadcastError, so maybe we could still enforce
			return false, nil
		}
	}

	return a.externalTxChecker.Run(ctx, chainStatus.currentHeight, batch, failedTxs)
}
