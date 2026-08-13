package lightning

import (
	"github.com/go-errors/errors"

	data "github.com/muun/libwallet/data/lightning"
	model "github.com/muun/libwallet/domain/model/lightning"
)

// RegisterIncomingHTLCAction registers an HTLC batch in pending state with
// its corresponding recalls and enforcement transactions.
type RegisterIncomingHTLCAction interface {
	Run(
		recalls []*model.IncomingHTLCRecall,
		enforcements []*model.IncomingHTLCEnforcement,
	) (*model.IncomingHTLCBatch, error)
}

type registerIncomingHTLCAction struct {
	batchRepository data.IncomingHTLCBatchRepository
}

func NewRegisterIncomingHTLCAction(
	batchRepository data.IncomingHTLCBatchRepository,
) RegisterIncomingHTLCAction {
	return &registerIncomingHTLCAction{batchRepository: batchRepository}
}

func (a *registerIncomingHTLCAction) Run(
	recalls []*model.IncomingHTLCRecall,
	enforcements []*model.IncomingHTLCEnforcement,
) (*model.IncomingHTLCBatch, error) {
	batch := model.NewIncomingHTLCBatch(
		recalls,
		enforcements,
	)

	if err := a.batchRepository.Save(batch); err != nil {
		return nil, errors.Errorf("save HTLC: %w", err)
	}

	return batch, nil
}
