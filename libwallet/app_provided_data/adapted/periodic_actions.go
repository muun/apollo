package adapted

import (
	"context"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/preconditions"
)

// PeriodicActionsBridge defines the app's actions that the Background Actions subsystem calls
// periodically. Each call blocks until the action ends, and returns an error when it fails or does
// not end within the context deadline.
type PeriodicActionsBridge interface {
	PullNotifications(ctx context.Context) error
	SyncRealTimeData(ctx context.Context) error
	RegisterInvoices(ctx context.Context) error
}

type periodicActionsBridge struct {
	bridge app_provided_data.PeriodicActionsBridge
}

func NewPeriodicActionsBridge(
	bridge app_provided_data.PeriodicActionsBridge,
) PeriodicActionsBridge {
	preconditions.CheckNotNilf(bridge, "missing periodic actions bridge")
	return &periodicActionsBridge{bridge: bridge}
}

func (a *periodicActionsBridge) PullNotifications(ctx context.Context) error {
	return a.run(ctx, a.bridge.PullNotifications)
}

func (a *periodicActionsBridge) SyncRealTimeData(ctx context.Context) error {
	return a.run(ctx, a.bridge.SyncRealTimeData)
}

func (a *periodicActionsBridge) RegisterInvoices(ctx context.Context) error {
	return a.run(ctx, a.bridge.RegisterInvoices)
}

// run calls action with the time left until ctx's deadline, unless none is left.
func (a *periodicActionsBridge) run(
	ctx context.Context,
	action func(timeoutMillis int64) error,
) error {
	if err := ctx.Err(); err != nil {
		return err
	}

	deadline, ok := ctx.Deadline()
	preconditions.CheckStatef(ok, "periodic action without a deadline")

	remaining := time.Until(deadline)
	if remaining <= 0 {
		return context.DeadlineExceeded
	}

	// Add a millisecond so the native timeout does not fire before ctx's deadline.
	timeoutMillis := remaining.Milliseconds() + 1
	err := action(timeoutMillis)

	// ctx.Err() can lag behind the deadline, so check the clock to report the timeout.
	if err != nil && !time.Now().Before(deadline) {
		return errors.Errorf("%w: %w", context.DeadlineExceeded, err)
	}

	return err
}
