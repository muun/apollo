package presentation

import (
	"context"
	"fmt"
	"log/slog"
	"path"
	"runtime/debug"
	"time"

	"github.com/go-errors/errors"
	grpc_recovery "github.com/grpc-ecosystem/go-grpc-middleware/recovery"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// RecoverUnknownErrorUnaryInterceptor converts UNKNOWN gRPC errors into INTERNAL gRPC errors
// to ensure consistency when errors are not properly constructed in the presentation layer.
func RecoverUnknownErrorUnaryInterceptor() grpc.UnaryServerInterceptor {
	return func(
		ctx context.Context,
		req any,
		info *grpc.UnaryServerInfo, //nolint:revive // TODO: use or remove info
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

// RecoverPanicUnaryInterceptor catches panic errors during RPC execution
// and converts them into INTERNAL gRPC errors.
func RecoverPanicUnaryInterceptor() grpc.UnaryServerInterceptor {
	return grpc_recovery.UnaryServerInterceptor(
		grpc_recovery.WithRecoveryHandler(panicRecoveryHandler),
	)
}

// LoggingUnaryInterceptor logs each incoming gRPC method, its duration and error status.
func LoggingUnaryInterceptor() grpc.UnaryServerInterceptor {
	return func(
		ctx context.Context,
		req any,
		info *grpc.UnaryServerInfo,
		handler grpc.UnaryHandler,
	) (resp any, err error) {
		startTime := time.Now()
		resp, err = handler(ctx, req)
		duration := time.Since(startTime)
		logCall(path.Base(info.FullMethod), duration, err)
		return resp, err
	}
}

// RecoverUnknownErrorStreamInterceptor converts UNKNOWN gRPC errors into INTERNAL gRPC errors
// for streaming RPCs, to ensure consistency when errors are not properly constructed in the
// presentation layer.
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
			return NewGrpcError(err)
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

// LoggingStreamInterceptor logs each streaming gRPC method, its duration and error status.
func LoggingStreamInterceptor() grpc.StreamServerInterceptor {
	return func(
		srv any,
		ss grpc.ServerStream,
		info *grpc.StreamServerInfo,
		handler grpc.StreamHandler,
	) error {
		startTime := time.Now()
		err := handler(srv, ss)
		duration := time.Since(startTime)
		logCall(path.Base(info.FullMethod), duration, err)
		return err
	}
}

func panicRecoveryHandler(p any) error {
	stack := debug.Stack()
	slog.Error(
		"recovery from panic",
		slog.Any("panic", fmt.Sprintf("%v", p)),
		slog.String("stack", string(stack)),
	)
	return NewGrpcError(errors.Errorf("panic: %v", p))
}

func logCall(method string, duration time.Duration, err error) {
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
}
