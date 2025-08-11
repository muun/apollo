package presentation

import (
	"errors"
	apierrors "github.com/muun/libwallet/errors"
	"github.com/muun/libwallet/presentation/api"
	"github.com/muun/libwallet/service"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"log/slog"
)

func NewGrpcErrorFromCode(errorCode apierrors.ErrorCode) error {
	return NewGrpcErrorFromCodeAndErr(errorCode, nil)
}

func NewGrpcErrorFromCodeAndErr(errorCode apierrors.ErrorCode, errCause error) error {
	errorMessage := errorCode.Message

	developerMessage := ""
	if errCause != nil {
		developerMessage = errCause.Error()
	}

	var statusCode codes.Code
	var errorType api.ErrorType

	switch errorCode.Type {
	case apierrors.CLIENT:
		statusCode = codes.InvalidArgument
		errorType = api.ErrorType_CLIENT
	case apierrors.LIBWALLET:
		statusCode = codes.Internal
		errorType = api.ErrorType_LIBWALLET
	default:
		slog.Error("Error type handling not defined", slog.Any("errorCode", errorCode))
		return NewGrpcError(errCause)
	}

	errorStatus := status.New(statusCode, errorMessage)
	detail := api.ErrorDetail_builder{
		Type:             errorType,
		Code:             int64(errorCode.Code),
		Message:          errorMessage,
		DeveloperMessage: developerMessage,
	}.Build()
	errWithDetails, err := errorStatus.WithDetails(detail)
	if err != nil {
		return errorStatus.Err()
	}
	return errWithDetails.Err()
}

func NewGrpcError(errCause error) error {
	detail := newErrorDetail(errCause)
	errorStatus := status.New(codes.Internal, detail.GetMessage())
	errorWithDetails, err := errorStatus.WithDetails(detail)
	if err != nil {
		return errorStatus.Err()
	}
	return errorWithDetails.Err()
}

func newErrorDetail(errCause error) *api.ErrorDetail {
	var houstonError *service.HoustonResponseError

	switch {
	case errors.As(errCause, &houstonError):
		// Certain Houston responses are essential for driving UI flows, so we need to ensure that
		// these error codes reach the native layer unaltered
		slog.Error("houston error", slog.Any("error", errCause))
		return api.ErrorDetail_builder{
			Type:             api.ErrorType_HOUSTON,
			Code:             int64(houstonError.ErrorCode),
			Message:          houstonError.Message,
			DeveloperMessage: houstonError.DeveloperMessage,
		}.Build()
	default:
		slog.Error("internal libwallet error", slog.Any("error", errCause))
		var developerMessage string
		if errCause != nil {
			developerMessage = errCause.Error()
		} else {
			// No one should call newErrorDetail(errCause) with a nil errCause,
			// but we log an error in case someone does
			slog.Error("calling newErrorDetail(errCause) with errCause=<nil>")
			developerMessage = "errCause=<nil>"
		}
		return api.ErrorDetail_builder{
			Type:             api.ErrorType_LIBWALLET,
			Code:             int64(apierrors.ErrorCodes.ErrUnknown.Code),
			Message:          apierrors.ErrorCodes.ErrUnknown.Message,
			DeveloperMessage: developerMessage,
		}.Build()
	}
}
