package data

import (
	"context"
	"sync"

	"github.com/go-errors/errors"
)

// RunCanceler allows canceling an in-flight run.
type RunCanceler struct {
	mutex  sync.Mutex
	cancel context.CancelCauseFunc
}

func NewRunCanceler() *RunCanceler {
	return &RunCanceler{}
}

func (rc *RunCanceler) SetNewRun(ctx context.Context) context.Context {
	ctx, cancelHandler := context.WithCancelCause(ctx)

	prevCancel := rc.replaceCancel(cancelHandler)
	if prevCancel != nil {
		prevCancel(newRunCanceledError(errors.Errorf("replaced by new run")))
	}

	return ctx
}

func (rc *RunCanceler) CancelRun(cause error) {
	cancel := rc.getCancel()
	if cancel != nil {
		cancel(newRunCanceledError(cause))
	}
}

// CancelCause returns the cause of a cancel made by the RunCanceler, or nil if err is not one.
func (rc *RunCanceler) CancelCause(err error) error {
	var runCanceledErr runCanceledError
	if err != nil && errors.As(err, &runCanceledErr) {
		return runCanceledErr.error
	}
	return nil
}

func (rc *RunCanceler) getCancel() context.CancelCauseFunc {
	rc.mutex.Lock()
	defer rc.mutex.Unlock()

	return rc.cancel
}

func (rc *RunCanceler) replaceCancel(cancel context.CancelCauseFunc) context.CancelCauseFunc {
	rc.mutex.Lock()
	defer rc.mutex.Unlock()

	prev := rc.cancel
	rc.cancel = cancel

	return prev
}

// runCanceledError is the cause of a run canceled through the RunCanceler.
type runCanceledError struct{ error }

func newRunCanceledError(err error) error {
	if err == nil {
		return nil
	}
	return runCanceledError{error: err}
}
