package lightning

import (
	"context"
	"time"

	"github.com/go-errors/errors"

	data "github.com/muun/libwallet/data/lightning"
	"github.com/muun/libwallet/platform/concurrency/memlock"
)

// RevealPreimagesAction marks the preimage for the given paymentHash as having been revealed.
type RevealPreimagesAction interface {
	Run(ctx context.Context, batchID string) error
}

type revealPreimagesAction struct {
	locker          *memlock.NamedLocker
	batchRepository data.IncomingHTLCBatchRepository
}

func NewRevealPreimagesAction(
	locker *memlock.NamedLocker,
	batchRepository data.IncomingHTLCBatchRepository,
) RevealPreimagesAction {
	return &revealPreimagesAction{locker: locker, batchRepository: batchRepository}
}

func (a *revealPreimagesAction) Run(ctx context.Context, batchID string) error {
	lock, err := a.locker.Acquire(ctx, "Enforcer:IncomingHTLCBatch:"+batchID, 200*time.Millisecond)
	defer lock.Release()
	if err != nil {
		return err
	}

	batch, err := a.batchRepository.FindByID(batchID)
	if err != nil {
		return errors.Errorf("find HTLC batch: %w", err)
	}
	if batch == nil {
		return errors.Errorf("HTLC batch not found for UUID: %s", batchID)
	}

	if batch.PreimagesRevealed {
		// We're only keeping an idempotent behavior if the action is called twice but
		// the client shouldn't invoke this action after the images have been revealed.
		return nil
	}

	batch.PreimagesRevealed = true
	if err := a.batchRepository.Save(batch); err != nil {
		return errors.Errorf("update HTLC state: %w", err)
	}

	return nil
}
