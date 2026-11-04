package app_provided_data

import "github.com/go-errors/errors"

// ErrBGTaskSchedulerUnavailable is what the iOS schedule wake methods return when
// BGTaskScheduler.submit fails with BGTaskScheduler.Error.Code.unavailable.
var ErrBGTaskSchedulerUnavailable error = errors.New("BGTaskScheduler.Error.Code.unavailable")

// AndroidBackgroundActionsBridge is how the Background Actions subsystem asks Android for
// background wakes.
type AndroidBackgroundActionsBridge interface {
	// ScheduleWake asks for a wake after minDelayMillis.
	ScheduleWake(minDelayMillis int64) error

	// CancelWake cancels the pending wake, if any.
	CancelWake() error
}

// IOSBackgroundActionsBridge is how the Background Actions subsystem asks iOS for background wakes.
type IOSBackgroundActionsBridge interface {
	// ScheduleRefreshWake asks for a refresh background task to wake after minDelayMillis.
	ScheduleRefreshWake(minDelayMillis int64) error

	// ScheduleProcessingWake asks for a processing background task to wake after minDelayMillis.
	ScheduleProcessingWake(minDelayMillis int64) error

	// CancelProcessingWake withdraws the pending processing background task, if any.
	CancelProcessingWake() error

	// CancelRefreshWake withdraws the pending refresh background task, if any.
	CancelRefreshWake() error
}
