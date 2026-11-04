package tests

import (
	"context"
	"slices"
	"testing"
	"time"

	"github.com/go-errors/errors"
	"google.golang.org/grpc/codes"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/platform/test/testsuite"
	"github.com/muun/libwallet/server/api"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

func TestBackgroundActions_Integration(t *testing.T) {
	t.Run("Android", func(t *testing.T) {
		testsuite.Run(t, newAgnosticSuite(app_provided_data.Android))
	})
	t.Run("IOS", func(t *testing.T) {
		testsuite.Run(t, newAgnosticSuite(app_provided_data.IOS))
	})
}

// AgnosticSuite runs tests that are independent of the platform.
type AgnosticSuite struct {
	baseBackgroundActionsSuite
}

func newAgnosticSuite(
	platform app_provided_data.Platform,
) *AgnosticSuite {
	return &AgnosticSuite{
		newBaseBackgroundActionsSuite(platform),
	}
}

// staticRegistrations are the static registrations of fnA with the default config, and fnB with its own.
var staticRegistrations = []ba_model.StaticRegistration{
	ba_model.NewStaticRegistration(fnA, 3*time.Hour),
	ba_model.NewStaticRegistration(fnB, 5*time.Hour, ba_model.RegisterConfig{
		Timeout:    10 * time.Second,
		MinRunTime: time.Second,
	}),
}

func (s *AgnosticSuite) TestRegisterSavesTheRegistration() {
	registeredAt := time.Now()
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))

	registration := s.getRegistration(fnA)
	s.Equal(3*time.Hour, registration.Period)
	s.Equal(20*time.Second, registration.Timeout)
	s.Zero(registration.MinRunTime)
	s.WithinRange(registration.DueAt, registeredAt.Add(3*time.Hour), time.Now().Add(3*time.Hour))
}

func (s *AgnosticSuite) TestRegisterIsIdempotent() {
	s.NoError(s.backgroundActions.RegisterPeriodic(
		s.Ctx, fnA, 3*time.Hour, ba_model.RegisterConfig{Timeout: 20 * time.Second},
	))
	before := s.getRegistration(fnA)
	s.bridge().takeCalls()

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))

	s.bridge().requireNoCalls()
	s.Equal(before, s.getRegistration(fnA))
}

func (s *AgnosticSuite) TestRegisterWithNewValuesRestartsThePeriod() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))
	before := s.getRegistration(fnA)

	registeredAt := time.Now()
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 5*time.Hour))

	after := s.getRegistration(fnA)
	s.Equal(5*time.Hour, after.Period)
	s.WithinRange(after.DueAt, registeredAt.Add(5*time.Hour), time.Now().Add(5*time.Hour))
	s.NotEqual(before.Generation, after.Generation)
}

func (s *AgnosticSuite) TestRegisterPanicsOnInvalidValues() {
	cases := []struct {
		name   string
		fn     ba_model.Function
		period time.Duration
		cfg    []ba_model.RegisterConfig
	}{
		{name: "unknown function", fn: "test.unknown", period: time.Hour},
		{name: "zero period", fn: fnA, period: 0},
		{
			name:   "negative timeout",
			fn:     fnA,
			period: time.Hour,
			cfg:    []ba_model.RegisterConfig{{Timeout: -time.Second}},
		},
		{
			name:   "negative min run time",
			fn:     fnA,
			period: time.Hour,
			cfg:    []ba_model.RegisterConfig{{MinRunTime: -time.Second}},
		},
		{
			name:   "min run time above the timeout",
			fn:     fnA,
			period: time.Hour,
			cfg: []ba_model.RegisterConfig{
				{Timeout: time.Second, MinRunTime: 2 * time.Second},
			},
		},
		{
			name:   "two configs",
			fn:     fnA,
			period: time.Hour,
			cfg:    []ba_model.RegisterConfig{{}, {}},
		},
	}

	for _, c := range cases {
		s.Panics(func() {
			_ = s.backgroundActions.RegisterPeriodic(s.Ctx, c.fn, c.period, c.cfg...)
		}, c.name)
	}

	s.bridge().requireNoCalls()
	s.Empty(s.registry())
}

