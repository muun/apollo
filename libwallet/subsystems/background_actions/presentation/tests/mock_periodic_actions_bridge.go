package tests

import (
	"sync/atomic"
	"testing"
	"time"

	"github.com/go-errors/errors"
)

// mockPeriodicActionsBridge records every call it gets. Once it times out, every call uses up
// its timeout before failing.
type mockPeriodicActionsBridge struct {
	callRecorder
	timingOut atomic.Bool
}

func newMockPeriodicActionsBridge(t func() *testing.T) *mockPeriodicActionsBridge {
	return &mockPeriodicActionsBridge{callRecorder: newCallRecorder(t)}
}

func (b *mockPeriodicActionsBridge) PullNotifications(timeoutMillis int64) error {
	b.t().Helper()
	return b.run("PullNotifications()", timeoutMillis)
}

func (b *mockPeriodicActionsBridge) SyncRealTimeData(timeoutMillis int64) error {
	b.t().Helper()
	return b.run("SyncRealTimeData()", timeoutMillis)
}

func (b *mockPeriodicActionsBridge) RegisterInvoices(timeoutMillis int64) error {
	b.t().Helper()
	return b.run("RegisterInvoices()", timeoutMillis)
}

// timeOut makes every call use up its timeout, then fail like a native timeout.
func (b *mockPeriodicActionsBridge) timeOut() {
	b.t().Helper()
	b.timingOut.Store(true)
	b.failWith(errors.New("native timeout"))
}

func (b *mockPeriodicActionsBridge) run(call string, timeoutMillis int64) error {
	b.t().Helper()

	err := b.record(call)
	if b.timingOut.Load() {
		// Spins instead of sleeping, since a sleep can overshoot the timeout by over a millisecond.
		timeout := time.Duration(timeoutMillis) * time.Millisecond
		for start := time.Now(); time.Since(start) < timeout; {
		}
	}

	return err
}
