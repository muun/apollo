package tracing

import (
	"context"

	"github.com/muun/libwallet/platform/observability/otel"
)

type otelSetupKey struct{}

// WithOpenTelemetrySetup returns a context that carries the otel.Setup provided.
func WithOpenTelemetrySetup(ctx context.Context, setup *otel.Setup) context.Context {
	return context.WithValue(ctx, otelSetupKey{}, setup)
}

// GetOpenTelemetrySetup returns the otel.Setup on context, or a no-op setup when none is set.
func GetOpenTelemetrySetup(ctx context.Context) *otel.Setup {
	if v, ok := ctx.Value(otelSetupKey{}).(*otel.Setup); ok && v != nil {
		return v
	}
	return otel.NoopSetup()
}