func (s *AgnosticSuite) TestRegisterIsSavedWhenThePlatformFails() {
	s.bridge().failWith(errors.New("background work is restricted"))

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))
	s.NotNil(s.getRegistration(fnA))
}

func (s *AgnosticSuite) TestDeregisterRemovesTheRegistration() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnA))

	s.Nil(s.getRegistration(fnA))
	s.NotNil(s.getRegistration(fnB))
}

func (s *AgnosticSuite) TestDeregisterWithoutARegistrationChangesNothing() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	before := s.registry()
	s.bridge().takeCalls()

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnB))

	s.bridge().requireNoCalls()
	s.Equal(before, s.registry())
}

func (s *AgnosticSuite) TestDeregisterIgnoresAnUnknownFunction() {
	s.NoError(s.backgroundActions.Deregister(s.Ctx, "test.unknown"))
	s.bridge().requireNoCalls()
}

func (s *AgnosticSuite) TestDeregisterSucceedsWhenThePlatformFails() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.bridge().failWith(errors.New("background work is restricted"))

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnA))

	s.Empty(s.registry())
}

func (s *AgnosticSuite) TestRunRunsEveryFunctionByDueTime() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 5*time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))

	start := time.Now()
	s.NoError(s.runDue(s.backgroundWake(), wakeDeadline))
	end := time.Now()

	s.handlers.requireRan(fnB, fnA)
	s.requireRescheduled(fnA, start, end)
	s.requireRescheduled(fnB, start, end)
}

func (s *AgnosticSuite) TestRunMovesEveryOutcomeToTheNextPeriod() {
	s.handlers.set(fnA, func(context.Context) error {
		return errors.New("houston is down")
	})
	s.handlers.set(fnB, func(context.Context) error {
		panic("unexpected state")
	})
	s.blockUntilDone(fnC)

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(
		s.Ctx, fnC, 3*time.Hour, ba_model.RegisterConfig{Timeout: 100 * time.Millisecond},
	))

	start := time.Now()
	s.NoError(s.runDue(s.backgroundWake(), wakeDeadline))
	end := time.Now()

	s.handlers.requireRan(fnA, fnB, fnC)
	s.requireRescheduled(fnA, start, end)
	s.requireRescheduled(fnB, start, end)
	s.requireRescheduled(fnC, start, end)
}

func (s *AgnosticSuite) TestRunRetriesOnceAFunctionTheWakeCutShort() {
	s.blockUntilDone(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(
		s.Ctx, fnA, 3*time.Hour, ba_model.RegisterConfig{Timeout: 5 * time.Second},
	))

	s.NoError(s.runDue(s.backgroundWake(), shortWakeDeadline))

	registration := s.getRegistration(fnA)
	s.Equal(1, registration.Attempts)
	s.False(registration.DueAt.After(time.Now()))

	start := time.Now()
	s.NoError(s.runDue(s.backgroundWake(), shortWakeDeadline))
	end := time.Now()

	s.handlers.requireRan(fnA, fnA)
	s.requireRescheduled(fnA, start, end)
}

func (s *AgnosticSuite) TestRunRetriesOnceAFunctionThatFailsWithADeadlineError() {
	// The handler returns before its ctx is done, so only its error tells it timed out.
	s.handlers.set(fnA, func(context.Context) error {
		return errors.Errorf("%w: native timeout", context.DeadlineExceeded)
	})
	s.NoError(s.backgroundActions.RegisterPeriodic(
		s.Ctx, fnA, 3*time.Hour, ba_model.RegisterConfig{Timeout: 5 * time.Second},
	))

	s.NoError(s.runDue(s.backgroundWake(), shortWakeDeadline))

	registration := s.getRegistration(fnA)
	s.Equal(1, registration.Attempts)
	s.False(registration.DueAt.After(time.Now()))
}

