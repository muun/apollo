package presentation

import (
	"context"
	"time"

	"github.com/muun/libwallet/platform/preconditions"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/action"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

type BackgroundActions struct {
	registerPeriodic action.RegisterPeriodicAction
	deregister       action.DeregisterAction
	runDue           action.RunDueAction
	reset            action.ResetAction
}

func NewBackgroundActions(
	registerPeriodic action.RegisterPeriodicAction,
	deregister action.DeregisterAction,
	runDue action.RunDueAction,
	reset action.ResetAction,
) BackgroundActions {
	return BackgroundActions{
		registerPeriodic: registerPeriodic,
		deregister:       deregister,
		runDue:           runDue,
		reset:            reset,
	}
}

func (a *BackgroundActions) RegisterPeriodic(
	ctx context.Context,
	fn ba_model.Function,
	period time.Duration,
	cfg ...ba_model.RegisterConfig,
) error {
	var config ba_model.RegisterConfig
	switch len(cfg) {
	case 0:
		config = ba_model.RegisterConfig{}
	case 1:
		config = cfg[0]
	default:
		preconditions.Failf("more than one (%v) configurations provided", len(cfg))
	}

	return a.registerPeriodic.Run(ctx, fn, period, config)
}

func (a *BackgroundActions) Deregister(ctx context.Context, fn ba_model.Function) error {
	return a.deregister.Run(ctx, fn)
}

func (a *BackgroundActions) RunDue(
	ctx context.Context,
	executionCtx ba_model.ExecutionContext,
) error {
	return a.runDue.Run(ctx, executionCtx)
}

func (a *BackgroundActions) Reset(ctx context.Context) error {
	return a.reset.Run(ctx)
}
