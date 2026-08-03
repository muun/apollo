package lightning

import (
	"github.com/go-errors/errors"

	model "github.com/muun/libwallet/domain/model/lightning"
)

// EnforcementBroadcastError is the base error for failed enforcer Tx broadcasts.
// It carries the transactions that could not be broadcast.
type EnforcementBroadcastError struct {
	error
	FailedTxs []model.Transaction
}

type BroadcastRecallIncomingSuccessError struct {
	*EnforcementBroadcastError
}

func (e *BroadcastRecallIncomingSuccessError) Unwrap() error {
	return e.EnforcementBroadcastError
}

type BroadcastEnforcementIncomingSuccessError struct {
	*EnforcementBroadcastError
}

func (e *BroadcastEnforcementIncomingSuccessError) Unwrap() error {
	return e.EnforcementBroadcastError
}

type NoEnforcementBroadcastedError struct {
	*EnforcementBroadcastError
}

func (e *NoEnforcementBroadcastedError) Unwrap() error {
	return e.EnforcementBroadcastError
}

type SettledExternalSpendError struct{ error }

func newSettledExternalSpendError(batch *model.IncomingHTLCBatch) *SettledExternalSpendError {
	return &SettledExternalSpendError{
		errors.Errorf(
			"batch %s: parent outputs irrevocably spent externally",
			batch.UUID,
		),
	}
}

func newBroadcastRecallIncomingSuccessError(
	err error, tx model.Transaction,
) error {
	return &BroadcastRecallIncomingSuccessError{
		EnforcementBroadcastError: &EnforcementBroadcastError{
			error: errors.Errorf(
				"broadcast recall incoming success transaction: %w",
				err,
			),
			FailedTxs: []model.Transaction{tx},
		},
	}
}

func newBroadcastEnforcementIncomingSuccessError(
	err error, tx model.Transaction,
) error {
	return &BroadcastEnforcementIncomingSuccessError{
		EnforcementBroadcastError: &EnforcementBroadcastError{
			error: errors.Errorf(
				"broadcast enforcement incoming success transaction: %w",
				err,
			),
			FailedTxs: []model.Transaction{tx},
		},
	}
}

func newNoEnforcementBroadcastedError(
	err error, txs []model.Transaction,
) error {
	return &NoEnforcementBroadcastedError{
		EnforcementBroadcastError: &EnforcementBroadcastError{
			error: errors.Errorf(
				"no enforcement transaction could be broadcasted: %w",
				err,
			),
			FailedTxs: txs,
		},
	}
}