func (s *AgnosticSuite) TestRunRetriesOnceANativeActionTheWakeCutShort() {
	native := newMockPeriodicActionsBridge(s.T)
	native.timeOut()
	bridge := adapted.NewPeriodicActionsBridge(native)
	s.handlers.set(fnA, func(ctx context.Context) error {
		// The bridge gets an earlier deadline, so the run's ctx is still live when the native
		// timeout ends, as it is when ctx's deadline timer runs late.
		ctx, cancel := context.WithTimeout(ctx, 100*time.Millisecond)
		defer cancel()

		return bridge.PullNotifications(ctx)
	})
	s.NoError(s.backgroundActions.RegisterPeriodic(
		s.Ctx, fnA, 3*time.Hour, ba_model.RegisterConfig{Timeout: 5 * time.Second},
	))

	s.NoError(s.runDue(s.backgroundWake(), shortWakeDeadline))

	registration := s.getRegistration(fnA)
	s.Equal(1, registration.Attempts)
	s.False(registration.DueAt.After(time.Now()))
}

func (s *AgnosticSuite) TestRunReschedulesANativeActionThatFailsBeforeItsDeadline() {
	native := newMockPeriodicActionsBridge(s.T)
	native.failWith(errors.New("houston is down"))
	bridge := adapted.NewPeriodicActionsBridge(native)
	s.handlers.set(fnA, bridge.PullNotifications)
	// The wake is shorter than the timeout, so a failure reported as a timeout would be retried.
	s.NoError(s.backgroundActions.RegisterPeriodic(
		s.Ctx, fnA, 3*time.Hour, ba_model.RegisterConfig{Timeout: 5 * time.Second},
	))

	start := time.Now()
	s.NoError(s.runDue(s.backgroundWake(), shortWakeDeadline))
	end := time.Now()

	native.requireCalls("PullNotifications()")
	s.requireRescheduled(fnA, start, end)
}

func (s *AgnosticSuite) TestRunDoesNotCallANativeActionWhoseCtxIsDone() {
	native := newMockPeriodicActionsBridge(s.T)
	bridge := adapted.NewPeriodicActionsBridge(native)
	var actionErr error
	s.handlers.set(fnA, func(ctx context.Context) error {
		ctx, cancel := context.WithCancel(ctx)
		cancel()

		actionErr = bridge.PullNotifications(ctx)
		return actionErr
	})
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))

	s.NoError(s.runDue(s.backgroundWake(), wakeDeadline))

	s.ErrorIs(actionErr, context.Canceled)
	native.requireNoCalls()
}

func (s *AgnosticSuite) TestRunSkipsAFunctionThatDoesNotFit() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour, ba_model.RegisterConfig{
		Timeout:    2 * time.Second,
		MinRunTime: time.Second,
	}))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))
	before := s.getRegistration(fnA)

	s.NoError(s.runDue(s.backgroundWake(), shortWakeDeadline))

	s.handlers.requireRan(fnB)
	s.Equal(before, s.getRegistration(fnA))
}

func (s *AgnosticSuite) TestRunSkipsAFunctionThatNoLongerFitsAfterTheOnesBefore() {
	s.handlers.set(fnA, func(context.Context) error {
		time.Sleep(500 * time.Millisecond)
		return nil
	})
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour, ba_model.RegisterConfig{
		Timeout:    time.Second,
		MinRunTime: 750 * time.Millisecond,
	}))
	before := s.getRegistration(fnB)

	s.NoError(s.runDue(s.backgroundWake(), 2*time.Second))

	s.handlers.requireRan(fnA)
	s.Equal(before, s.getRegistration(fnB))
}

