package server

import (
	"context"
	"fmt"
	"log/slog"
	"runtime/debug"

	"github.com/go-errors/errors"
	grpc_middleware "github.com/grpc-ecosystem/go-grpc-middleware"
	grpc_recovery "github.com/grpc-ecosystem/go-grpc-middleware/recovery"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/platform/observability/otel"
)

// RecoverUnknownErrorUnaryInterceptor converts UNKNOWN gRPC errors into INTERNAL gRPC errors
// to ensure consistency when errors are not properly constructed in the server layer.
func RecoverUnknownErrorUnaryInterceptor() grpc.UnaryServerInterceptor {
	return func(
		ctx context.Context,
		req any,
		_ *grpc.UnaryServerInfo,
		handler grpc.UnaryHandler,
	) (any, error) {

		resp, err := handler(ctx, req)
		st, ok := status.FromError(err)
		if !ok || st.Code() == codes.Unknown {

			return nil, newGrpcError(err)
		}
		return resp, err
	}
}

// RecoverPanicUnaryInterceptor catches panic errors during RPC execution
// and converts them into INTERNAL gRPC errors.
func RecoverPanicUnaryInterceptor() grpc.UnaryServerInterceptor {
	return grpc_recovery.UnaryServerInterceptor(
		grpc_recovery.WithRecoveryHandler(panicRecoveryHandler),
	)
}

// TracingUnaryInterceptor registers tracingInterceptorHandler for unary endpoints.
// All forceFlushMethods, given as full method names, export the request's spans before responding.
func TracingUnaryInterceptor(
	otelSetup *otel.Setup,
	session app_provided_data.SessionProvider,
	forceFlushMethods ...string,
) grpc.UnaryServerInterceptor {
	return func(
		ctx context.Context,
		req any,
		info *grpc.UnaryServerInfo,
		handler grpc.UnaryHandler,
	) (any, error) {
		var resp any
		err := tracingInterceptorHandler(
			ctx,
			otelSetup,
			session,
			forceFlushMethods,
			info.FullMethod,
			func(ctx context.Context) error {
				var err error
				resp, err = handler(ctx, req)
				return err
			},
		)

		return resp, err
	}
}

// RecoverUnknownErrorStreamInterceptor converts UNKNOWN gRPC errors into INTERNAL gRPC errors
// for streaming RPCs, to ensure consistency when errors are not properly constructed in the
// server layer.
func RecoverUnknownErrorStreamInterceptor() grpc.StreamServerInterceptor {
	return func(
		srv any,
		ss grpc.ServerStream,
		_ *grpc.StreamServerInfo,
		handler grpc.StreamHandler,
	) error {
		err := handler(srv, ss)
		st, ok := status.FromError(err)
		if !ok || st.Code() == codes.Unknown {
			return newGrpcError(err)
		}
		return err
	}
}

// RecoverPanicStreamInterceptor catches panics during streaming RPC execution
// and converts them into INTERNAL gRPC errors.
func RecoverPanicStreamInterceptor() grpc.StreamServerInterceptor {
	return grpc_recovery.StreamServerInterceptor(
		grpc_recovery.WithRecoveryHandler(panicRecoveryHandler),
	)
}

// TracingStreamInterceptor registers tracingInterceptorHandler for stream endpoints.
func TracingStreamInterceptor(
	otelSetup *otel.Setup,
	session app_provided_data.SessionProvider,
	forceFlushMethods ...string,
) grpc.StreamServerInterceptor {
	return func(
		srv any,
		ss grpc.ServerStream,
		info *grpc.StreamServerInfo,
		handler grpc.StreamHandler,
	) error {
		ctx := ss.Context()

		return tracingInterceptorHandler(
			ctx,
			otelSetup,
			session,
			forceFlushMethods,
			info.FullMethod,
			func(ctx context.Context) error {
				return handler(srv, &grpc_middleware.WrappedServerStream{
					ServerStream:   ss,
					WrappedContext: ctx,
				})
			},
		)
	}
}

func panicRecoveryHandler(p any) error {
	slog.Error(
		"recovery from panic",
		slog.Any("panic", fmt.Sprintf("%v", p)),
		slog.String("stack", string(debug.Stack())),
	)

	// Capture the stacktrace of the panic into an error (if not done already), and mark error as a panic.
	return newGrpcError(errors.WrapPrefix(p, "panic", 0))
}
