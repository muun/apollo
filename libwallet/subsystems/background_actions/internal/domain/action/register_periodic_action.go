package action

import (
	"context"
	"slices"
	"time"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/platform/observability/logging"
	"github.com/muun/libwallet/platform/observability/tracing"
	"github.com/muun/libwallet/platform/preconditions"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data/repository"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/model"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

// RegisterPeriodicAction registers fn to run every period, and asks for the wake it needs.
type RegisterPeriodicAction interface {
	Run(
		ctx context.Context,
		fn ba_model.Function,
		period time.Duration,
		cfg ba_model.RegisterConfig,
	) error
}

type registerPeriodicAction struct {
	platform               app_provided_data.Platform
	locker                 *memlock.NamedLocker
	registrationRepository repository.RegistrationRepository
	syncWakes              SyncWakesAction
	handlers               map[ba_model.Function]ba_model.Handler
}

func NewRegisterPeriodicAction(
	platform app_provided_data.Platform,
	locker *memlock.NamedLocker,
	registrationRepository repository.RegistrationRepository,
	syncWakes SyncWakesAction,
	handlers map[ba_model.Function]ba_model.Handler,
) RegisterPeriodicAction {
	return &registerPeriodicAction{
		platform:               platform,
		locker:                 locker,
		registrationRepository: registrationRepository,
		syncWakes:              syncWakes,
		handlers:               handlers,
	}
}

func (a *registerPeriodicAction) Run(
	ctx context.Context,
	fn ba_model.Function,
	period time.Duration,
	cfg ba_model.RegisterConfig,
) error {
	if _, functionExists := a.handlers[fn]; !functionExists {
		preconditions.Failf("unknown function %q", fn)
	}

	registration := model.NewRegistration(fn, period, cfg, time.Now())

	lock, err := a.locker.AcquireLock(ctx, wakeLock)
	defer lock.Release()
	if err != nil {
		return err
	}

	registrations, err := a.registrationRepository.List(ctx)
	if err != nil {
		return err
	}

	index := slices.IndexFunc(registrations, func(registration *model.Registration) bool {
		return registration.Function == fn
	})
	if index >= 0 && registration.HasSameConfiguration(registrations[index]) {
		return nil
	}

	if err := a.registrationRepository.Save(ctx, registration); err != nil {
		return err
	}

	if a.shouldResyncWake(registration, registrations) {
		if err := a.syncWakes.RunUnderLock(ctx, nil); err != nil {
			// We don't want to fail a registration due to an OS failure.
			// We log the error and swallow it.
			tracing.AddTag(ctx, "background_actions:register_wake_sync_failed", true)
			logging.Error(ctx, "Failed to sync the background actions subsystem background wakes",
				"function", fn,
				"error", err,
			)
		}
	}

	return nil
}

func (a *registerPeriodicAction) shouldResyncWake(
	newRegistration *model.Registration,
	oldRegistrations []*model.Registration,
) bool {
	if len(oldRegistrations) == 0 {
		// No previous registration.
		return true
	}

	if newRegistration.DueAt.Before(oldRegistrations[0].DueAt) {
		// New registration is the earliest due at.
		return true
	}

	if a.platform.IOS() && newRegistration.FitsRefresh() {
		noneFitRefresh := !slices.ContainsFunc(oldRegistrations, (*model.Registration).FitsRefresh)
		if noneFitRefresh {
			// New registration is the first to fit the refresh.
			return true
		}
	}

	return false
}
