package action

import (
	"context"
	"math"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/platform/observability/logging"
	"github.com/muun/libwallet/platform/observability/tracing"
	"github.com/muun/libwallet/platform/preconditions"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data/repository"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/model"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

// reserve is the minimum amount of execution time left needed to keep executing functions.
const reserve = time.Second

// RunDueAction tries every registered function that fits before ctx's deadline, most overdue
// first, then asks for the next wake. An error means the run did not ask for it, and the
// platform should call again.
type RunDueAction interface {
	Run(ctx context.Context, executionCtx ba_model.ExecutionContext) error
}

type runDueAction struct {
	platform      app_provided_data.Platform
	session       app_provided_data.SessionProvider
	locker        *memlock.NamedLocker
	runCanceler   *data.RunCanceler
	registrations repository.RegistrationRepository
	syncWakes     SyncWakesAction
	reset         ResetAction

	handlers            map[ba_model.Function]ba_model.Handler
	staticRegistrations []ba_model.StaticRegistration
}

func NewRunDueAction(
	platform app_provided_data.Platform,
	session app_provided_data.SessionProvider,
	locker *memlock.NamedLocker,
	runCanceler *data.RunCanceler,
	registrations repository.RegistrationRepository,
	syncWakes SyncWakesAction,
	reset ResetAction,
	handlers map[ba_model.Function]ba_model.Handler,
	staticRegistrations []ba_model.StaticRegistration,
) RunDueAction {
	action := &runDueAction{
		platform:            platform,
		session:             session,
		locker:              locker,
		runCanceler:         runCanceler,
		registrations:       registrations,
		syncWakes:           syncWakes,
		reset:               reset,
		handlers:            handlers,
		staticRegistrations: staticRegistrations,
	}

	action.validateStaticRegistrations()

	return action
}

func (a *runDueAction) Run(ctx context.Context, executionCtx ba_model.ExecutionContext) error {
	tracing.AddTag(ctx, "execution_context", executionCtx)

	a.validateExecutionCtx(executionCtx)

	lock, err := a.locker.AcquireLock(ctx, runLock)
	defer lock.Release()
	if err != nil {
		return err
	}

	loggedIn, err := a.session.IsLoggedIn()
	if err != nil {
		return errors.Errorf("is logged in: %w", err)
	}

	if !loggedIn {
		if err := a.reset.Run(ctx); err != nil {
			// We swallow any reset errors.
			tracing.AddTag(ctx, "run_reset_failed", true)
			logging.Error(ctx, "Failed to reset the background actions subsystem",
				"error", err,
			)
		}

		return nil
	}

	ctx = a.runCanceler.SetNewRun(ctx)

	for _, staticRegistration := range a.staticRegistrations {
		if err := a.storeStaticRegistration(ctx, staticRegistration); err != nil {
			// The run goes on with the registrations already stored.
			tracing.AddTag(ctx, "static_registration_failed", staticRegistration.Function)
			logging.Error(ctx,
				"Failed to register a static function of the background actions subsystem",
				"function", staticRegistration.Function,
				"error", err,
			)
		}
	}

	err = a.runDueBackgroundActions(ctx, executionCtx)
	if err != nil {
		// We don't consider a cancel an error.
		cancelCause := a.runCanceler.CancelCause(context.Cause(ctx))
		if cancelCause != nil {
			logging.Info(ctx, "Run was canceled", "cause", cancelCause)
			tracing.AddTag(ctx, "run_cancel_cause", cancelCause.Error())
			return nil
		}
	}

	return err
}

func (a *runDueAction) validateStaticRegistrations() {
	seen := map[ba_model.Function]bool{}

	for _, staticRegistration := range a.staticRegistrations {
		fn := staticRegistration.Function

		if _, functionExists := a.handlers[fn]; !functionExists {
			preconditions.Failf("unknown static function %q", fn)
		}

		if seen[fn] {
			preconditions.Failf("static function %q registered twice", fn)
		}

		// Validate that it's a valid registration
		model.NewRegistration(fn, staticRegistration.Period, staticRegistration.Config, time.Now())

		seen[fn] = true
	}
}

func (a *runDueAction) validateExecutionCtx(executionCtx ba_model.ExecutionContext) {
	switch executionCtx {
	case ba_model.AndroidBackground, ba_model.AndroidForeground:
		preconditions.CheckStatef(a.platform.Android(), "Android wake %v on iOS", executionCtx)

	case ba_model.IOSBackgroundRefresh,
		ba_model.IOSBackgroundProcessing,
		ba_model.IOSForeground:
		preconditions.CheckStatef(a.platform.IOS(), "iOS wake %v on Android", executionCtx)

	default:
		preconditions.MissingCase(executionCtx)
	}
}

// storeStaticRegistration makes sure the static registration is stored.
func (a *runDueAction) storeStaticRegistration(
	ctx context.Context,
	staticRegistration ba_model.StaticRegistration,
) error {
	registration := model.NewRegistration(
		staticRegistration.Function,
		staticRegistration.Period,
		staticRegistration.Config,
		time.Now(),
	)

	stored, err := a.registrations.Get(ctx, registration.Function)
	if err != nil {
		return err
	}

	if stored != nil && stored.HasSameConfiguration(registration) {
		return nil
	}

	return a.registrations.Save(ctx, registration)
}

func (a *runDueAction) runDueBackgroundActions(
	ctx context.Context,
	executionCtx ba_model.ExecutionContext,
) error {
	if executionCtx == ba_model.IOSForeground {
		// In IOS, when on foreground, we need to pre-emptively arm the wakes
		// in case we get interrupted in the middle. The platform doesn't do it for us.
		if err := a.runSyncWakes(ctx, executionCtx); err != nil {
			tracing.AddTag(ctx, "run_foreground_wake_sync_failed", true)
			logging.Error(ctx, "Failed to sync the background actions subsystem background wakes",
				"error", err,
			)
		}
	}

	registrations, err := a.registrations.List(ctx)
	if err != nil {
		return err
	}

	for _, registration := range registrations {
		if err := ctx.Err(); err != nil {
			return errors.Errorf("run interrupted: %w", err)
		}

		functionName := string(registration.Function)
		err := tracing.WithSpan(ctx, functionName, func(ctx context.Context) error {
			return a.processRegistration(ctx, registration)
		})

		if err != nil {
			return errors.Errorf("%s: %w", functionName, err)
		}
	}

	return a.runSyncWakes(ctx, executionCtx)
}

// processRegistration runs the function of registration and reschedules.
// It returns an error only when the run must stop.
func (a *runDueAction) processRegistration(
	ctx context.Context,
	registration *model.Registration,
) error {
	fn := registration.Function

	tracing.Current(ctx).
		AddTag("due_at", registration.DueAt).
		AddTag("min_run_time", registration.MinRunTime).
		AddTag("timeout", registration.Timeout).
		AddTag("attempts", registration.Attempts)

	budget := time.Duration(math.MaxInt64)
	if deadline, ok := ctx.Deadline(); ok {
		budget = time.Until(deadline) - reserve
		tracing.AddTag(ctx, "budget", budget)
	}

	if registration.MinRunTime > budget {
		tracing.AddTag(ctx, "result", "skipped_by_min_runtime")
		return nil
	}

	registration.Claim(time.Now())
	claimed, err := a.registrations.UpdateIfSameGeneration(ctx, registration)
	if err != nil || !claimed {
		return err
	}

	timeout := min(registration.Timeout, budget)
	handlerCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	start := time.Now()
	timedOut, panicked, handlerErr := a.executeHandler(handlerCtx, a.handlers[fn])
	tracing.AddTag(ctx, "handler_duration", time.Since(start))

	if ctx.Err() != nil {
		tracing.AddTag(ctx, "result", "interrupted")
		return errors.Errorf("run interrupted: %w", ctx.Err())
	}

	now := time.Now()
	var result string
	switch {
	case timedOut && timeout < registration.Timeout && registration.Attempts == 0:
		result = "timeout_retry"
		registration.Retry(now)

	case timedOut:
		result = "timeout_reschedule"
		registration.Reschedule(now)

	case handlerErr != nil:
		result = "error"
		if panicked {
			result = "panicked"
		}
		tracing.RecordErrorInline(ctx, handlerErr)
		registration.Reschedule(now)

	default:
		result = "success"
		registration.Reschedule(now)
	}

	tracing.AddTag(ctx, "result", result)

	updated, err := a.registrations.UpdateIfSameGeneration(ctx, registration)
	if err != nil {
		return err
	}

	tracing.AddTag(ctx, "auto_rescheduled", updated)

	return nil
}

// executeHandler executes the handler of a registered function, and returns whether it timed
// out, whether it panicked and its error. A handler that ignores its ctx is abandoned at its deadline.
func (a *runDueAction) executeHandler(
	ctx context.Context,
	handler ba_model.Handler,
) (bool, bool, error) {
	type result struct {
		err      error
		panicked bool
	}

	resultChan := make(chan result, 1)

	go func() {
		defer func() {
			if recovered := recover(); recovered != nil {
				var err error
				if panicErr, ok := recovered.(error); ok {
					err = errors.Errorf("handler panicked: %w", panicErr)
				} else {
					err = errors.Errorf("handler panicked: %v", recovered)
				}

				resultChan <- result{err, true}
			}
		}()

		resultChan <- result{handler(ctx), false}
	}()

	var timedOut, panicked bool
	var err error
	select {
	case res := <-resultChan:
		timedOut = res.err != nil && (errors.Is(res.err, context.DeadlineExceeded) ||
			errors.Is(ctx.Err(), context.DeadlineExceeded))
		panicked = res.panicked
		err = res.err

	case <-ctx.Done():
		timedOut = errors.Is(ctx.Err(), context.DeadlineExceeded)
	}

	return timedOut, panicked, err
}

func (a *runDueAction) runSyncWakes(
	ctx context.Context,
	executionCtx ba_model.ExecutionContext,
) error {
	lock, err := a.locker.AcquireLock(ctx, wakeLock)
	defer lock.Release()
	if err != nil {
		return err
	}

	return a.syncWakes.RunUnderLock(ctx, &executionCtx)
}
