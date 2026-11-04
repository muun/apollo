package model

import (
	"github.com/muun/libwallet/platform/preconditions"
	"github.com/muun/libwallet/server/api"
)

// ExecutionContext is the type of wake that triggered the execution.
type ExecutionContext string

const (
	AndroidBackground       ExecutionContext = "ANDROID_BACKGROUND"
	AndroidForeground       ExecutionContext = "ANDROID_FOREGROUND"
	IOSBackgroundRefresh    ExecutionContext = "IOS_BACKGROUND_REFRESH"
	IOSBackgroundProcessing ExecutionContext = "IOS_BACKGROUND_PROCESSING"
	IOSForeground           ExecutionContext = "IOS_FOREGROUND"
)

func FromProtoExecutionContext(proto api.ExecutionContext) ExecutionContext {
	switch proto {
	case api.ExecutionContext_ANDROID_BACKGROUND:
		return AndroidBackground
	case api.ExecutionContext_ANDROID_FOREGROUND:
		return AndroidForeground
	case api.ExecutionContext_IOS_BACKGROUND_REFRESH:
		return IOSBackgroundRefresh
	case api.ExecutionContext_IOS_BACKGROUND_PROCESSING:
		return IOSBackgroundProcessing
	case api.ExecutionContext_IOS_FOREGROUND:
		return IOSForeground
	default:
		preconditions.MissingCase(proto)
		return ""
	}
}
