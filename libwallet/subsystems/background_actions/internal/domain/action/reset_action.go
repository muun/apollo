package action

import (
	"context"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data/repository"
)

// ResetAction interrupts the run in progress and cancels every pending wake.
type ResetAction interface {
	Run(ctx context.Context) error
}

type resetAction struct {
	locker                 *memlock.NamedLocker
	runCanceler            *data.RunCanceler
	registrationRepository repository.RegistrationRepository
	syncWakes              SyncWakesAction
}

func NewResetAction(
	locker *memlock.NamedLocker,
	runCanceler *data.RunCanceler,
	registrationRepository repository.RegistrationRepository,
	syncWakes SyncWakesAction,
) ResetAction {
	return &resetAction{
		locker:                 locker,
		runCanceler:            runCanceler,
		registrationRepository: registrationRepository,
		syncWakes:              syncWakes,
	}
}

func (a *resetAction) Run(ctx context.Context) error {
	lock, err := a.locker.AcquireLock(ctx, wakeLock)
	defer lock.Release()
	if err != nil {
		return err
	}

	a.runCanceler.CancelRun(errors.Errorf("Background Actions subsystem was reset"))

	if err = a.registrationRepository.Wipe(ctx); err != nil {
		return err
	}

	return a.syncWakes.RunUnderLock(ctx, nil)
}
