package server

import (
	"encoding/binary"
	"fmt"
	"log/slog"
	"slices"
	"strings"
	"unicode/utf8"

	"github.com/go-errors/errors"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"

	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/server/api"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

// maxErrorDetailSize is the maximum size in bytes the ErrorDetail trailer can have.
var maxErrorDetailSize = int(0.7 * float64(maxTrailersTotalSize()))

// maxErrorStackFrameDepth is the maximum amount of frames per error stack trace.
const maxErrorStackFrameDepth = 30

// errorWithContext is an error that carries key-values for the native layer to report along
// with it. The ErrorDetail of a gRPC error takes them from the first such error in its cause.
type errorWithContext interface {
	error
	ErrorContext() map[string]string
}

// Check a failed sign carries the tapped card's firmware into its ErrorDetail context
var _ errorWithContext = (*sc_model.TappedCardError)(nil)

func newGrpcErrorFromCode(code errorCode) error {
	return newGrpcErrorFromCodeAndErr(code, nil)
}

func newGrpcErrorFromCodeAndErr(code errorCode, errCause error) error {
	errorMessage := code.Message

	developerMessage := ""
	if errCause != nil {
		developerMessage = errCause.Error()
	}

	var statusCode codes.Code

	switch code.Type {
	case api.ErrorType_CLIENT:
		statusCode = codes.InvalidArgument
	case api.ErrorType_LIBWALLET:
		statusCode = codes.Internal
	default:
		slog.Error("Error type handling not defined", slog.Any("errorCode", code))
		return newGrpcError(errCause)
	}

	errorStatus := status.New(statusCode, errorMessage)
	detail := api.ErrorDetail_builder{
		Type:             code.Type,
		Code:             int64(code.Code),
		Message:          errorMessage,
		DeveloperMessage: developerMessage,
		StackTrace:       buildErrorStackTrace(errCause),
		Context:          buildErrorContext(errCause),
	}.Build()
	capErrorDetailSize(detail, maxErrorDetailSize)
	errWithDetails, err := errorStatus.WithDetails(detail)
	if err != nil {
		return errorStatus.Err()
	}
	return errWithDetails.Err()
}

func newGrpcError(errCause error) error {
	detail := newErrorDetail(errCause)
	capErrorDetailSize(detail, maxErrorDetailSize)
	errorStatus := status.New(codes.Internal, detail.GetMessage())
	errorWithDetails, err := errorStatus.WithDetails(detail)
	if err != nil {
		return errorStatus.Err()
	}
	return errorWithDetails.Err()
}

func newErrorDetail(errCause error) *api.ErrorDetail {
	var houstonError *houston.HoustonResponseError

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
			StackTrace:       buildErrorStackTrace(errCause),
			Context:          buildErrorContext(errCause),
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
			Code:             int64(errorCodes.ErrUnknown.Code),
			Message:          errorCodes.ErrUnknown.Message,
			DeveloperMessage: developerMessage,
			StackTrace:       buildErrorStackTrace(errCause),
			Context:          buildErrorContext(errCause),
		}.Build()
	}
}

// buildErrorContext returns the key-values of the first error in err's tree that carries them,
// or nil when none does.
func buildErrorContext(err error) map[string]string {
	var withContext errorWithContext
	if errors.As(err, &withContext) {
		return withContext.ErrorContext()
	}
	return nil
}

