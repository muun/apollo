package tracing

import (
	"log/slog"
	"math"
	"testing"

	"github.com/go-errors/errors"
	"github.com/stretchr/testify/require"
	"go.opentelemetry.io/otel/attribute"
)

func TestToAttribute(t *testing.T) {
	tests := []struct {
		name  string
		value any
		want  attribute.Value
	}{
		{"nil", nil, attribute.StringValue("<null>")},
		{"string", "hello", attribute.StringValue("hello")},
		{"bool", true, attribute.BoolValue(true)},
		{"int", -42, attribute.Int64Value(-42)},
		{"int8", int8(8), attribute.Int64Value(8)},
		{"int16", int16(16), attribute.Int64Value(16)},
		{"int32", int32(32), attribute.Int64Value(32)},
		{"int64", int64(64), attribute.Int64Value(64)},
		{"uint", uint(42), attribute.Int64Value(42)},
		{"uint8", uint8(8), attribute.Int64Value(8)},
		{"uint16", uint16(16), attribute.Int64Value(16)},
		{"uint32", uint32(32), attribute.Int64Value(32)},
		{"uint64", uint64(64), attribute.Int64Value(64)},
		{"uint64 at max int64", uint64(math.MaxInt64), attribute.Int64Value(math.MaxInt64)},
		{
			"uint64 overflow",
			uint64(math.MaxInt64) + 1,
			attribute.StringValue("9223372036854775808"),
		},
		{"uint64 max", uint64(math.MaxUint64), attribute.StringValue("18446744073709551615")},
		{"float32", float32(1.5), attribute.Float64Value(1.5)},
		{"float64", 2.5, attribute.Float64Value(2.5)},
		{"bytes", []byte{0xde, 0xad, 0xbe, 0xef}, attribute.StringValue(`\xdeadbeef`)},
		{"empty bytes", []byte{}, attribute.StringValue(`\x`)},
		{"stringer", testStringer{}, attribute.StringValue("stringer value")},
		{"error", errors.New("boom"), attribute.StringValue("boom")},
		{"stringer wins over error", testStringerError{}, attribute.StringValue("from stringer")},
		{"log valuer wins over stringer", testLogValuer{}, attribute.StringValue("from valuer")},
		{"fallback to %v", struct{ N int }{5}, attribute.StringValue("{5}")},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			attr := toAttribute("key", tt.value)
			require.Equal(t, attribute.Key("key"), attr.Key)
			require.Equal(t, tt.want, attr.Value)
		})
	}
}

func TestToAttributeRecoversFromPanickingLogValue(t *testing.T) {
	var nilValuer *testValueLogValuer

	var attr attribute.KeyValue
	require.NotPanics(t, func() { attr = toAttribute("key", nilValuer) })
	require.Contains(t, attr.Value.AsString(), "LogValue panicked")
}

func TestToAttributes(t *testing.T) {
	attrs := toAttributes([]any{
		"string", "value",
		"int", 42,
		attribute.Bool("keyvalue", true),
		"float", 1.5,
	})

	require.Equal(t, []attribute.KeyValue{
		attribute.String("string", "value"),
		attribute.Int64("int", 42),
		attribute.Bool("keyvalue", true),
		attribute.Float64("float", 1.5),
	}, attrs)
}

func TestToAttributes_Empty(t *testing.T) {
	require.Empty(t, toAttributes(nil))
	require.Empty(t, toAttributes([]any{}))
}

func TestToAttributes_KeyWithMissingValuePanics(t *testing.T) {
	requirePanic(t, "dangling", func() {
		toAttributes([]any{"key", "value", "dangling"})
	})
}

func TestToAttributes_ValueWithoutKeyPanics(t *testing.T) {
	requirePanic(t, "42", func() {
		toAttributes([]any{42, "value"})
	})
}

// requirePanic asserts fn panics with a preconditions.PreconditionError whose
// message contains the given substring.
func requirePanic(t *testing.T, contains string, fn func()) {
	t.Helper()

	defer func() {
		t.Helper()

		err, ok := recover().(error)
		require.True(t, ok, "expected a panic with an error")
		require.ErrorContains(t, err, contains)
	}()

	fn()
}

type testStringer struct{}

func (testStringer) String() string { return "stringer value" }

// testStringerError implements both fmt.Stringer and error: toAttribute prefers Stringer.
type testStringerError struct{}

func (testStringerError) String() string { return "from stringer" }

func (testStringerError) Error() string { return "from error" }

// testLogValuer implements both slog.LogValuer and fmt.Stringer: toAttribute prefers LogValuer.
type testLogValuer struct{}

func (testLogValuer) LogValue() slog.Value { return slog.StringValue("from valuer") }

func (testLogValuer) String() string { return "from stringer" }

// testValueLogValuer implements slog.LogValuer on a value receiver, which panics through a nil
// pointer.
type testValueLogValuer struct{ name string }

func (v testValueLogValuer) LogValue() slog.Value { return slog.StringValue(v.name) }
