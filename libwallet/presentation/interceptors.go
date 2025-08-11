package presentation

import (
	"context"
	"fmt"
	"github.com/grpc-ecosystem/go-grpc-middleware/recovery"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"log/slog"
	"path"
	"runtime/debug"
	"time"
)

// RecoverUnknownErrorInterceptor converts UNKNOWN gRPC errors into INTERNAL gRPC errors
// to ensure consistency when errors are not properly constructed in the presentation layer.
func RecoverUnknownErrorInterceptor() grpc.UnaryServerInterceptor {
	return func(
		ctx context.Context,
		req any,
		info *grpc.UnaryServerInfo,
		handler grpc.UnaryHandler,
	) (resp any, err error) {

		resp, err = handler(ctx, req)
		st, ok := status.FromError(err)
		if !ok || st.Code() == codes.Unknown {

			return nil, NewGrpcError(err)
		}
		return resp, err
	}
}

// RecoverPanicInterceptor catches panic errors during RPC execution
// and converts them into INTERNAL gRPC errors.
func RecoverPanicInterceptor() grpc.UnaryServerInterceptor {
	return grpc_recovery.UnaryServerInterceptor(
		grpc_recovery.WithRecoveryHandler(func(p any) error {

			stack := debug.Stack()
			slog.Error(
				"recovery from panic",
				slog.Any("panic", fmt.Sprintf("%v", p)),
				slog.String("stack", string(stack)),
			)
			return NewGrpcError(fmt.Errorf("panic: %v", p))
		}),
	)
}

// LoggingInterceptor logs each incoming gRPC method, its duration and error status.
func LoggingInterceptor() grpc.UnaryServerInterceptor {
	return func(
		ctx context.Context,
		req any,
		info *grpc.UnaryServerInfo,
		handler grpc.UnaryHandler,
	) (resp any, err error) {
		startTime := time.Now()
		resp, err = handler(ctx, req)
		duration := time.Since(startTime)
		method := path.Base(info.FullMethod)
		statusCode := status.Code(err)
		attrs := []any{
			slog.String("method", method),
			slog.Int("duration_ms", int(duration.Milliseconds())),
			slog.String("status_code", statusCode.String()),
		}

		if err != nil {
			attrs = append(attrs, slog.Any("error", err))
			slog.Error("gRPC call failed", attrs...)
		} else {
			slog.Debug("gRPC call succeeded", attrs...)
		}

		return resp, err
	}
}