// buildErrorStackTrace walks the error tree and produces the corresponding ErrorStackTrace:
// - Wrapped errors are stored as the "cause" of the error.
// - Only errors with stack trace are retained. Other errors only matter for their message.
// - Joined errors are added as "siblings" with no common cause.
// - The root node's message is the full error string.
func buildErrorStackTrace(err error) *api.ErrorStackTrace {
	if err == nil {
		return nil
	}

	rootMessage := err.Error()

	// Represents a node in the chain of errors.
	type errorNode struct {
		message string
		frames  []*api.StackFrame
	}
	var errorNodes []errorNode

	// Subtree hanging at the bottom of the chain. Produced when the chain ends in a joined error.
	var bottom *api.ErrorStackTrace

	for err != nil {
		if multiErr, ok := err.(interface{ Unwrap() []error }); ok {
			// A joined error branches the tree and ends the linear chain.
			children := multiErr.Unwrap()

			siblings := make([]*api.ErrorStackTrace, 0, len(children))
			for _, child := range children {
				// Recursively build the joined error chains.
				// Joined errors are rare, so this recursion should stay bounded.
				if childStackTrace := buildErrorStackTrace(child); childStackTrace != nil {
					siblings = append(siblings, childStackTrace)
				}
			}

			switch len(siblings) {
			case 0:
				bottom = nil
			case 1:
				// A joined error with only one element is just another node in the chain.
				bottom = siblings[0]
			default:
				bottom = api.ErrorStackTrace_builder{
					Message:  err.Error(),
					Siblings: siblings,
				}.Build()
			}

			// Multi errors have no source.
			err = nil
		} else {
			// Only keep errors that carry a stack trace.
			if frames := buildErrorStackFrames(err); len(frames) > 0 {
				errorNodes = append(errorNodes, errorNode{message: err.Error(), frames: frames})
			}

			err = errors.Unwrap(err)
		}
	}

	// Build the ErrorStackTraces bottom-up.
	result := bottom
	for _, node := range slices.Backward(errorNodes) {
		result = api.ErrorStackTrace_builder{
			Message: node.message,
			Frames:  node.frames,
			Cause:   result,
		}.Build()
	}

	if result != nil {
		// Make sure the root node has the full original error message.
		result.SetMessage(rootMessage)
	}

	return result
}

// buildErrorStackFrames builds a stack of api.StackFrame from the frames returned by go-errors.
func buildErrorStackFrames(err error) []*api.StackFrame {
	errWithStack, ok := err.(*errors.Error) //nolint:errorlint // We are "re-implementing" errors.As
	if !ok {
		return nil
	}

	frames := errWithStack.StackFrames()
	if len(frames) > maxErrorStackFrameDepth {
		frames = frames[:maxErrorStackFrameDepth]
	}

	result := make([]*api.StackFrame, 0, len(frames))
	for _, frame := range frames {
		if frame.Name == "" {
			continue
		}

		result = append(result, api.StackFrame_builder{
			File:     frame.File,
			Line:     int32(frame.LineNumber),
			Function: formatStackFrameFunction(frame.Name),
			Package:  frame.Package,
		}.Build())
	}

	return result
}

// formatStackFrameFunction normalizes Go runtime function names.
func formatStackFrameFunction(name string) string {
	segments := strings.Split(name, ".")
	out := segments[:0]

	for _, seg := range segments {
		if isAllDigits(seg) {
			if len(out) > 0 && out[len(out)-1] == "init" {
				// init functions have the index the go compiler processed it appended.
				// This is more an artifact of the go compiler rather than useful information.
				// We drop it.
				//
				// Example: "init.0" -> "init"
				continue
			}

			// Closures inside closures drop the `func` prefix. They are useful to signal closures.
			// We add the `func` back.
			//
			// Example: "run.func1.2" -> "run.func1.func2"
			seg = "func" + seg
		}

		out = append(out, seg)
	}

	return strings.Join(out, ".")
}

func isAllDigits(s string) bool {
	if s == "" {
		return false
	}

	for _, r := range s {
		if r < '0' || r > '9' {
			return false
		}
	}

	return true
}

