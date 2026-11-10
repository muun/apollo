// Package logging is libwallet's slog facade.
// Loggers travel and are configured through context.
package logging

import (
	"context"
	"log/slog"
	"runtime"
	"time"
)

type ctxKey struct{}

// WithLogger returns a new context with the given logger stored in it.
func WithLogger(ctx context.Context, logger *slog.Logger) context.Context {
	return context.WithValue(ctx, ctxKey{}, logger)
}

// Logger returns the logger on ctx, or slog.Default when ctx has none.
func Logger(ctx context.Context) *slog.Logger {
	if logger, ok := ctx.Value(ctxKey{}).(*slog.Logger); ok && logger != nil {
		return logger
	}
	return slog.Default()
}

// With returns a context whose logger has the given attributes added.
func With(ctx context.Context, args ...any) context.Context {
	return WithLogger(ctx, Logger(ctx).With(args...))
}

// WithGroup returns a context whose logger is grouped under name.
func WithGroup(ctx context.Context, name string) context.Context {
	return WithLogger(ctx, Logger(ctx).WithGroup(name))
}

// Debug logs at DebugLevel using the logger from ctx.
func Debug(ctx context.Context, msg string, args ...any) {
	log(ctx, slog.LevelDebug, msg, args)
}

// Info logs at InfoLevel using the logger from ctx.
func Info(ctx context.Context, msg string, args ...any) {
	log(ctx, slog.LevelInfo, msg, args)
}

// Warn logs at WarnLevel using the logger from ctx.
func Warn(ctx context.Context, msg string, args ...any) {
	log(ctx, slog.LevelWarn, msg, args)
}

// Error logs at ErrorLevel using the logger from ctx.
func Error(ctx context.Context, msg string, args ...any) {
	log(ctx, slog.LevelError, msg, args)
}

// log logs with the caller of Debug, Info, Warn or Error as the source, not this file.
func log(ctx context.Context, level slog.Level, msg string, args []any) {
	logger := Logger(ctx)
	if !logger.Enabled(ctx, level) {
		return
	}

	var pc [1]uintptr
	runtime.Callers(3, pc[:]) // runtime.Callers, log and the exported helper

	record := slog.NewRecord(time.Now(), level, msg, pc[0])
	record.Add(args...)

	_ = logger.Handler().Handle(ctx, record)
}
