package otel_test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/require"
	"go.opentelemetry.io/otel/attribute"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	oteltrace "go.opentelemetry.io/otel/trace"

	"github.com/muun/libwallet/platform/observability/otel"
)

// Trace IDs paired with the position their hash lands on in the [0, 1) sampling space.
// Computed independently of the sampler implementation, so they also pin compatibility with
// Java's Sampler#shouldSampleBasedOnRatio.
const (
	traceIDAt06 = "0000000000000000000000000000000c"
	traceIDAt24 = "00000000000000000000000000000001"
	traceIDAt41 = "ffffffffffffffffffffffffffffffff"
	traceIDAt70 = "deadbeefdeadbeefdeadbeefdeadbeef"
	traceIDAt82 = "0102030405060708090a0b0c0d0e0f10"
	traceIDAt93 = "0000000000000000000000000000001c"
)

func TestMuunSampler_RatioIsDeterministicOnTraceID(t *testing.T) {
	tests := []struct {
		traceID string
		ratio   float64
		sampled bool
	}{
		// Trace ID at 0.06: sampled by every ratio above it.
		{traceIDAt06, 0.1, true},
		{traceIDAt06, 0.5, true},
		{traceIDAt06, 0.9, true},

		// Trace ID at 0.93: dropped by every ratio below it.
		{traceIDAt93, 0.1, false},
		{traceIDAt93, 0.5, false},
		{traceIDAt93, 0.9, false},

		// The rest straddle the ratios they are compared against.
		{traceIDAt24, 0.1, false},
		{traceIDAt24, 0.5, true},
		{traceIDAt41, 0.1, false},
		{traceIDAt41, 0.5, true},
		{traceIDAt70, 0.5, false},
		{traceIDAt70, 0.9, true},
		{traceIDAt82, 0.5, false},
		{traceIDAt82, 0.9, true},
	}

	for _, tt := range tests {
		result := sample(t, params{traceID: tt.traceID, ratio: &tt.ratio})

		want := sdktrace.RecordOnly
		if tt.sampled {
			want = sdktrace.RecordAndSample
		}
		require.Equalf(t, want, result.Decision,
			"traceID %s with ratio %v", tt.traceID, tt.ratio,
		)
	}
}

func TestMuunSampler_RatioBounds(t *testing.T) {
	// Ratios at or beyond the bounds decide without hashing, so every trace ID agrees.
	tests := []struct {
		name  string
		ratio float64
		want  sdktrace.SamplingDecision
	}{
		{"always sample", 1, sdktrace.RecordAndSample},
		{"above always sample", 1.5, sdktrace.RecordAndSample},
		{"never sample", 0, sdktrace.RecordOnly},
		{"below never sample", -0.5, sdktrace.RecordOnly},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			for _, traceID := range []string{traceIDAt06, traceIDAt41, traceIDAt93} {
				result := sample(t, params{traceID: traceID, ratio: &tt.ratio})
				require.Equalf(t, tt.want, result.Decision, "traceID %s", traceID)
			}
		})
	}
}

func TestMuunSampler_RatioOverridesParentDecision(t *testing.T) {
	// A span that carries a ratio makes its own decision, regardless of its parent's.
	// This is what keeps sampling deterministic across services.
	always, never := 1.0, 0.0

	tests := []struct {
		name         string
		ratio        *float64
		parentSample bool
		want         sdktrace.SamplingDecision
	}{
		{"always sample under unsampled parent", &always, false, sdktrace.RecordAndSample},
		{"never sample under sampled parent", &never, true, sdktrace.RecordOnly},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := sample(t, params{
				traceID:       traceIDAt41,
				ratio:         tt.ratio,
				hasParent:     true,
				parentSampled: tt.parentSample,
			})
			require.Equal(t, tt.want, result.Decision)
		})
	}
}

func TestMuunSampler_WithoutRatioFollowsParent(t *testing.T) {
	tests := []struct {
		name          string
		hasParent     bool
		parentSampled bool
		want          sdktrace.SamplingDecision
	}{
		{"sampled parent is sampled", true, true, sdktrace.RecordAndSample},
		{"unsampled parent is dropped", true, false, sdktrace.Drop},
		{"root span is dropped", false, false, sdktrace.Drop},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := sample(t, params{
				traceID:       traceIDAt06,
				hasParent:     tt.hasParent,
				parentSampled: tt.parentSampled,
			})
			require.Equal(t, tt.want, result.Decision)
		})
	}
}

func TestMuunSampler_IgnoresUnrelatedAttributes(t *testing.T) {
	// A span whose attributes do not include the sample ratio falls back to its parent.
	result := sample(t, params{
		traceID:       traceIDAt93,
		hasParent:     true,
		parentSampled: true,
		extraAttrs: []attribute.KeyValue{
			attribute.Float64("sample_rate", 0),
			attribute.String(otel.SampleRatioAttr+"_suffix", "1"),
		},
	})

	require.Equal(t, sdktrace.RecordAndSample, result.Decision)
}

func TestMuunSampler_PropagatesParentTraceState(t *testing.T) {
	traceState, err := oteltrace.ParseTraceState("muun=on")
	require.NoError(t, err)

	ratio := 1.0
	result := sample(t, params{
		traceID:       traceIDAt41,
		ratio:         &ratio,
		hasParent:     true,
		parentSampled: true,
		traceState:    traceState,
	})

	require.Equal(t, traceState, result.Tracestate)
}

// params describes one sampling decision to exercise.
type params struct {
	traceID       string
	ratio         *float64
	hasParent     bool
	parentSampled bool
	traceState    oteltrace.TraceState
	extraAttrs    []attribute.KeyValue
}

// sample runs MuunSampler over the given params.
func sample(t *testing.T, p params) sdktrace.SamplingResult {
	t.Helper()

	traceID, err := oteltrace.TraceIDFromHex(p.traceID)
	require.NoError(t, err)

	ctx := context.Background()
	if p.hasParent {
		var flags oteltrace.TraceFlags
		if p.parentSampled {
			flags = oteltrace.FlagsSampled
		}
		ctx = oteltrace.ContextWithSpanContext(ctx, oteltrace.NewSpanContext(
			oteltrace.SpanContextConfig{
				TraceID:    traceID,
				SpanID:     oteltrace.SpanID{1, 2, 3, 4, 5, 6, 7, 8},
				TraceFlags: flags,
				TraceState: p.traceState,
			},
		))
	}

	attrs := p.extraAttrs
	if p.ratio != nil {
		attrs = append(attrs, attribute.Float64(otel.SampleRatioAttr, *p.ratio))
	}

	return otel.MuunSampler{}.ShouldSample(sdktrace.SamplingParameters{
		ParentContext: ctx,
		TraceID:       traceID,
		Name:          "test_span",
		Attributes:    attrs,
	})
}
