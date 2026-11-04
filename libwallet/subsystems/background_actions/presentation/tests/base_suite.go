package tests

import (
	"context"
	"sync"
	"time"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/emptypb"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/test/testsuite"
	"github.com/muun/libwallet/server/api"
	"github.com/muun/libwallet/subsystems/background_actions"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data/repository"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/model"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

const (
	fnA ba_model.Function = "test.a"
	fnB ba_model.Function = "test.b"
	fnC ba_model.Function = "test.c"

	// wakeDeadline is long enough for any run that is not about time.
	wakeDeadline = 10 * time.Second

	// shortWakeDeadline leaves a handler about 300 ms past the run's 1 s reserve.
	shortWakeDeadline = 1300 * time.Millisecond
)

type baseBackgroundActionsSuite struct {
	testsuite.BaseIntegrationSuite

	platform app_provided_data.Platform
	android  *mockAndroidBridge
	ios      *mockIOSBridge
	session  *mockSessionProvider
	handlers *mockHandlers

	registrationRepository repository.RegistrationRepository

	backgroundActions background_actions.BackgroundActions
}

func newBaseBackgroundActionsSuite(platform app_provided_data.Platform) baseBackgroundActionsSuite {
	return baseBackgroundActionsSuite{
		platform: platform,
	}
}

func (s *baseBackgroundActionsSuite) SetupSuite() {
	s.BaseIntegrationSuite.SetupSuite()

	s.android = newMockAndroidBridge(s.T)
	s.ios = newMockIOSBridge(s.T)
	s.session = newMockSessionProvider()
	s.handlers = newMockHandlers(s.T, fnA, fnB, fnC)

	s.registrationRepository = repository.NewRegistrationRepository(
		s.KvStore,
		s.handlers.handlers,
	)

	s.backgroundActions = background_actions.NewBackgroundActions(
		&s.Locker,
		s.KvStore,
		s.platform,
		s.android,
		s.ios,
		s.session,
		s.handlers.handlers,
		nil,
	)

	s.StartWalletServer(testsuite.WalletServerBuilder{
		BackgroundActions: s.backgroundActions,
	})
}

func (s *baseBackgroundActionsSuite) SetupTest() {
	s.BaseIntegrationSuite.SetupTest()

	s.android.reset()
	s.ios.reset()
	s.handlers.reset()
	s.session.reset()
}

// runDue calls RunDueBackgroundActions as a wake of execCtx would, with deadline on the call.
func (s *baseBackgroundActionsSuite) runDue(
	execCtx api.ExecutionContext,
	deadline time.Duration,
) error {
	ctx, cancel := context.WithTimeout(s.Ctx, deadline)
	defer cancel()
	return s.runDueWithContext(ctx, execCtx)
}

// runDueInBackground starts a RunDueBackgroundActions call on ctx, and returns where its error
// arrives.
func (s *baseBackgroundActionsSuite) runDueInBackground(
	ctx context.Context,
	execCtx api.ExecutionContext,
) <-chan error {
	result := make(chan error, 1)
	go func() {
		result <- s.runDueWithContext(ctx, execCtx)
	}()
	return result
}

func (s *baseBackgroundActionsSuite) runDueWithContext(
	ctx context.Context,
	execCtx api.ExecutionContext,
) error {
	_, err := s.Client.RunDueBackgroundActions(ctx, api.RunDueBackgroundActionsRequest_builder{
		ExecutionContext: execCtx,
	}.Build())
	return err
}

// newBackgroundActions returns the BackgroundActions an app restarted with handlers and
// staticRegistrations builds, over the suite's storage, bridges and session.
func (s *baseBackgroundActionsSuite) newBackgroundActions(
	handlers *mockHandlers,
	staticRegistrations ...ba_model.StaticRegistration,
) *background_actions.BackgroundActions {
	a := background_actions.NewBackgroundActions(
		&s.Locker,
		s.KvStore,
		s.platform,
		s.android,
		s.ios,
		s.session,
		handlers.handlers,
		staticRegistrations,
	)
	return &a
}

// runDueOn calls RunDue of a as a wake of execCtx would, with deadline on the call.
func (s *baseBackgroundActionsSuite) runDueOn(
	a *background_actions.BackgroundActions,
	execCtx api.ExecutionContext,
	deadline time.Duration,
) error {
	ctx, cancel := context.WithTimeout(s.Ctx, deadline)
	defer cancel()
	return a.RunDue(ctx, ba_model.FromProtoExecutionContext(execCtx))
}

// resetDataOnLogout calls ResetData as logout does.
func (s *baseBackgroundActionsSuite) resetDataOnLogout() {
	_, err := s.Client.ResetData(s.Ctx, &emptypb.Empty{})
	s.NoError(err)
}

// registry returns the stored registrations of the test functions, by due time.
func (s *baseBackgroundActionsSuite) registry() []*model.Registration {
	s.T().Helper()
	registrations, err := s.registrationRepository.List(s.Ctx)
	s.NoError(err)
	return registrations
}

// getRegistration returns the stored registration of fn, or nil.
func (s *baseBackgroundActionsSuite) getRegistration(fn ba_model.Function) *model.Registration {
	s.T().Helper()
	registration, err := s.registrationRepository.Get(s.Ctx, fn)
	s.NoError(err)
	return registration
}

// requireRescheduled checks that fn moved to its next period during [from, to].
func (s *baseBackgroundActionsSuite) requireRescheduled(fn ba_model.Function, from, to time.Time) {
	s.T().Helper()
	registration := s.getRegistration(fn)
	s.NotNil(registration)
	s.WithinRange(registration.DueAt, from.Add(registration.Period), to.Add(registration.Period))
	s.Zero(registration.Attempts)
}

// requireCode checks that err is a gRPC error with code.
func (s *baseBackgroundActionsSuite) requireCode(err error, code codes.Code) {
	s.T().Helper()
	s.Error(err)
	s.Equal(code, status.Code(err))
}

// blockUntilDone makes fn report on the returned channel when it starts, then block until its
// ctx is done.
func (s *baseBackgroundActionsSuite) blockUntilDone(fn ba_model.Function) <-chan struct{} {
	started := make(chan struct{}, 1)
	s.handlers.set(fn, func(ctx context.Context) error {
		started <- struct{}{}
		<-ctx.Done()
		return ctx.Err()
	})
	return started
}

// blockUntilReleased makes fn report on the returned channel when it starts, then block until
// release is called. The channel has room for the starts of two runs.
func (s *baseBackgroundActionsSuite) blockUntilReleased(
	fn ba_model.Function,
) (<-chan struct{}, func()) {
	startedChan := make(chan struct{}, 2)
	released := make(chan struct{})
	release := sync.OnceFunc(func() {
		close(released)
	})
	s.T().Cleanup(release)

	s.handlers.set(fn, func(context.Context) error {
		startedChan <- struct{}{}
		<-released
		return nil
	})
	return startedChan, release
}

// awaitStart waits until started reports a handler start.
func (s *baseBackgroundActionsSuite) awaitStart(started <-chan struct{}) {
	s.T().Helper()
	select {
	case <-started:
	case <-time.After(wakeDeadline):
		s.FailNow("handler did not start")
	}
}
