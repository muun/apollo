package tests

import (
	"fmt"
	"testing"
	"time"
)

// mockIOSBridge records every call it gets, with delays rounded to the second.
type mockIOSBridge struct {
	callRecorder
}

func newMockIOSBridge(t func() *testing.T) *mockIOSBridge {
	return &mockIOSBridge{callRecorder: newCallRecorder(t)}
}

func (b *mockIOSBridge) ScheduleRefreshWake(minDelayMillis int64) error {
	b.t().Helper()
	return b.record(fmt.Sprintf("ScheduleRefreshWake(%v)", b.roundedDelay(minDelayMillis)))
}

func (b *mockIOSBridge) ScheduleProcessingWake(minDelayMillis int64) error {
	b.t().Helper()
	return b.record(fmt.Sprintf("ScheduleProcessingWake(%v)", b.roundedDelay(minDelayMillis)))
}

func (b *mockIOSBridge) CancelProcessingWake() error {
	b.t().Helper()
	return b.record("CancelProcessingWake()")
}

func (b *mockIOSBridge) CancelRefreshWake() error {
	b.t().Helper()
	b.t().Helper()
	return b.record("CancelRefreshWake()")
}

func (b *mockIOSBridge) roundedDelay(delayMillis int64) time.Duration {
	b.t().Helper()
	return (time.Duration(delayMillis) * time.Millisecond).Round(time.Second)
}