func (s *AgnosticSuite) TestRunAbandonsAHandlerPastItsDeadline() {
	stuck := make(chan struct{})
	s.T().Cleanup(func() {
		close(stuck)
	})
	s.handlers.set(fnA, func(context.Context) error {
		<-stuck
		return nil
	})
	s.NoError(s.backgroundActions.RegisterPeriodic(
		s.Ctx, fnA, 3*time.Hour, ba_model.RegisterConfig{Timeout: 100 * time.Millisecond},
	))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))

	start := time.Now()
	s.NoError(s.runDue(s.backgroundWake(), wakeDeadline))
	end := time.Now()

	s.Less(end.Sub(start), time.Second)
	s.handlers.requireRan(fnA, fnB)
	s.requireRescheduled(fnA, start, end)
}

func (s *AgnosticSuite) TestInterruptedFunctionRunsAgainAtTheNextWake() {
	started := s.blockUntilDone(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))
	before := s.getRegistration(fnA)

	ctx, cancel := context.WithCancel(s.Ctx)
	result := s.runDueInBackground(ctx, s.backgroundWake())
	s.awaitStart(started)
	cancel()
	s.requireCode(<-result, codes.Canceled)

	after := s.getRegistration(fnA)
	s.Equal(before.DueAt, after.DueAt)
	s.Equal(before.Attempts, after.Attempts)
	s.Equal(before.Generation, after.Generation)

	s.handlers.set(fnA, nil)
	start := time.Now()
	s.NoError(s.runDue(s.backgroundWake(), wakeDeadline))
	end := time.Now()

	s.handlers.requireRan(fnA, fnA)
	s.requireRescheduled(fnA, start, end)
}

func (s *AgnosticSuite) TestRunEndReturnsThePlatformError() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.bridge().failWith(errors.New("background work is restricted"))

	err := s.runDue(s.backgroundWake(), wakeDeadline)

	s.requireCode(err, codes.Internal)
	s.handlers.requireRan(fnA)
}

func (s *AgnosticSuite) TestRunEndReturnsTheWakeLockTimeout() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))

	// The test holds the wake lock the run needs to ask for its next wake.
	lock, err := s.Locker.Acquire(s.Ctx, "backgroundActionsWake", time.Second)
	s.NoError(err)
	defer lock.Release()

	err = s.runDue(s.backgroundWake(), wakeDeadline)

	s.requireCode(err, codes.Internal)
	s.handlers.requireRan(fnA)
}

func (s *AgnosticSuite) TestRunsNeverOverlap() {
	started, release := s.blockUntilReleased(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))

	first := s.runDueInBackground(s.Ctx, s.backgroundWake())
	s.awaitStart(started)
	second := s.runDueInBackground(s.Ctx, s.foregroundWake())

	s.Never(func() bool {
		return len(started) > 0
	}, 200*time.Millisecond, 10*time.Millisecond)
	release()

	s.NoError(<-first)
	s.NoError(<-second)
	s.handlers.requireRan(fnA, fnA)
}

func (s *AgnosticSuite) TestWakeWaitingForARunEndsAtItsDeadline() {
	started := s.blockUntilDone(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))

	ctx, cancel := context.WithCancel(s.Ctx)
	first := s.runDueInBackground(ctx, s.backgroundWake())
	s.awaitStart(started)

	err := s.runDue(s.backgroundWake(), 200*time.Millisecond)
	s.requireCode(err, codes.DeadlineExceeded)
	s.handlers.requireRan(fnA)

	cancel()
	s.requireCode(<-first, codes.Canceled)
}

func (s *AgnosticSuite) TestRegisterDuringARunWins() {
	started, release := s.blockUntilReleased(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))

	result := s.runDueInBackground(s.Ctx, s.backgroundWake())
	s.awaitStart(started)

	registeredAt := time.Now()
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 5*time.Hour))
	release()
	s.NoError(<-result)

	registration := s.getRegistration(fnA)
	s.Equal(5*time.Hour, registration.Period)
	s.WithinRange(registration.DueAt, registeredAt.Add(5*time.Hour), time.Now().Add(5*time.Hour))
}

