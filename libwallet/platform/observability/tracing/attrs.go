package tracing

import (
	"encoding/hex"
	"fmt"
	"log/slog"
	"math"
	"strconv"

	"go.opentelemetry.io/otel/attribute"

	"github.com/muun/libwallet/platform/preconditions"
)

// toAttribute converts a key/value into an OTel attribute.KeyValue.
func toAttribute(key string, value any) attribute.KeyValue {
	switch v := value.(type) {
	case nil:
		return attribute.String(key, "<null>")
	case string:
		return attribute.String(key, v)
	case bool:
		return attribute.Bool(key, v)
	case int:
		return attribute.Int64(key, int64(v))
	case int8:
		return attribute.Int64(key, int64(v))
	case int16:
		return attribute.Int64(key, int64(v))
	case int32:
		return attribute.Int64(key, int64(v))
	case int64:
		return attribute.Int64(key, v)
	case uint:
		if uint64(v) > math.MaxInt64 {
			return attribute.String(key, strconv.FormatUint(uint64(v), 10))
		}
		return attribute.Int64(key, int64(v))
	case uint8:
		return attribute.Int64(key, int64(v))
	case uint16:
		return attribute.Int64(key, int64(v))
	case uint32:
		return attribute.Int64(key, int64(v))
	case uint64:
		if v > math.MaxInt64 {
			return attribute.String(key, strconv.FormatUint(v, 10))
		}
		return attribute.Int64(key, int64(v))
	case float32:
		return attribute.Float64(key, float64(v))
	case float64:
		return attribute.Float64(key, v)
	case []byte:
		return attribute.String(key, "\\x"+hex.EncodeToString(v))
	// MUST come before fmt.Stringer. Secret-bearing types redact through LogValue while their
	// String may still return the secret; checking String first exports it in span attributes.
	case slog.LogValuer:
		return toAttribute(key, slog.AnyValue(v).Resolve().Any())
	case fmt.Stringer:
		return attribute.String(key, v.String())
	case error:
		return attribute.String(key, v.Error())
	default:
		return attribute.String(key, fmt.Sprintf("%v", v))
	}
}

// toAttributes converts a variadic key/value list to OTel attributes.
func toAttributes(args []any) []attribute.KeyValue {
	attrs := make([]attribute.KeyValue, 0, len(args)/2+1)

	for len(args) > 0 {
		switch item := args[0].(type) {
		case string:
			preconditions.CheckStatef(len(args) > 1, "tracing: key with missing value: %s", item)
			attrs = append(attrs, toAttribute(item, args[1]))
			args = args[2:]

		case attribute.KeyValue:
			attrs = append(attrs, item)
			args = args[1:]

		default:
			preconditions.Failf("tracing: invalid value without key: %v", item)
		}
	}

	return attrs
}