// capErrorDetailSize degrades the detail until its serialized size fits in maxBytes.
func capErrorDetailSize(detail *api.ErrorDetail, maxBytes int) {
	if proto.Size(detail) <= maxBytes {
		return
	}

	// We try again after truncating every non-terminal error node's frames.
	newFramesCap := 5
	truncateStackTraceFrames(detail.GetStackTrace(), newFramesCap)

	size := proto.Size(detail)
	if size <= maxBytes {
		slog.Warn("ErrorDetail too large, truncated non-terminal error nodes' frames",
			"total_size", size,
			"stack_trace_size", proto.Size(detail.GetStackTrace()),
			"truncated_frames_cap", newFramesCap,
		)
		return
	}

	slog.Warn("ErrorDetail too large, dropping stack trace",
		"total_size", size,
		"stack_trace_size", proto.Size(detail.GetStackTrace()),
	)

	detail.ClearStackTrace()

	if proto.Size(detail) <= maxBytes {
		return
	}

	// The developer message can shrink only to nothing, so a context that leaves the detail over
	// budget even without it would cost us the error itself. The context goes first: it only adds
	// to what the error says.
	developerMessage := detail.GetDeveloperMessage()
	detail.SetDeveloperMessage("")
	contextBlowsBudget := proto.Size(detail) > maxBytes
	detail.SetDeveloperMessage(developerMessage)

	if contextBlowsBudget {
		slog.Warn("ErrorDetail too large, dropping context",
			"total_size", proto.Size(detail),
			"context_entries", len(detail.GetContext()),
		)

		detail.SetContext(nil)

		if proto.Size(detail) <= maxBytes {
			return
		}
	}

	// The developer message alone blows the budget. An oversized trailer is rejected whole by
	// the client, so an untouched message here costs us the error itself.
	capDeveloperMessage(detail, maxBytes)
}

// truncateStackTraceFrames drops frames beyond maxFrames on every non-innermost node of the error tree.
func truncateStackTraceFrames(node *api.ErrorStackTrace, maxFrames int) {
	for node != nil {
		if !node.HasCause() && len(node.GetSiblings()) == 0 {
			// This is an innermost error.
			// We avoid truncating it, and finish.
			break
		}

		if frames := node.GetFrames(); len(frames) > maxFrames {
			node.SetFrames(frames[:maxFrames])
		}

		// Joined errors are rare, so this recursion should stay bounded.
		for _, sibling := range node.GetSiblings() {
			truncateStackTraceFrames(sibling, maxFrames)
		}

		node = node.GetCause()
	}
}

// capDeveloperMessage truncates the detail's developer message so the whole detail fits in maxBytes.
func capDeveloperMessage(detail *api.ErrorDetail, maxBytes int) {
	// what protobuf adds around the developer message: its field tag plus the varint holding its length.
	const developerMessageFieldOverhead = 1 + binary.MaxVarintLen32

	message := detail.GetDeveloperMessage()

	detail.SetDeveloperMessage("")
	budget := maxBytes - proto.Size(detail) - developerMessageFieldOverhead

	// We truncate the middle of the message because the ends are where a Go error chain carries the failing operation
	// and its root cause.
	truncationMarker := fmt.Sprintf("[... truncated from %d bytes ...]", len(message))
	newDeveloperMessage := truncateMiddle(message, truncationMarker, budget)
	detail.SetDeveloperMessage(newDeveloperMessage)

	slog.Warn("ErrorDetail too large, truncated developer message",
		"total_size", proto.Size(detail),
		"developer_message_size", len(message),
		"truncated_developer_message_size", len(detail.GetDeveloperMessage()),
		"original_message", message,
	)
}

// truncateMiddle shortens message to at most maxBytes by dropping its middle and marking the gap.
func truncateMiddle(message string, truncationMarker string, maxBytes int) string {
	if len(message) <= maxBytes {
		return message
	}

	if maxBytes <= len(truncationMarker) {
		return ""
	}

	// Cuts land on rune boundaries.
	headEnd := (maxBytes - len(truncationMarker)) / 2
	for headEnd > 0 && !utf8.RuneStart(message[headEnd]) {
		headEnd--
	}

	tailStart := len(message) - (maxBytes - len(truncationMarker) - headEnd)
	for tailStart < len(message) && !utf8.RuneStart(message[tailStart]) {
		tailStart++
	}

	return message[:headEnd] + truncationMarker + message[tailStart:]
}
