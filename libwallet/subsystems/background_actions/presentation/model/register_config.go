package model

import "time"

type RegisterConfig struct {
	// Timeout is the maximum amount of time this function is allowed to execute.
	// Default 20 seconds.
	Timeout time.Duration

	// MinRunTime is the least time a run must be able to give the function to execute.
	// Default 0.
	MinRunTime time.Duration
}
