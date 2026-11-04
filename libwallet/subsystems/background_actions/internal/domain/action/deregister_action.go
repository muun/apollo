package action

import (
	"context"
	"slices"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/platform/observability/logging"
	"github.com/muun/libwallet/platform/observability/tracing"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data/repository"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/model"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

// DeregisterAction removes the registration of fn, if any, and withdraws the wakes it needed.
type DeregisterAction interface {
	Run(ctx context.Context, fn ba_model.Function) error
}

type deregisterAction struct {
	platform      app_provided_data.Platform
	locker        *memlock.NamedLocker
	registrations repository.RegistrationRepository
	syncWakes     SyncWakesAction
	handlers      map[ba_model.Function]ba_model.Handler
}

func NewDeregisterAction(
	platform app_provided_data.Platform,
	locker *memlock.NamedLocker,
	registrations repository.RegistrationRepository,
	syncWakes SyncWakesAction,
	handlers map[ba_model.Function]ba_model.Handler,
) DeregisterAction {
	return &deregisterAction{
		platform:      platform,
		locker:        locker,
		registrations: registrations,
		syncWakes:     syncWakes,
		handlers:      handlers,
	}
}

func (a *deregisterAction) Run(ctx context.Context, fn ba_model.Function) error {
	if _, functionExists := a.handlers[fn]; !functionExists {
		return nil
	}

	lock, err := a.locker.AcquireLock(ctx, wakeLock)
	defer lock.Release()
	if err != nil {
		return err
	}

	registration, err := a.registrations.Get(ctx, fn)
	if registration == nil || err != nil {
		return err
	}

	if err := a.registrations.Delete(ctx, fn); err != nil {
		return err
	}

	registrations, err := a.registrations.List(ctx)
	if err != nil {
		return err
	}

	if a.shouldResyncWake(registration, registrations) {
		if err := a.syncWakes.RunUnderLock(ctx, nil); err != nil {
			// We don't want to fail a deregistration due to an OS failure.
			// We log the error and swallow it.
			tracing.AddTag(ctx, "background_actions:deregister_wake_sync_failed", true)
			logging.Error(ctx, "Failed to sync the background actions subsystem background wakes",
				"function", fn,
				"error", err,
			)
		}
	}

	return nil
}

func (a *deregisterAction) shouldResyncWake(
	oldRegistration *model.Registration,
	registrations []*model.Registration,
) bool {
	if len(registrations) == 0 {
		// No registrations left.
		return true
	}

	if oldRegistration.DueAt.Before(registrations[0].DueAt) {
		// The registration deleted was the earliest due at.
		return true
	}

	if a.platform.IOS() && oldRegistration.FitsRefresh() {
		noneFitRefresh := !slices.ContainsFunc(registrations, (*model.Registration).FitsRefresh)
		if noneFitRefresh {
			// The registration deleted was the only one that fitted the refresh.
			return true
		}
	}

	return false
}
