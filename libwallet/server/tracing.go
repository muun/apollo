package server

import (
	"context"
	"fmt"
	"path"
	"slices"
	"time"

	"go.opentelemetry.io/otel/baggage"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/observability/logging"
	"github.com/muun/libwallet/platform/observability/otel"
	"github.com/muun/libwallet/platform/observability/tracing"
)

// spanFlushTimeout bounds how long a flush delays the response.
const spanFlushTimeout = 3 * time.Second

// spanFlushMargin is how long before the caller's deadline a flush stops, so the response still
// arrives in time.
const spanFlushMargin = 200 * time.Millisecond

// tracingInterceptorHandler creates the requests' tracing root span, adds common tags, and logs the
// request result.
// OpenTelemetry is configured for the request only if an otel.Setup is provided.
func tracingInterceptorHandler(
	ctx context.Context,
	otelSetup *otel.Setup,
	session app_provided_data.SessionProvider,
	forceFlushMethods []string,
	fullMethod string,
	handler func(ctx context.Context) error,
) error {
	if otelSetup == nil {
		return handler(ctx)
	}

	ctx = tracing.WithOpenTelemetrySetup(ctx, otelSetup)
	if slices.Contains(forceFlushMethods, fullMethod) {
		// Deferred before the root span's Finish, so it runs once the span has ended.
		defer forceFlushSpans(ctx, otelSetup)
	}

	method := path.Base(fullMethod)
	name := otelSetup.ServiceName + " " + method
	ctx, err := addPropagatedTag(ctx, "execution_unit", "request: "+name)
	if err != nil {
		logging.Error(ctx, "Failed to add execution_unit propagated tag",
			"method", method,
			"error", err,
		)
	}

	if session != nil {
		userResponse, err := session.GetUser()
		if err != nil {
			logging.Error(ctx, "Failed to get the logged-in user",
				"method", method,
				"error", err,
			)
		} else if userResponse != nil && userResponse.User != nil {
			user := userResponse.User
			ctx, err = addPropagatedTag(ctx, "user", fmt.Sprintf("user-%d", user.HoustonID))
			if err != nil {
				logging.Error(ctx, "Failed to add user propagated tag",
					"method", method,
					"error", err,
				)
			}
		}
	}

	ctx, trace := tracing.StartWithoutSampling(ctx, name, true)
	defer trace.Finish()

	tracing.AddTag(ctx, "method", method)

	err = handler(ctx)

	// A call the caller canceled is not a server error.
	errCode := status.Code(err)
	if errCode != codes.OK && errCode != codes.Canceled {
		trace.RecordErrorInline(err)
	}

	tracing.AddTag(ctx, "grpc_status_code", status.Code(err).String())

	return err
}

// forceFlushSpans exports otelSetup's pending spans for up to spanFlushTimeout. While the caller
// still waits for the response, it also stops spanFlushMargin before ctx's deadline.
func forceFlushSpans(ctx context.Context, otelSetup *otel.Setup) {
	timeout := spanFlushTimeout
	if ctx.Err() != nil {
		// Even if the caller canceled or its deadline passed, we want to flush the spans.
		ctx = context.WithoutCancel(ctx)
	} else if deadline, ok := ctx.Deadline(); ok {
		// A response past the caller's deadline would turn a finished request into a failure.
		timeout = min(timeout, time.Until(deadline)-spanFlushMargin)
	}

	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	if err := otelSetup.ForceFlush(ctx); err != nil {
		logging.Warn(ctx, "Failed to flush spans", "error", err)
	}
}

// addPropagatedTag adds a tag to the OTel baggage to be attached to all spans created.
func addPropagatedTag(ctx context.Context, key, value string) (context.Context, error) {
	member, err := baggage.NewMemberRaw(key, value)
	if err != nil {
		return ctx, err
	}

	bag, err := baggage.FromContext(ctx).SetMember(member)
	if err != nil {
		return ctx, err
	}

	return baggage.ContextWithBaggage(ctx, bag), nil
}
