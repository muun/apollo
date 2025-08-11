package log

import (
	"io"
	"log/slog"
	"path/filepath"
	"strings"
)

// NewBridgeLogHandler returns a [slog.JSONHandler]] that forwards logs to the provided
// sink.
func NewBridgeLogHandler(sink io.Writer, level slog.Level) *slog.JSONHandler {
	opts := &slog.HandlerOptions{
		AddSource:   true,
		Level:       level,
		ReplaceAttr: replaceAttrs,
	}

	return slog.NewJSONHandler(sink, opts)
}

func replaceAttrs(groups []string, a slog.Attr) slog.Attr {
	// Trim the values in the source key.
	if a.Key == slog.SourceKey {
		source := a.Value.Any().(*slog.Source)

		// Remove the directory from the source's filename.
		source.File = filepath.Base(source.File)

		// Remove the module and package from the function's name.
		funcName := source.Function
		lastSlash := max(0, strings.LastIndexByte(funcName, '/'))
		lastDot := strings.LastIndexByte(funcName[lastSlash:], '.') + lastSlash
		source.Function = funcName[lastDot+1:]
	}
	return a
}
