// Package tracing provides an API that encapsulates OpenTelemetry spans.
// All configuration and data travels in the context.
package tracing

import (
	"context"
	"reflect"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
	"go.opentelemetry.io/otel/trace"

	"github.com/muun/libwallet/platform/observability/otel"
)

const tracerName = "github.com/muun/libwallet"

// Trace is a handle to a span. The zero value is safe.
type Trace struct {
	span trace.Span
}

func (t Trace) IsValid() bool {
	return t.span != nil && t.span.SpanContext().IsValid()
}

func (t Trace) Finish() {
	if t.IsValid() {
		t.span.End()
	}
}

// Current returns the current context's span.
func Current(ctx context.Context) Trace {
	return Trace{span: trace.SpanFromContext(ctx)}
}

// tracer returns the package's named Tracer pulled from the TracerProvider stored on ctx.
func tracer(ctx context.Context) trace.Tracer {
	if t := Current(ctx); t.IsValid() {
		return t.span.TracerProvider().Tracer(tracerName)
	}
	return GetOpenTelemetrySetup(ctx).TracerProvider.Tracer(tracerName)
}

// Start opens a new root span (no parent) and returns a context that carries it.
func Start(
	ctx context.Context,
	name string,
	sampleRatio float64,
	tags ...any,
) (context.Context, Trace) {
	attrs := toAttributes(tags)
	attrs = append(attrs, attribute.Float64(otel.SampleRatioAttr, sampleRatio))

	opts := []trace.SpanStartOption{
		trace.WithNewRoot(),
		trace.WithAttributes(attrs...),
	}

	ctx, span := tracer(ctx).Start(ctx, name, opts...)
	return ctx, Trace{span}
}

// StartWithoutSampling is like Start but without sampling. You either record all traces, or none.
func StartWithoutSampling(
	ctx context.Context,
	name string,
	sample bool,
	tags ...any,
) (context.Context, Trace) {
	sampleRatio := 0.0
	if sample {
		sampleRatio = 1.0
	}
	return Start(ctx, name, sampleRatio, tags...)
}

// NewSpan is like Trace.NewSpan, but for the current context's Trace.
func NewSpan(ctx context.Context, name string, tags ...any) (context.Context, Trace) {
	return Current(ctx).NewSpan(ctx, name, tags...)
}

// NewSpan creates a child span and returns a context carrying it.
func (t Trace) NewSpan(ctx context.Context, name string, tags ...any) (context.Context, Trace) {
	if !t.IsValid() {
		return ctx, t
	}

	opts := []trace.SpanStartOption{
		trace.WithAttributes(toAttributes(tags)...),
	}

	ctx, span := tracer(ctx).Start(ctx, name, opts...)
	return ctx, Trace{span: span}
}

// AddTag is like Trace.AddTag, but for the context's current Trace.
func AddTag(ctx context.Context, key string, value any) Trace {
	return Current(ctx).AddTag(key, value)
}

// AddTag sets a key/value tag on the span. Repeat calls with the same key overwrite.
func (t Trace) AddTag(key string, value any) Trace {
	if t.IsValid() {
		t.span.SetAttributes(toAttribute(key, value))
	}
	return t
}

// RecordError is like Trace.RecordError, but for the current context's Trace.
func RecordError(ctx context.Context, err error, tags ...any) Trace {
	return Current(ctx).RecordError(err, tags...)
}

// RecordError attaches an error to the span as an exception event and sets the
// span status to Error. Extra args are converted to event tags.
func (t Trace) RecordError(err error, tags ...any) Trace {
	if !t.IsValid() || err == nil {
		return t
	}

	if len(tags) > 0 {
		t.span.RecordError(err, trace.WithAttributes(toAttributes(tags)...))
	} else {
		t.span.RecordError(err)
	}

	t.span.SetStatus(codes.Error, err.Error())

	return t
}

// RecordErrorInline records the error on ctx's current span and sets status to Error.
func RecordErrorInline(ctx context.Context, err error, tags ...any) Trace {
	return Current(ctx).RecordErrorInline(err, tags...)
}

// RecordErrorInline records the error the same way RecordError does and additionally stamps
// `exception.type` and `exception.message` tags on the span itself, so consumers can query errors
// without expanding events.
func (t Trace) RecordErrorInline(err error, tags ...any) Trace {
	if !t.IsValid() || err == nil {
		return t
	}

	t.RecordError(err, tags...)
	t.span.SetAttributes(
		semconv.ExceptionType(reflect.TypeOf(err).String()),
		semconv.ExceptionMessage(err.Error()),
	)

	return t
}

// AddEvent is like Trace.AddEvent, but for the current context's Trace.
func AddEvent(ctx context.Context, name string, tags ...any) Trace {
	return Current(ctx).AddEvent(name, tags...)
}

// AddEvent records a named event on the span with the given tags.
func (t Trace) AddEvent(name string, tags ...any) Trace {
	if t.IsValid() {
		t.span.AddEvent(name, trace.WithAttributes(toAttributes(tags)...))
	}
	return t
}

// WithSpan calls NewSpan, runs fn with the new context created, and ends the span.
// A non-nil error from fn is recorded with Trace.RecordErrorInline.
func WithSpan(ctx context.Context, name string, fn func(context.Context) error) error {
	ctx, span := NewSpan(ctx, name)
	defer span.Finish()

	err := fn(ctx)
	if err != nil {
		span.RecordErrorInline(err)
	}

	return err
}

// WithSpan1 is like WithSpan for functions that return (T, error).
// The result is forwarded. A non-nil error is recorded with RecordErrorInline.
func WithSpan1[T any](
	ctx context.Context,
	name string,
	fn func(context.Context) (T, error),
) (T, error) {
	ctx, span := NewSpan(ctx, name)
	defer span.Finish()

	ret, err := fn(ctx)
	if err != nil {
		span.RecordErrorInline(err)
	}

	return ret, err
}
