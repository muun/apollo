// Package otel configures OpenTelemetry tracing for libwallet against Honeycomb.
// Configure a new OpenTelemetry Setup using a Config, and then include it in the context using
// the tracing package.
package otel

import (
	"context"
	"crypto/tls"
	"slices"
	"time"

	"github.com/go-errors/errors"
	"go.opentelemetry.io/contrib/processors/baggagecopy"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
	"go.opentelemetry.io/otel/trace"
	"go.opentelemetry.io/otel/trace/noop"
	"google.golang.org/grpc/credentials"

	"github.com/muun/libwallet/platform/observability/logging"
	"github.com/muun/libwallet/platform/tlsdistrust"
)

// Setup is a struct that encapsulates an OpenTelemetry setup.
// It can be installed into a context using tracing.WithOpenTelemetrySetup.
type Setup struct {
	ServiceName string

	TracerProvider interface {
		trace.TracerProvider

		ForceFlush(ctx context.Context) error
		Shutdown(ctx context.Context) error
	}

	Propagator propagation.TextMapPropagator
}

// NoopSetup provides a Setup that does nothing, to be used as a default.
func NoopSetup() *Setup {
	return newNoopSetup(defaultServiceName)
}

// NewSetup builds a Setup for the given config.
func NewSetup(ctx context.Context, cfg *Config) (*Setup, error) {
	if cfg.HoneycombAPIKey == "" && !cfg.LogSpans {
		logging.Info(ctx, "OpenTelemetry tracing disabled")
		return newNoopSetup(cfg.ServiceName), nil
	}

	otelResource, err := buildResource(ctx, cfg)
	if err != nil {
		return nil, errors.Errorf("otel: build resource: %w", err)
	}

	opts := []sdktrace.TracerProviderOption{
		sdktrace.WithResource(otelResource),
		sdktrace.WithSampler(MuunSampler{}),
		sdktrace.WithSpanProcessor(baggagecopy.NewSpanProcessor(baggagecopy.AllowAllMembers)),
	}

	if cfg.HoneycombAPIKey != "" {
		exporterCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
		defer cancel()

		exporter, err := newHoneycombExporter(exporterCtx, cfg)
		if err != nil {
			return nil, errors.Errorf("otel: create exporter: %w", err)
		}
		opts = append(opts, sdktrace.WithBatcher(exporter))
	}

	if cfg.LogSpans {
		opts = append(opts,
			sdktrace.WithSpanProcessor(newLoggingSpanProcessor()),
		)
	}

	logging.Info(ctx, "OpenTelemetry tracing enabled",
		"service_name", cfg.ServiceName,
		"honeycomb_export", cfg.HoneycombAPIKey != "",
		"log_spans", cfg.LogSpans,
		"honeycomb_dataset", cfg.HoneycombDataset,
		"honeycomb_endpoint", cfg.HoneycombEndpoint,
		"resource", otelResource.String(),
	)

	return &Setup{
		ServiceName:    cfg.ServiceName,
		TracerProvider: sdktrace.NewTracerProvider(opts...),
		Propagator:     propagation.TraceContext{},
	}, nil
}

// ForceFlush exports any spans the batch processor is still holding.
func (s *Setup) ForceFlush(ctx context.Context) error {
	return s.TracerProvider.ForceFlush(ctx)
}

// Shutdown flushes pending spans and tears down the TracerProvider.
func (s *Setup) Shutdown(ctx context.Context) error {
	return s.TracerProvider.Shutdown(ctx)
}

// newHoneycombExporter builds an OTLP gRPC Honeycomb exporter that points at Config.HoneycombEndpoint
// and authenticates using headers with Config.HoneycombAPIKey and Config.HoneycombDataset.
func newHoneycombExporter(ctx context.Context, cfg *Config) (*otlptrace.Exporter, error) {
	headers := map[string]string{"x-honeycomb-team": cfg.HoneycombAPIKey}
	if cfg.HoneycombDataset != "" {
		headers["x-honeycomb-dataset"] = cfg.HoneycombDataset
	}

	tlsConfig := tlsdistrust.Config()
	tlsConfig.MinVersion = tls.VersionTLS12

	opts := []otlptracegrpc.Option{
		otlptracegrpc.WithEndpoint(cfg.HoneycombEndpoint),
		otlptracegrpc.WithHeaders(headers),
		otlptracegrpc.WithTLSCredentials(credentials.NewTLS(tlsConfig)),
	}

	return otlptrace.New(ctx, otlptracegrpc.NewClient(opts...))
}

// buildResource builds the OTel Resource attached to every exported span.
// It defines all tags that will be added to all spans created. They are:
// - `service.name`: Config.ServiceName
// - Config.ExtraAttrs
func buildResource(ctx context.Context, cfg *Config) (*resource.Resource, error) {
	attrs := slices.Concat(
		[]attribute.KeyValue{semconv.ServiceName(cfg.ServiceName)},
		cfg.ExtraAttrs,
	)

	return resource.New(ctx, resource.WithAttributes(attrs...))
}

type noopTracerProvider struct {
	trace.TracerProvider
}

func (noopTracerProvider) ForceFlush(_ context.Context) error {
	return nil
}

func (noopTracerProvider) Shutdown(_ context.Context) error {
	return nil
}

func newNoopSetup(serviceName string) *Setup {
	return &Setup{
		ServiceName:    serviceName,
		TracerProvider: noopTracerProvider{noop.NewTracerProvider()},
		// A composite propagator of no propagators acts as a no-op propagator.
		Propagator: propagation.NewCompositeTextMapPropagator(),
	}
}
