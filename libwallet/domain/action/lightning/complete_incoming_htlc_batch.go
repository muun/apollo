package lightning

import (
	"context"
	"time"

	data "github.com/muun/libwallet/data/lightning"
	"github.com/muun/libwallet/platform/concurrency/memlock"
)

// CompleteIncomingHTLCBatchAction stores the recall revocation transaction for a
// given recall, marking the HTLC as revoked.
type CompleteIncomingHTLCBatchAction interface {
	Run(ctx context.Context, batchID string) error
}

type completeIncomingHTLCAction struct {
	locker          *memlock.NamedLocker
	batchRepository data.IncomingHTLCBatchRepository
}

func NewCompleteIncomingHTLCAction(
	locker *memlock.NamedLocker,
	batchRepository data.IncomingHTLCBatchRepository,
) CompleteIncomingHTLCBatchAction {
	return &completeIncomingHTLCAction{locker: locker, batchRepository: batchRepository}
}

func (a *completeIncomingHTLCAction) Run(ctx context.Context, batchID string) error {
	lock, err := a.locker.Acquire(ctx, "Enforcer:IncomingHTLCBatch:"+batchID, 200*time.Millisecond)
	defer lock.Release()
	if err != nil {
		return err
	}

	// The server has shared the recall revocations with us, this is the expected happy path final
	// state and there's no longer a purpose for the client to persist the incoming HTLC batch.
	return a.batchRepository.DeleteByID(batchID)
}