func (s *AgnosticSuite) TestDeregisterDuringARunWins() {
	started, release := s.blockUntilReleased(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))

	result := s.runDueInBackground(s.Ctx, s.backgroundWake())
	s.awaitStart(started)

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnA))
	release()
	s.NoError(<-result)

	s.Empty(s.registry())
}

func (s *AgnosticSuite) TestRunWithoutAWalletClearsTheRegistry() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.session.loggedIn.Store(false)

	s.NoError(s.runDue(s.backgroundWake(), wakeDeadline))

	s.handlers.requireNoRan()
	s.Empty(s.registry())
}

func (s *AgnosticSuite) TestRunWhenTheSessionFailsReturnsItsErrorAndChangesNothing() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	before := s.registry()
	s.bridge().takeCalls()
	s.session.failWith(errors.New("can't read the session"))

	err := s.runDue(s.backgroundWake(), wakeDeadline)

	s.requireCode(err, codes.Internal)
	s.handlers.requireNoRan()
	s.bridge().requireNoCalls()
	s.Equal(before, s.registry())
}

func (s *AgnosticSuite) TestResetDataInterruptsTheRunAndClearsTheRegistry() {
	started := s.blockUntilDone(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))

	result := s.runDueInBackground(s.Ctx, s.backgroundWake())
	s.awaitStart(started)

	s.resetDataOnLogout()

	s.NoError(<-result)
	s.handlers.requireRan(fnA)
	s.Empty(s.registry())

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))
	s.NoError(s.runDue(s.backgroundWake(), wakeDeadline))
	s.handlers.requireRan(fnB)
}

func (s *AgnosticSuite) TestRegistrationsOfUnknownFunctionsAreKept() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))

	withoutA := newMockHandlers(s.T, fnB, fnC)
	restarted := s.newBackgroundActions(withoutA)

	s.NoError(s.runDueOn(restarted, s.backgroundWake(), wakeDeadline))
	withoutA.requireRan(fnB)
	s.Panics(func() {
		_ = restarted.RegisterPeriodic(s.Ctx, fnA, time.Hour)
	})
	s.NoError(restarted.Deregister(s.Ctx, fnB))

	s.NoError(s.runDue(s.backgroundWake(), wakeDeadline))
	s.handlers.requireRan(fnA)
}

func (s *AgnosticSuite) TestRunRejectsAnExecutionContextOfNoWakeOrAnotherPlatform() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))

	err := s.runDue(api.ExecutionContext_EXECUTION_CONTEXT_UNSPECIFIED, wakeDeadline)
	s.requireCode(err, codes.Internal)

	err = s.runDue(s.otherPlatformWake(), wakeDeadline)
	s.requireCode(err, codes.Internal)

	s.handlers.requireNoRan()
}

func (s *AgnosticSuite) TestRunStoresAndRunsTheStaticRegistrations() {
	withStaticRegistrations := s.newBackgroundActions(s.handlers, staticRegistrations...)

	s.NoError(s.runDueOn(withStaticRegistrations, s.backgroundWake(), wakeDeadline))

	s.handlers.requireRan(fnA, fnB)

	registrationA := s.getRegistration(fnA)
	s.Equal(3*time.Hour, registrationA.Period)
	s.Equal(20*time.Second, registrationA.Timeout)
	s.Zero(registrationA.MinRunTime)

	registrationB := s.getRegistration(fnB)
	s.Equal(5*time.Hour, registrationB.Period)
	s.Equal(10*time.Second, registrationB.Timeout)
	s.Equal(time.Second, registrationB.MinRunTime)
}

func (s *AgnosticSuite) TestRunKeepsTheStaticRegistrationsAlreadyStored() {
	withStaticRegistrations := s.newBackgroundActions(s.handlers, staticRegistrations...)
	s.NoError(s.runDueOn(withStaticRegistrations, s.backgroundWake(), wakeDeadline))
	before := s.registry()

	s.NoError(s.runDueOn(withStaticRegistrations, s.backgroundWake(), wakeDeadline))

	after := s.registry()
	s.Len(after, len(before))
	for i := range before {
		s.Equal(before[i].Generation, after[i].Generation)
	}
}

