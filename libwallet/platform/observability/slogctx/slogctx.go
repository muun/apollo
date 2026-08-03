package slogctx

import (
	"context"
	"log/slog"
)

type ctxKey struct{}

// WithLogger returns a new context with the given logger stored in it.
func WithLogger(ctx context.Context, logger *slog.Logger) context.Context {
	return context.WithValue(ctx, ctxKey{}, logger)
}

// Logger retrieves the logger from the context, or returns slog.Default() if none is set.
func Logger(ctx context.Context) *slog.Logger {
	if logger, ok := ctx.Value(ctxKey{}).(*slog.Logger); ok && logger != nil {
		return logger
	}
	return slog.Default()
}

// With returns a new context whose logger has the given attributes added.
func With(ctx context.Context, args ...any) context.Context {
	return WithLogger(ctx, Logger(ctx).With(args...))
}

// WithGroup returns a new context whose logger has the given group name.
func WithGroup(ctx context.Context, name string) context.Context {
	return WithLogger(ctx, Logger(ctx).WithGroup(name))
}

// Debug logs at DebugLevel using the logger from ctx.
func Debug(ctx context.Context, msg string, args ...any) {
	Logger(ctx).DebugContext(ctx, msg, args...)
}

// Info logs at InfoLevel using the logger from ctx.
func Info(ctx context.Context, msg string, args ...any) {
	Logger(ctx).InfoContext(ctx, msg, args...)
}

// Warn logs at WarnLevel using the logger from ctx.
func Warn(ctx context.Context, msg string, args ...any) {
	Logger(ctx).WarnContext(ctx, msg, args...)
}

// Error logs at ErrorLevel using the logger from ctx.
func Error(ctx context.Context, msg string, args ...any) {
	Logger(ctx).ErrorContext(ctx, msg, args...)
}
