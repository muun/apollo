package action

import (
	"context"
	"slices"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/observability/logging"
	"github.com/muun/libwallet/platform/observability/tracing"
	"github.com/muun/libwallet/platform/preconditions"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data/repository"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/model"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

const (
	// androidMinDelayFloor is the minimum cooldown delay asked after a wake.
	androidMinDelayFloor = 90 * time.Minute

	// iosRefreshMinDelay is the cooldown delay asked after a refreshing wake.
	iosRefreshMinDelay = 15 * time.Minute

	// iosProcessingMinDelay is the cooldown delay asked after a processing wake.
	iosProcessingMinDelay = 60 * time.Minute
)

type wakeAction int

const (
	noopWakeAction wakeAction = iota
	cancelWakeAction
	scheduleWakeAction
	scheduleWakeNowAction
)

// SyncWakesAction syncs the registrations stored in the Background Actions subsystem with the
// background wakes asked to the OS.
// Requires holding the `backgroundActionsWake` lock.
type SyncWakesAction interface {
	RunUnderLock(ctx context.Context, executionCtx *ba_model.ExecutionContext) error
}

type syncWakesAction struct {
	platform      app_provided_data.Platform
	android       app_provided_data.AndroidBackgroundActionsBridge
	ios           app_provided_data.IOSBackgroundActionsBridge
	registrations repository.RegistrationRepository
}

func NewSyncWakesAction(
	platform app_provided_data.Platform,
	android app_provided_data.AndroidBackgroundActionsBridge,
	ios app_provided_data.IOSBackgroundActionsBridge,
	registrations repository.RegistrationRepository,
) SyncWakesAction {
	return &syncWakesAction{
		platform:      platform,
		android:       android,
		ios:           ios,
		registrations: registrations,
	}
}

func (a *syncWakesAction) RunUnderLock(
	ctx context.Context,
	executionCtx *ba_model.ExecutionContext,
) error {
	registrations, err := a.registrations.List(ctx)
	if err != nil {
		return err
	}

	// A missing bridge means the app does not ask for background wakes.
	switch a.platform {
	case app_provided_data.Android:
		if a.android == nil {
			return nil
		}
		return a.syncWakeAndroid(executionCtx, registrations)

	case app_provided_data.IOS:
		if a.ios == nil {
			return nil
		}
		return a.syncWakeIOS(ctx, executionCtx, registrations)

	default:
		preconditions.MissingCase(a.platform)
		return nil
	}
}

func (a *syncWakesAction) syncWakeAndroid(
	executionCtx *ba_model.ExecutionContext,
	registrations []*model.Registration,
) error {
	var minDelayFloor time.Duration

	switch a.androidWakeAction(executionCtx, registrations) {
	case noopWakeAction:
		return nil
	case cancelWakeAction:
		if err := a.android.CancelWake(); err != nil {
			return errors.Errorf("Android cancel wake: %w", err)
		}
		return nil
	case scheduleWakeAction:
		minDelayFloor = androidMinDelayFloor
	case scheduleWakeNowAction:
		minDelayFloor = 0
	}

	now := time.Now()

	delay := registrations[0].WakeDelay(now)
	for _, registration := range registrations[1:] {
		delay = min(delay, registration.WakeDelay(now))
	}

	minDelay := max(minDelayFloor, delay)

	if err := a.android.ScheduleWake(minDelay.Milliseconds()); err != nil {
		return errors.Errorf("Android schedule wake: %w", err)
	}

	return nil
}

func (a *syncWakesAction) androidWakeAction(
	executionCtx *ba_model.ExecutionContext,
	registrations []*model.Registration,
) wakeAction {
	if len(registrations) == 0 {
		return cancelWakeAction
	}

	if executionCtx == nil {
		return scheduleWakeNowAction
	}

	switch *executionCtx {
	case ba_model.AndroidBackground, ba_model.AndroidForeground:
		return scheduleWakeAction
	default:
		preconditions.Failf("invalid execution context %v for Android", *executionCtx)
		return noopWakeAction // unreachable
	}
}

func (a *syncWakesAction) syncWakeIOS(
	ctx context.Context,
	executionCtx *ba_model.ExecutionContext,
	registrations []*model.Registration,
) error {
	var errRefresh, errProcessing error

	switch a.iosRefreshWakeAction(executionCtx, registrations) {
	case noopWakeAction:
	case cancelWakeAction:
		errRefresh = a.ios.CancelRefreshWake()
	case scheduleWakeNowAction:
		errRefresh = a.ios.ScheduleRefreshWake(0)
	case scheduleWakeAction:
		errRefresh = a.ios.ScheduleRefreshWake(iosRefreshMinDelay.Milliseconds())
	}

	errRefresh = a.ignoreBGTaskSchedulerUnavailable(ctx, "refresh", errRefresh)

	switch a.iosProcessingWakeAction(executionCtx, registrations) {
	case noopWakeAction:
	case cancelWakeAction:
		errProcessing = a.ios.CancelProcessingWake()
	case scheduleWakeNowAction:
		errProcessing = a.ios.ScheduleProcessingWake(0)
	case scheduleWakeAction:
		errProcessing = a.ios.ScheduleProcessingWake(iosProcessingMinDelay.Milliseconds())
	}

	errProcessing = a.ignoreBGTaskSchedulerUnavailable(ctx, "processing", errProcessing)

	if err := errors.Join(errRefresh, errProcessing); err != nil {
		return errors.Errorf("IOS sync wake: %w", err)
	}

	return nil
}

func (a *syncWakesAction) iosRefreshWakeAction(
	executionCtx *ba_model.ExecutionContext,
	registrations []*model.Registration,
) wakeAction {
	if !slices.ContainsFunc(registrations, (*model.Registration).FitsRefresh) {
		return cancelWakeAction
	}

	if executionCtx == nil {
		return scheduleWakeNowAction
	}

	switch *executionCtx {
	case ba_model.IOSBackgroundRefresh:
		return scheduleWakeAction
	case ba_model.IOSBackgroundProcessing:
		return noopWakeAction
	case ba_model.IOSForeground:
		return scheduleWakeNowAction
	default:
		preconditions.Failf("invalid execution context %v for IOS", *executionCtx)
		return noopWakeAction // unreachable
	}
}

func (a *syncWakesAction) iosProcessingWakeAction(
	executionCtx *ba_model.ExecutionContext,
	registrations []*model.Registration,
) wakeAction {
	if len(registrations) == 0 {
		return cancelWakeAction
	}

	if executionCtx == nil {
		return scheduleWakeNowAction
	}

	switch *executionCtx {
	case ba_model.IOSBackgroundRefresh:
		return noopWakeAction
	case ba_model.IOSBackgroundProcessing:
		return scheduleWakeAction
	case ba_model.IOSForeground:
		return scheduleWakeNowAction
	default:
		preconditions.Failf("invalid execution context %v for IOS", *executionCtx)
		return noopWakeAction // unreachable
	}
}

func (a *syncWakesAction) ignoreBGTaskSchedulerUnavailable(
	ctx context.Context,
	wake string,
	err error,
) error {
	if errors.Is(err, app_provided_data.ErrBGTaskSchedulerUnavailable) {
		tracing.AddTag(ctx, "background_actions:bg_task_scheduler_unavailable", wake)
		logging.Info(ctx, "BGTaskScheduler is unavailable",
			"wake", wake,
		)
		return nil
	}
	return err
}
