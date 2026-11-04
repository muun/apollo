package tests

import (
	"context"
	"testing"
	"time"

	"github.com/go-errors/errors"
	"google.golang.org/grpc/codes"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/test/testsuite"
	"github.com/muun/libwallet/server/api"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

func TestIOSBackgroundActions_Integration(t *testing.T) {
	testsuite.Run(t, newIOSSuite())
}

// IOSSuite runs IOS specific tests.
type IOSSuite struct {
	baseBackgroundActionsSuite
}

func newIOSSuite() *IOSSuite {
	return &IOSSuite{
		newBaseBackgroundActionsSuite(app_provided_data.IOS),
	}
}

// fitsRefresh and needsProcessing are the configs of a function that fits a refresh launch,
// and of one that only fits a processing launch.
var (
	fitsRefresh     = ba_model.RegisterConfig{Timeout: 20 * time.Second}
	needsProcessing = ba_model.RegisterConfig{Timeout: 4 * time.Minute, MinRunTime: time.Minute}
)

func (s *IOSSuite) TestRegisterArmsBothRequests() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, fitsRefresh))
	s.ios.requireCalls("ScheduleRefreshWake(0s)", "ScheduleProcessingWake(0s)")

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, time.Hour, fitsRefresh))
	s.ios.requireNoCalls()
}

func (s *IOSSuite) TestRegisterArmsARefreshOnlyForAFunctionThatFitsIt() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, needsProcessing))
	s.ios.requireCalls("CancelRefreshWake()", "ScheduleProcessingWake(0s)")

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, time.Hour, fitsRefresh))
	s.ios.requireCalls("ScheduleRefreshWake(0s)", "ScheduleProcessingWake(0s)")
}

func (s *IOSSuite) TestReRegisterThatNoLongerFitsARefreshCancelsItAtRunEnd() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, needsProcessing))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, time.Hour, fitsRefresh))
	s.ios.takeCalls()

	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, time.Hour, needsProcessing))
	s.ios.requireNoCalls()

	s.NoError(s.runDue(api.ExecutionContext_IOS_BACKGROUND_PROCESSING, wakeDeadline))
	s.ios.requireCalls("CancelRefreshWake()", "ScheduleProcessingWake(1h0m0s)")
}

func (s *IOSSuite) TestDeregisterCancelsWhatTheRegistryNoLongerNeeds() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, needsProcessing))
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnB, time.Hour, fitsRefresh))
	s.ios.takeCalls()

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnB))
	s.ios.requireCalls("CancelRefreshWake()", "ScheduleProcessingWake(0s)")

	s.NoError(s.backgroundActions.Deregister(s.Ctx, fnA))
	s.ios.requireCalls("CancelRefreshWake()", "CancelProcessingWake()")
}

func (s *IOSSuite) TestLaunchDatesOnlyItsOwnRequest() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, fitsRefresh))
	s.ios.takeCalls()

	s.NoError(s.runDue(api.ExecutionContext_IOS_BACKGROUND_PROCESSING, wakeDeadline))
	s.ios.requireCalls("ScheduleProcessingWake(1h0m0s)")

	s.NoError(s.runDue(api.ExecutionContext_IOS_BACKGROUND_REFRESH, wakeDeadline))
	s.ios.requireCalls("ScheduleRefreshWake(15m0s)")

	s.handlers.requireRan(fnA, fnA)
}

func (s *IOSSuite) TestForegroundRunArmsBothRequestsBeforeAndAfterRunning() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, fitsRefresh))
	s.ios.takeCalls()

	var callsBeforeRunning []string
	s.handlers.set(fnA, func(context.Context) error {
		callsBeforeRunning = s.ios.takeCalls()
		return nil
	})

	s.NoError(s.runDue(api.ExecutionContext_IOS_FOREGROUND, wakeDeadline))

	s.Equal(
		[]string{"ScheduleRefreshWake(0s)", "ScheduleProcessingWake(0s)"},
		callsBeforeRunning,
	)
	s.ios.requireCalls("ScheduleRefreshWake(0s)", "ScheduleProcessingWake(0s)")
}

