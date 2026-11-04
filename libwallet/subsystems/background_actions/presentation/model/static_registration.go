package model

import (
	"time"

	"github.com/muun/libwallet/platform/preconditions"
)

// StaticRegistration is a function the Background Actions subsystem keeps registered on its own,
// as if it was registered with RegisterPeriodic. Every run writes it while a wallet exists, unless
// the same one is stored.
type StaticRegistration struct {
	Function Function
	Period   time.Duration
	Config   RegisterConfig
}

// NewStaticRegistration returns the static registration of fn every period, with cfg if given.
func NewStaticRegistration(
	fn Function,
	period time.Duration,
	cfg ...RegisterConfig,
) StaticRegistration {
	var config RegisterConfig
	switch len(cfg) {
	case 0:
		config = RegisterConfig{}
	case 1:
		config = cfg[0]
	default:
		preconditions.Failf("more than one (%v) configurations provided", len(cfg))
	}

	return StaticRegistration{
		Function: fn,
		Period:   period,
		Config:   config,
	}
}
