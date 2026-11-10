package tests

import (
	"testing"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/test/testsuite"
	"github.com/muun/libwallet/server/api"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

func TestAndroidBackgroundActions_Integration(t *testing.T) {
	testsuite.Run(t, newAndroidSuite())
}

// AndroidSuite runs Android specific tests.
type AndroidSuite struct {
	baseBackgroundActionsSuite
}

func newAndroidSuite() *AndroidSuite {
	return &AndroidSuite{
		newBaseBackgroundActionsSuite(app_provided_data.Android),
	}
}

func (s *AndroidSuite) TestRegisterAsksForTheWakeOnlyWhenDueFirst() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))
	s.android.requireCalls("ScheduleWake(3h0m0s)")

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 5*time.Hour))
	s.android.requireNoCalls()

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnC, time.Hour))
	s.android.requireCalls("ScheduleWake(1h0m0s)")
}

func (s *AndroidSuite) TestReRegisterDueLaterLeavesTheWakeUntilARunEnds() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))
	s.android.takeCalls()

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 5*time.Hour))
	s.android.requireNoCalls()

	s.NoError(s.runDue(api.ExecutionContext_ANDROID_BACKGROUND, wakeDeadline))
	s.android.requireCalls("ScheduleWake(3h0m0s)")
}

func (s *AndroidSuite) TestRunAsksAgainForAWakeThePlatformFailed() {
	s.android.failWith(errors.New("background work is restricted"))

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 3*time.Hour))
	s.android.requireCalls("ScheduleWake(3h0m0s)")

	s.android.failWith(nil)
	s.NoError(s.runDue(api.ExecutionContext_ANDROID_FOREGROUND, wakeDeadline))
	s.android.requireCalls("ScheduleWake(3h0m0s)")
}

func (s *AndroidSuite) TestDeregisterMovesTheWakeOnlyWhenDueFirst() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnC, 5*time.Hour))
	s.android.takeCalls()

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnB))
	s.android.requireNoCalls()

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnA))
	s.android.requireCalls("ScheduleWake(5h0m0s)")

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnC))
	s.android.requireCalls("CancelWake()")
}

func (s *AndroidSuite) TestRunAsksForTheWakeOfTheFirstDueFunction() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 5*time.Hour))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, 3*time.Hour))
	s.android.takeCalls()

	s.NoError(s.runDue(api.ExecutionContext_ANDROID_BACKGROUND, wakeDeadline))

	s.android.requireCalls("ScheduleWake(3h0m0s)")
}

func (s *AndroidSuite) TestRunAsksForTheNextWakeAtLeastTheFloorOut() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.android.takeCalls()

	s.NoError(s.runDue(api.ExecutionContext_ANDROID_BACKGROUND, wakeDeadline))
	s.android.requireCalls("ScheduleWake(1h30m0s)")

	s.NoError(s.runDue(api.ExecutionContext_ANDROID_FOREGROUND, wakeDeadline))
	s.android.requireCalls("ScheduleWake(1h30m0s)")
}

func (s *AndroidSuite) TestRunAsksForTheFloorWakeForARetry() {
	s.blockUntilDone(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(
		s.Ctx, fnA, 3*time.Hour, ba_model.RegisterConfig{Timeout: 5 * time.Second},
	))
	s.android.takeCalls()

	s.NoError(s.runDue(api.ExecutionContext_ANDROID_BACKGROUND, shortWakeDeadline))
	s.android.requireCalls("ScheduleWake(1h30m0s)")

	s.NoError(s.runDue(api.ExecutionContext_ANDROID_BACKGROUND, shortWakeDeadline))
	s.android.requireCalls("ScheduleWake(3h0m0s)")
}

func (s *AndroidSuite) TestRunEndAsksForTheWakeOfARegistrationMadeDuringIt() {
	started, release := s.blockUntilReleased(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.android.takeCalls()

	result := s.runDueInBackground(s.Ctx, api.ExecutionContext_ANDROID_BACKGROUND)
	s.awaitStart(started)

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, 5*time.Hour))
	release()
	s.NoError(<-result)

	s.android.requireCalls("ScheduleWake(5h0m0s)")
}

func (s *AndroidSuite) TestRunEndKeepsTheWakeCancelledAfterADeregisterDuringIt() {
	started, release := s.blockUntilReleased(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.android.takeCalls()

	result := s.runDueInBackground(s.Ctx, api.ExecutionContext_ANDROID_BACKGROUND)
	s.awaitStart(started)

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnA))
	s.android.requireCalls("CancelWake()")
	release()
	s.NoError(<-result)

	s.android.requireCalls("CancelWake()")
}

func (s *AndroidSuite) TestRunWithoutAWalletCancelsTheWake() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.android.takeCalls()
	s.session.loggedIn.Store(false)

	s.NoError(s.runDue(api.ExecutionContext_ANDROID_BACKGROUND, wakeDeadline))

	s.android.requireCalls("CancelWake()")
}

func (s *AndroidSuite) TestResetDataCancelsTheWake() {
	started := s.blockUntilDone(fnA)
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour))
	s.android.takeCalls()

	result := s.runDueInBackground(s.Ctx, api.ExecutionContext_ANDROID_BACKGROUND)
	s.awaitStart(started)

	s.resetDataOnLogout()

	s.NoError(<-result)
	s.android.requireCalls("CancelWake()")
}
