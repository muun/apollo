package presentation

import (
	"context"
	"time"

	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

// BackgroundActions is the component modules call to schedule functions.
type BackgroundActions interface {
	// RegisterPeriodic asks to run fn every period, starting now + period.
	// Registering fn again with the same values changes nothing.
	// New values replace the registration and restart the period.
	// A run in progress is not stopped.
	RegisterPeriodic(
		ctx context.Context,
		fn ba_model.Function,
		period time.Duration,
		cfg ...ba_model.RegisterConfig,
	) error

	// Deregister removes the registration of fn, if any.
	// A run in progress is not stopped.
	Deregister(ctx context.Context, fn ba_model.Function) error
}

// BackgroundActionsRunner is the component the RunDueBackgroundActions gRPC calls to run functions.
type BackgroundActionsRunner interface {
	// RunDue tries every registered function that fits before ctx's deadline.
	RunDue(ctx context.Context, executionCtx ba_model.ExecutionContext) error

	// Reset interrupts the run in progress and cancels every pending wake.
	// ResetData calls it on logout, before wiping the database.
	Reset(ctx context.Context) error
}
