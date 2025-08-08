package app_provided_data

import (
	"io"
)

// AppLogSink is the interface we provide to the containing application's log infrastructure.
// It's really just an [io.Writer] but we have to provide our own name for it to make gobind
// export it as an ObjC protocol or a Java interface.
type AppLogSink interface {
	GetDefaultLogLevel() int // This should be a slog.Level, but gomobile can't handle it.
	io.Writer
}
