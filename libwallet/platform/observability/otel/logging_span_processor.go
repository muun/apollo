package otel

import (
	"context"
	"log/slog"
	"sync"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"

	"github.com/muun/libwallet/platform/observability/logging"
)

// loggingSpanProcessor is a SpanProcessor that emits one structured slog record per finished span.
type loggingSpanProcessor struct {
	mutex   sync.Mutex
	spanCtx map[spanKey]context.Context
}

type spanKey struct {
	traceID trace.TraceID
	spanID  trace.SpanID
}

func newSpanKey(span sdktrace.ReadOnlySpan) spanKey {
	spanCtx := span.SpanContext()
	return spanKey{traceID: spanCtx.TraceID(), spanID: spanCtx.SpanID()}
}

func newLoggingSpanProcessor() *loggingSpanProcessor {
	return &loggingSpanProcessor{spanCtx: map[spanKey]context.Context{}}
}

func (p *loggingSpanProcessor) OnStart(ctx context.Context, span sdktrace.ReadWriteSpan) {
	p.setSpanCtx(span, ctx)
}

func (p *loggingSpanProcessor) OnEnd(span sdktrace.ReadOnlySpan) {
	ctx := p.popSpanCtx(span)

	p.logSpan(ctx, span)
}

func (p *loggingSpanProcessor) Shutdown(context.Context) error {
	p.mutex.Lock()
	defer p.mutex.Unlock()

	clear(p.spanCtx)

	return nil
}

func (*loggingSpanProcessor) ForceFlush(context.Context) error {
	return nil
}

func (p *loggingSpanProcessor) setSpanCtx(span sdktrace.ReadOnlySpan, ctx context.Context) {
	p.mutex.Lock()
	defer p.mutex.Unlock()

	p.spanCtx[newSpanKey(span)] = ctx
}

func (p *loggingSpanProcessor) popSpanCtx(span sdktrace.ReadOnlySpan) context.Context {
	p.mutex.Lock()
	defer p.mutex.Unlock()

	key := newSpanKey(span)
	ctx, ok := p.spanCtx[key]
	if ok {
		delete(p.spanCtx, key)
	} else {
		ctx = context.Background()
	}

	return ctx
}

func (p *loggingSpanProcessor) logSpan(ctx context.Context, span sdktrace.ReadOnlySpan) {
	level := slog.LevelInfo
	if span.Status().Code == codes.Error {
		level = slog.LevelError
	}

	message := span.Name()

	attrs := []slog.Attr{
		slog.String("trace_id", span.SpanContext().TraceID().String()),
		slog.String("span_id", span.SpanContext().SpanID().String()),
		slog.Int64("span_duration_ms", span.EndTime().Sub(span.StartTime()).Milliseconds()),
	}
	if status := span.Status(); status.Code != codes.Unset {
		attrs = append(attrs, slog.String("span_status_code", status.Code.String()))
		if status.Description != "" {
			attrs = append(attrs, slog.String("span_status_description", status.Description))
		}
	}
	for _, kv := range span.Attributes() {
		if string(kv.Key) == SampleRatioAttr {
			continue
		}
		attrs = append(attrs, toSlogAttr(kv))
	}

	logging.Logger(ctx).LogAttrs(ctx, level, message, attrs...)
}

// toSlogAttr converts a span attribute into a slog one, preserving its type.
func toSlogAttr(kv attribute.KeyValue) slog.Attr {
	key := string(kv.Key)

	switch kv.Value.Type() {
	case attribute.BOOL:
		return slog.Bool(key, kv.Value.AsBool())
	case attribute.INT64:
		return slog.Int64(key, kv.Value.AsInt64())
	case attribute.FLOAT64:
		return slog.Float64(key, kv.Value.AsFloat64())
	default:
		return slog.String(key, kv.Value.Emit())
	}
}