func (s *AgnosticSuite) TestRunReplacesAStaticRegistrationWhoseConfigChanged() {
	withStaticRegistrations := s.newBackgroundActions(s.handlers, staticRegistrations...)
	s.NoError(s.runDueOn(withStaticRegistrations, s.backgroundWake(), wakeDeadline))
	beforeA := s.getRegistration(fnA)
	beforeB := s.getRegistration(fnB)

	changed := slices.Clone(staticRegistrations)
	changed[0] = ba_model.NewStaticRegistration(fnA, 4*time.Hour)
	upgraded := s.newBackgroundActions(s.handlers, changed...)

	s.NoError(s.runDueOn(upgraded, s.backgroundWake(), wakeDeadline))

	afterA := s.getRegistration(fnA)
	s.Equal(4*time.Hour, afterA.Period)
	s.NotEqual(beforeA.Generation, afterA.Generation)
	s.Equal(beforeB.Generation, s.getRegistration(fnB).Generation)
}

func (s *AgnosticSuite) TestRunAfterResetDataStoresTheStaticRegistrationsAgain() {
	withStaticRegistrations := s.newBackgroundActions(s.handlers, staticRegistrations...)
	s.NoError(s.runDueOn(withStaticRegistrations, s.backgroundWake(), wakeDeadline))
	s.handlers.requireRan(fnA, fnB)

	s.resetDataOnLogout()
	s.Empty(s.registry())

	s.NoError(s.runDueOn(withStaticRegistrations, s.backgroundWake(), wakeDeadline))
	s.handlers.requireRan(fnA, fnB)
	s.Len(s.registry(), 2)
}

func (s *AgnosticSuite) TestStaticRegistrationsPanicOnInvalidValues() {
	cases := []struct {
		name                string
		staticRegistrations []ba_model.StaticRegistration
	}{
		{
			name: "unknown function",
			staticRegistrations: []ba_model.StaticRegistration{
				ba_model.NewStaticRegistration("test.unknown", time.Hour),
			},
		},
		{
			name: "function twice",
			staticRegistrations: []ba_model.StaticRegistration{
				ba_model.NewStaticRegistration(fnA, time.Hour),
				ba_model.NewStaticRegistration(fnA, 2*time.Hour),
			},
		},
		{
			name: "zero period",
			staticRegistrations: []ba_model.StaticRegistration{
				ba_model.NewStaticRegistration(fnA, 0),
			},
		},
	}

	for _, c := range cases {
		s.Panics(func() {
			s.newBackgroundActions(s.handlers, c.staticRegistrations...)
		}, c.name)
	}
}

// backgroundWake is the platform's background wake with the most execution time.
func (s *AgnosticSuite) backgroundWake() api.ExecutionContext {
	if s.platform.IOS() {
		return api.ExecutionContext_IOS_BACKGROUND_PROCESSING
	}
	return api.ExecutionContext_ANDROID_BACKGROUND
}

func (s *AgnosticSuite) foregroundWake() api.ExecutionContext {
	if s.platform.IOS() {
		return api.ExecutionContext_IOS_FOREGROUND
	}
	return api.ExecutionContext_ANDROID_FOREGROUND
}

func (s *AgnosticSuite) otherPlatformWake() api.ExecutionContext {
	if s.platform.IOS() {
		return api.ExecutionContext_ANDROID_BACKGROUND
	}
	return api.ExecutionContext_IOS_FOREGROUND
}

// bridge returns the call recorder of the platform's bridge.
func (s *AgnosticSuite) bridge() *callRecorder {
	if s.platform.IOS() {
		return &s.ios.callRecorder
	}
	return &s.android.callRecorder
}