func (s *IOSSuite) TestFirstForegroundRunArmsBothRequestsForTheStaticRegistrations() {
	var callsBeforeRunning []string
	s.handlers.set(fnA, func(context.Context) error {
		callsBeforeRunning = s.ios.takeCalls()
		return nil
	})
	withStaticRegistration := s.newBackgroundActions(
		s.handlers, ba_model.NewStaticRegistration(fnA, time.Hour),
	)

	err := s.runDueOn(withStaticRegistration, api.ExecutionContext_IOS_FOREGROUND, wakeDeadline)
	s.NoError(err)

	s.Equal(
		[]string{"ScheduleRefreshWake(0s)", "ScheduleProcessingWake(0s)"},
		callsBeforeRunning,
	)
}

func (s *IOSSuite) TestRunCancelsTheRefreshWhenNoFunctionFitsIt() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, needsProcessing))
	s.ios.takeCalls()

	s.NoError(s.runDue(api.ExecutionContext_IOS_BACKGROUND_PROCESSING, wakeDeadline))
	s.ios.requireCalls("CancelRefreshWake()", "ScheduleProcessingWake(1h0m0s)")

	s.NoError(s.runDue(api.ExecutionContext_IOS_FOREGROUND, wakeDeadline))
	s.ios.requireCalls(
		"CancelRefreshWake()",
		"ScheduleProcessingWake(0s)",
		"CancelRefreshWake()",
		"ScheduleProcessingWake(0s)",
	)
}

func (s *IOSSuite) TestRunRunsInARefreshAFunctionAtTheRefreshLimit() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, ba_model.RegisterConfig{
		Timeout:    20 * time.Second,
		MinRunTime: 20 * time.Second,
	}))
	s.ios.requireCalls("ScheduleRefreshWake(0s)", "ScheduleProcessingWake(0s)")

	s.NoError(s.runDue(api.ExecutionContext_IOS_BACKGROUND_REFRESH, 25*time.Second))

	s.handlers.requireRan(fnA)
}

func (s *IOSSuite) TestForegroundRunRunsWhenArmingFails() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, fitsRefresh))
	s.ios.takeCalls()
	s.ios.failWith(errors.New("too many pending task requests"))

	err := s.runDue(api.ExecutionContext_IOS_FOREGROUND, wakeDeadline)

	s.requireCode(err, codes.Internal)
	s.handlers.requireRan(fnA)
}

func (s *IOSSuite) TestForegroundRunSucceedsWhenTheBGTaskSchedulerIsUnavailable() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, fitsRefresh))
	s.ios.takeCalls()
	s.ios.failWith(app_provided_data.ErrBGTaskSchedulerUnavailable)

	s.NoError(s.runDue(api.ExecutionContext_IOS_FOREGROUND, wakeDeadline))

	s.handlers.requireRan(fnA)
}

func (s *IOSSuite) TestBackgroundRunSucceedsWhenTheBGTaskSchedulerIsUnavailable() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, fitsRefresh))
	s.ios.takeCalls()
	s.ios.failWith(app_provided_data.ErrBGTaskSchedulerUnavailable)

	s.NoError(s.runDue(api.ExecutionContext_IOS_BACKGROUND_PROCESSING, wakeDeadline))

	s.handlers.requireRan(fnA)
}

func (s *IOSSuite) TestRunFailsWhenOnlyOneWakeHasTheBGTaskSchedulerUnavailable() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, fitsRefresh))
	s.ios.takeCalls()
	s.ios.failCallWith("ScheduleRefreshWake(0s)", app_provided_data.ErrBGTaskSchedulerUnavailable)
	s.ios.failCallWith("ScheduleProcessingWake(0s)", errors.New("too many pending task requests"))

	err := s.runDue(api.ExecutionContext_IOS_FOREGROUND, wakeDeadline)

	s.requireCode(err, codes.Internal)
}

func (s *IOSSuite) TestEveryCancelIsMadeWhenOneFails() {
	s.NoError(s.backgroundActions.RegisterPeriodic(s.Ctx, fnA, time.Hour, fitsRefresh))
	s.ios.takeCalls()
	s.ios.failWith(errors.New("background app refresh is off"))

	s.resetDataOnLogout()

	s.ios.requireCalls("CancelRefreshWake()", "CancelProcessingWake()")
}
