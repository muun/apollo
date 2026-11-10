package app_provided_data

// AppLogSink is the interface we provide to the containing application's log infrastructure.
type AppLogSink interface {
	// GetMinLogLevel returns the lowest level the app logs.
	// It should be a slog.Level, but gomobile can't handle it.
	GetMinLogLevel() int

	// Log logs a line at a slog level, with the file, line and function of the log call. The
	// message is ready to log.
	//
	// Libwallet ignores the error, which is there so gomobile clears
	// what the app throws instead of leaving it pending on the thread that logged.
	Log(level int, message string, file string, line int, function string) error
}
