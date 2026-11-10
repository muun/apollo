package tests

import (
	"fmt"
	"testing"
	"time"
)

// mockAndroidBridge records every call it gets, with delays rounded to the second.
type mockAndroidBridge struct {
	callRecorder
}

func newMockAndroidBridge(t func() *testing.T) *mockAndroidBridge {
	return &mockAndroidBridge{callRecorder: newCallRecorder(t)}
}

func (b *mockAndroidBridge) ScheduleWake(minDelayMillis int64) error {
	b.t().Helper()
	return b.record(fmt.Sprintf("ScheduleWake(%v)", b.roundedDelay(minDelayMillis)))
}

func (b *mockAndroidBridge) CancelWake() error {
	b.t().Helper()
	return b.record("CancelWake()")
}

func (b *mockAndroidBridge) roundedDelay(delayMillis int64) time.Duration {
	b.t().Helper()
	return (time.Duration(delayMillis) * time.Millisecond).Round(time.Second)
}
