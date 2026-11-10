package otel

import (
	"crypto/sha256"
	"encoding/binary"
	"math"

	"go.opentelemetry.io/otel/attribute"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
)

// SampleRatioAttr is the span attribute key the sampler reads to get the sample ratio to use.
const SampleRatioAttr = "sample_ratio"

// MuunSampler is a trace.Sampler that reads SampleRatioAttr from the span's start attributes
// and uses it to make a deterministic per-span sampling decision.
//
// Sample rate is given as a probability between 0 and 1. 0 never samples and 1 always does.
// Sampling is deterministic based on traceId. This is quite powerful and enables cross-service
// tracing with sampling that doesn't have incomplete spans. That is, if service A has a sample rate
// of 0.2 and B one of 0.3, all spans originating in A that call into B will also generate a span
// in B.
//
// Spans without the attribute respect their parent's sampling decision.
// If both the span and its parent aren't sampled, we drop it.
//
// This is the Go equivalent of the Java Sampler class.
type MuunSampler struct{}

// ShouldSample implements trace.Sampler.
func (s MuunSampler) ShouldSample(params sdktrace.SamplingParameters) sdktrace.SamplingResult {
	parentCtx := trace.SpanContextFromContext(params.ParentContext)

	ratio, ratioFound := s.findSampleRatio(params.Attributes)

	var decision sdktrace.SamplingDecision
	if ratioFound {
		// If we decided a sample ratio before creating the span, honor that decision.
		if shouldSampleWithRatio(params.TraceID, ratio) {
			decision = sdktrace.RecordAndSample
		} else {
			decision = sdktrace.RecordOnly
		}
	} else if parentCtx.IsValid() && parentCtx.IsSampled() {
		// If the parent is sampled, sample away!
		decision = sdktrace.RecordAndSample
	} else {
		// If we got here then it's either:
		// 1. Child span and the parent is not sampled
		// 2. A top level span without a sampling decision taken ahead of time.
		// Both we drop.
		decision = sdktrace.Drop
	}

	return sdktrace.SamplingResult{
		Decision:   decision,
		Tracestate: parentCtx.TraceState(),
	}
}

// findSampleRatio finds the ratio stored in the SampleRatioAttr attribute, if any.
func (s MuunSampler) findSampleRatio(attributes []attribute.KeyValue) (float64, bool) {
	for _, attr := range attributes {
		if string(attr.Key) == SampleRatioAttr {
			return attr.Value.AsFloat64(), true
		}
	}
	return 0, false
}

// shouldSampleWithRatio decides to sample or not given a TraceID and a ratio.
// Maintains compatibility with Java's Sampler#shouldSampleBasedOnRatio
func shouldSampleWithRatio(traceID trace.TraceID, ratio float64) bool {
	if ratio <= 0 {
		return false
	}
	if ratio >= 1 {
		return true
	}

	hash := sha256.Sum256([]byte(traceID.String()))
	first8Bytes := int64(binary.BigEndian.Uint64(hash[:8]))

	upperBound := int64(ratio * float64(math.MaxInt64))

	abs := first8Bytes
	if abs < 0 {
		abs = -abs
	}

	return abs < upperBound
}

// Description implements trace.Sampler.
func (s MuunSampler) Description() string {
	return "muun sampler"
}
