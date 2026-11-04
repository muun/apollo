package otel_test

import (
	"testing"

	"github.com/stretchr/testify/require"
	"go.opentelemetry.io/otel/attribute"

	"github.com/muun/libwallet/platform/observability/otel"
)

func TestNewConfig_Defaults(t *testing.T) {
	cfg := otel.NewConfig()

	require.Equal(t, "libwallet", cfg.ServiceName)
	require.Equal(t, "api.honeycomb.io:443", cfg.HoneycombEndpoint)
	require.Empty(t, cfg.HoneycombAPIKey)
	require.Empty(t, cfg.HoneycombDataset)
	require.False(t, cfg.LogSpans)
	require.Empty(t, cfg.ExtraAttrs)
}

func TestConfig_WithMethodsModifyInPlace(t *testing.T) {
	cfg := otel.NewConfig()

	returned := cfg.
		WithHoneycombAPIKey("key").
		WithHoneycombDataset("dataset").
		WithLogSpans(true)

	require.Same(t, cfg, returned)
	require.Equal(t, "key", cfg.HoneycombAPIKey)
	require.Equal(t, "dataset", cfg.HoneycombDataset)
	require.True(t, cfg.LogSpans)
}

func TestConfig_WithExtraAttrsSkipsEmptyValues(t *testing.T) {
	cfg := otel.NewConfig().WithExtraAttrs(map[string]string{
		"set":   "value",
		"empty": "",
	})

	require.Equal(t, []attribute.KeyValue{attribute.String("set", "value")}, cfg.ExtraAttrs)
}

func TestConfig_WithExtraAttrsAppends(t *testing.T) {
	cfg := otel.NewConfig().
		WithExtraAttrs(map[string]string{"first": "1"}).
		WithExtraAttrs(map[string]string{"second": "2"})

	require.ElementsMatch(t, []attribute.KeyValue{
		attribute.String("first", "1"),
		attribute.String("second", "2"),
	}, cfg.ExtraAttrs)
}

func TestNewConfigFromMap(t *testing.T) {
	cfg := otel.NewConfigFromMap(map[string]string{
		"HONEYCOMB_KEY": "key",
		"ENV":           "dataset",
		"CI":            "true",
		"HOSTNAME":      "pod-1",
		"GIT_BRANCH":    "",
	})

	require.Equal(t, "libwallet", cfg.ServiceName)
	require.Equal(t, "key", cfg.HoneycombAPIKey)
	require.Equal(t, "dataset", cfg.HoneycombDataset)
	require.Equal(t, "api.honeycomb.io:443", cfg.HoneycombEndpoint)
	require.False(t, cfg.LogSpans)
	require.Equal(t, []attribute.KeyValue{attribute.String("pod", "pod-1")}, cfg.ExtraAttrs)
}

func TestNewConfigFromMap_EndpointOverride(t *testing.T) {
	cfg := otel.NewConfigFromMap(map[string]string{"HONEYCOMB_ENDPOINT": "localhost:4317"})

	require.Equal(t, "localhost:4317", cfg.HoneycombEndpoint)
	require.True(t, cfg.LogSpans)
}
