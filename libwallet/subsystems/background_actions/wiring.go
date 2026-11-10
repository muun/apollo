package background_actions

import (
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data"
	"github.com/muun/libwallet/subsystems/background_actions/internal/data/repository"
	"github.com/muun/libwallet/subsystems/background_actions/internal/domain/action"
	"github.com/muun/libwallet/subsystems/background_actions/internal/presentation"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

type BackgroundActions = presentation.BackgroundActions

func NewBackgroundActions(
	locker *memlock.NamedLocker,
	keyValueStorage *storage.KeyValueStorage,
	platform app_provided_data.Platform,
	androidBridge app_provided_data.AndroidBackgroundActionsBridge,
	iosBridge app_provided_data.IOSBackgroundActionsBridge,
	session app_provided_data.SessionProvider,
	handlers map[ba_model.Function]ba_model.Handler,
	staticRegistrations []ba_model.StaticRegistration,
) BackgroundActions {
	runCanceler := data.NewRunCanceler()
	registrations := repository.NewRegistrationRepository(keyValueStorage, handlers)
	syncWakes := action.NewSyncWakesAction(platform, androidBridge, iosBridge, registrations)
	reset := action.NewResetAction(locker, runCanceler, registrations, syncWakes)

	return presentation.NewBackgroundActions(
		action.NewRegisterPeriodicAction(platform, locker, registrations, syncWakes, handlers),
		action.NewDeregisterAction(platform, locker, registrations, syncWakes, handlers),
		action.NewRunDueAction(
			platform,
			session,
			locker,
			runCanceler,
			registrations,
			syncWakes,
			reset,
			handlers,
			staticRegistrations,
		),
		reset,
	)
}
