package test

import (
	"context"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/muun/libwallet/platform/observability/otel"
	"github.com/muun/libwallet/platform/observability/tracing"
	"github.com/muun/libwallet/server"
	"github.com/muun/libwallet/server/api"
)

func TestTracingInterceptors(t *testing.T) {
	suite.Run(t, new(tracingInterceptorsSuite))
}

// tracingInterceptorsSuite runs requests through the tracing interceptors, which force flush
// RunDueBackgroundActions and PerformDiagnosticScanForUtxos. Spans not flushed wait an hour in
// the batcher.
type tracingInterceptorsSuite struct {
	suite.Suite
	*require.Assertions

	exporter  *tracetest.InMemoryExporter
	otelSetup *otel.Setup
}

func (s *tracingInterceptorsSuite) SetupTest() {
	s.Assertions = require.New(s.T())

	s.exporter = tracetest.NewInMemoryExporter()
	s.otelSetup = s.newOtelSetup(s.exporter)
}

func (s *tracingInterceptorsSuite) TestFlushedMethodExportsItsTraceBeforeResponding() {
	ctx, cancel := context.WithTimeout(s.T().Context(), 10*time.Second)
	defer cancel()

	err := s.callUnary(ctx, api.WalletService_RunDueBackgroundActions_FullMethodName,
		func(ctx context.Context) error {
			_, span := tracing.NewSpan(ctx, "platform.pull_notifications")
			span.Finish()
			return nil
		},
	)
	s.NoError(err)

	s.ElementsMatch(
		[]string{"libwallet RunDueBackgroundActions", "platform.pull_notifications"},
		s.exportedSpanNames(),
	)
}

func (s *tracingInterceptorsSuite) TestOtherMethodsLeaveTheirTraceInTheBatcher() {
	ctx := s.T().Context()
	err := s.callUnary(ctx, api.WalletService_ResetData_FullMethodName,
		func(context.Context) error {
			return nil
		},
	)
	s.NoError(err)
	s.Empty(s.exportedSpanNames())

	s.NoError(s.otelSetup.ForceFlush(ctx))
	s.Equal([]string{"libwallet ResetData"}, s.exportedSpanNames())
}

func (s *tracingInterceptorsSuite) TestFlushedMethodExportsItsTraceWhenTheCallerCancels() {
	ctx, cancel := context.WithCancel(s.T().Context())
	err := s.callUnary(ctx, api.WalletService_RunDueBackgroundActions_FullMethodName,
		func(context.Context) error {
			cancel()
			return status.Error(codes.Canceled, "canceled")
		},
	)
	s.Equal(codes.Canceled, status.Code(err))

	s.Equal([]string{"libwallet RunDueBackgroundActions"}, s.exportedSpanNames())
}

func (s *tracingInterceptorsSuite) TestHangingFlushStillRespondsBeforeTheCallerDeadline() {
	exporter := &hangingExporter{}
	s.otelSetup = s.newOtelSetup(exporter)

	deadline := time.Now().Add(500 * time.Millisecond)
	ctx, cancel := context.WithDeadline(s.T().Context(), deadline)
	defer cancel()

	err := s.callUnary(ctx, api.WalletService_RunDueBackgroundActions_FullMethodName,
		func(context.Context) error {
			return nil
		},
	)
	s.NoError(err)

	s.True(exporter.called.Load(), "the flush never reached the exporter")
	s.Less(time.Now(), deadline, "the response missed the caller's deadline")
}

func (s *tracingInterceptorsSuite) TestFlushedStreamMethodExportsItsTraceBeforeResponding() {
	ctx := s.T().Context()
	err := s.callStream(ctx, api.WalletService_PerformDiagnosticScanForUtxos_FullMethodName)
	s.NoError(err)

	s.Equal([]string{"libwallet PerformDiagnosticScanForUtxos"}, s.exportedSpanNames())
}

// newOtelSetup returns an otel.Setup that exports to exporter through a batcher.
func (s *tracingInterceptorsSuite) newOtelSetup(exporter sdktrace.SpanExporter) *otel.Setup {
	tracerProvider := sdktrace.NewTracerProvider(
		sdktrace.WithSampler(otel.MuunSampler{}),
		sdktrace.WithBatcher(exporter, sdktrace.WithBatchTimeout(time.Hour)),
	)
	s.T().Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		_ = tracerProvider.Shutdown(ctx)
	})

	return &otel.Setup{ServiceName: "libwallet", TracerProvider: tracerProvider}
}

// callUnary runs handler as the unary method through the tracing interceptor.
func (s *tracingInterceptorsSuite) callUnary(
	ctx context.Context,
	method string,
	handler func(ctx context.Context) error,
) error {
	interceptor := server.TracingUnaryInterceptor(
		s.otelSetup,
		nil,
		api.WalletService_RunDueBackgroundActions_FullMethodName,
	)

	info := &grpc.UnaryServerInfo{FullMethod: method}
	_, err := interceptor(ctx, nil, info, func(ctx context.Context, _ any) (any, error) {
		return nil, handler(ctx)
	})
	return err
}

// callStream runs the stream method through the tracing interceptor, with a handler that ends
// right away.
func (s *tracingInterceptorsSuite) callStream(ctx context.Context, method string) error {
	interceptor := server.TracingStreamInterceptor(
		s.otelSetup,
		nil,
		api.WalletService_PerformDiagnosticScanForUtxos_FullMethodName,
	)

	info := &grpc.StreamServerInfo{FullMethod: method}
	handler := func(any, grpc.ServerStream) error {
		return nil
	}
	return interceptor(nil, contextServerStream{ctx: ctx}, info, handler)
}

func (s *tracingInterceptorsSuite) exportedSpanNames() []string {
	var names []string
	for _, span := range s.exporter.GetSpans() {
		names = append(names, span.Name)
	}
	return names
}

// hangingExporter is a span exporter whose exports only end with their context.
type hangingExporter struct {
	called atomic.Bool
}

func (e *hangingExporter) ExportSpans(ctx context.Context, _ []sdktrace.ReadOnlySpan) error {
	e.called.Store(true)
	<-ctx.Done()
	return ctx.Err()
}

func (e *hangingExporter) Shutdown(context.Context) error {
	return nil
}

// contextServerStream is a server stream that only has a context.
type contextServerStream struct {
	grpc.ServerStream

	ctx context.Context
}

func (s contextServerStream) Context() context.Context {
	return s.ctx
}
