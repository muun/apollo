package test

import (
	"context"
	"testing"
	"time"

	"github.com/go-errors/errors"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/muun/libwallet/app_provided_data"
	app_mock "github.com/muun/libwallet/app_provided_data/mock"
	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data"
	repository_mock "github.com/muun/libwallet/subsystems/background_actions/internal/data/repository/mock"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/action"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/model"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

const (
	fnA ba_model.Function = "test.a"
	fnB ba_model.Function = "test.b"
)

func TestRunDueAction(t *testing.T) {
	suite.Run(t, new(runDueActionSuite))
}

// runDueActionSuite covers what a run does when it is canceled or its storage fails, which the
// integration harness cannot produce at a chosen point of the run.
type runDueActionSuite struct {
	suite.Suite
	*require.Assertions

	runCanceler   *data.RunCanceler
	registrations *repository_mock.MockRegistrationRepository
}

func (s *runDueActionSuite) SetupTest() {
	s.Assertions = require.New(s.T())
	s.runCanceler = data.NewRunCanceler()
	s.registrations = repository_mock.NewMockRegistrationRepository(s.T())
}

func (s *runDueActionSuite) TestRunStopsWhenCanceledWhileStoringStaticRegistrations() {
	s.registrations.EXPECT().Get(mock.Anything, fnA).RunAndReturn(
		func(ctx context.Context, _ ba_model.Function) (*model.Registration, error) {
			s.runCanceler.CancelRun(errors.New("reset"))
			return nil, ctx.Err()
		},
	).Once()

	err := s.newRunDueAction().Run(s.T().Context(), ba_model.AndroidBackground)

	s.NoError(err)
}

func (s *runDueActionSuite) TestRunGoesOnWhenStoringAStaticRegistrationFails() {
	s.registrations.EXPECT().Get(mock.Anything, mock.Anything).
		Return(nil, errors.New("storage failed"))
	s.registrations.EXPECT().List(mock.Anything).Return(nil, nil)

	err := s.newRunDueAction().Run(s.T().Context(), ba_model.AndroidBackground)

	s.NoError(err)
}

func (s *runDueActionSuite) TestRunReturnsNilWhenCanceledWhileRunningDueActions() {
	s.registrations.EXPECT().Get(mock.Anything, mock.Anything).
		Return(nil, nil).Maybe()
	s.registrations.EXPECT().Save(mock.Anything, mock.Anything).
		Return(nil).Maybe()
	s.registrations.EXPECT().List(mock.Anything).RunAndReturn(
		func(ctx context.Context) ([]*model.Registration, error) {
			s.runCanceler.CancelRun(errors.New("reset"))
			return nil, ctx.Err()
		},
	).Once()

	err := s.newRunDueAction().Run(s.T().Context(), ba_model.AndroidBackground)

	s.NoError(err)
}

func (s *runDueActionSuite) TestRunReturnsAStorageFailureWhileRunningDueActions() {
	storageErr := errors.New("storage failed")
	s.registrations.EXPECT().Get(mock.Anything, mock.Anything).
		Return(nil, nil).Maybe()
	s.registrations.EXPECT().Save(mock.Anything, mock.Anything).
		Return(nil).Maybe()
	s.registrations.EXPECT().List(mock.Anything).Return(nil, storageErr).Once()

	err := s.newRunDueAction().Run(s.T().Context(), ba_model.AndroidBackground)

	s.ErrorIs(err, storageErr)
}

// newRunDueAction returns a logged in Android RunDueAction over the suite's registrations, with
// static registrations of fnA and fnB.
func (s *runDueActionSuite) newRunDueAction() action.RunDueAction {
	session := app_mock.NewMockSessionProvider(s.T())
	session.EXPECT().IsLoggedIn().Return(true, nil).Maybe()

	locker := &memlock.NamedLocker{}
	// No bridge: syncing the wakes only lists the registrations.
	syncWakes := action.NewSyncWakesAction(app_provided_data.Android, nil, nil, s.registrations)

	noop := func(context.Context) error { return nil }

	return action.NewRunDueAction(
		app_provided_data.Android,
		session,
		locker,
		s.runCanceler,
		s.registrations,
		syncWakes,
		action.NewResetAction(locker, s.runCanceler, s.registrations, syncWakes),
		map[ba_model.Function]ba_model.Handler{fnA: noop, fnB: noop},
		[]ba_model.StaticRegistration{
			ba_model.NewStaticRegistration(fnA, time.Hour),
			ba_model.NewStaticRegistration(fnB, time.Hour),
		},
	)
}
