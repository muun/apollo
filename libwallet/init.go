package libwallet

import (
	"log/slog"
	"runtime/debug"
)

// BackendActivatedFeatureStatusProvider is an interface implemented by the
// apps to provide us with information about the state of some backend side
// feature flags until we can implement a libwallet-side solution for this.
type BackendActivatedFeatureStatusProvider interface {
	IsBackendFlagEnabled(flag string) bool
}

// Config defines the global libwallet configuration.
type Config struct {
	DataDir               string
	FeatureStatusProvider BackendActivatedFeatureStatusProvider
	AppLogSink            AppLogSink
}

var Cfg *Config

// Init configures the libwallet
func Init(c *Config) {
	debug.SetTraceback("crash")
	Cfg = c

	if Cfg.AppLogSink != nil {
		logger := slog.New(NewBridgeLogHandler(Cfg.AppLogSink, slog.LevelWarn))
		slog.SetDefault(logger)
	}
}
