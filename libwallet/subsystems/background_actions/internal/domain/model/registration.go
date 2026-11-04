package model

import (
	"math/rand/v2"
	"time"

	"github.com/muun/libwallet/platform/preconditions"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
)

// refreshRunTime is the longest minimum run time that fits an iOS refresh launch. A refresh
// launch gives about 25 s. 5 s of it covers the run's reserve and reaching the run, which leaves
// 20 s for the function.
const refreshRunTime = 20 * time.Second

// defaultTimeout is the timeout of a function registered without one.
const defaultTimeout = 20 * time.Second

// Registration is a function registered to run periodically.
type Registration struct {
	Function   ba_model.Function `json:"function"`
	Period     time.Duration     `json:"period"`
	DueAt      time.Time         `json:"dueAt"`
	Timeout    time.Duration     `json:"timeout"`
	MinRunTime time.Duration     `json:"minRunTime"`

	// Generation changes on every registration, so that a run never overwrites a newer one.
	Generation int `json:"generation"`

	// Attempts counts the early retries given to the function in its current period.
	Attempts int `json:"attempts"`

	// ClaimedAt is set while a run executes the function, and stays set if it is interrupted.
	ClaimedAt *time.Time `json:"claimedAt"`

	RegisteredAt time.Time `json:"registeredAt"`
}

// NewRegistration returns the registration of fn written at now, first due at now + period.
func NewRegistration(
	fn ba_model.Function,
	period time.Duration,
	cfg ba_model.RegisterConfig,
	now time.Time,
) *Registration {
	timeout := cfg.Timeout
	if timeout == 0 {
		timeout = defaultTimeout
	}

	minRunTime := cfg.MinRunTime

	preconditions.CheckPositivef(period, "non-positive period %v", period)
	preconditions.CheckPositivef(timeout, "non-positive timeout %v", timeout)
	preconditions.CheckNotNegativef(minRunTime, "negative minimum runtime %v", minRunTime)
	preconditions.CheckStatef(
		minRunTime <= timeout,
		"minimum runtime (%v) cannot exceed timeout (%v)", minRunTime, timeout,
	)

	return &Registration{
		Function:     fn,
		Period:       period,
		DueAt:        now.Add(period),
		Timeout:      timeout,
		MinRunTime:   minRunTime,
		Generation:   int(rand.Uint32()),
		Attempts:     0,
		ClaimedAt:    nil,
		RegisteredAt: now,
	}
}

// HasSameConfiguration returns whether other has the same period, timeout and minimum run time.
func (r *Registration) HasSameConfiguration(other *Registration) bool {
	return r.Period == other.Period &&
		r.MinRunTime == other.MinRunTime &&
		r.Timeout == other.Timeout
}

// FitsRefresh returns whether an iOS refresh launch can give the function its minimum run
// time.
func (r *Registration) FitsRefresh() bool {
	return r.MinRunTime <= refreshRunTime
}

// WakeDelay returns how long from now the function needs a wake, bounding it between 0 and the period.
func (r *Registration) WakeDelay(now time.Time) time.Duration {
	return max(min(r.Period, r.DueAt.Sub(now)), 0)
}

// Claim marks the function as being executed by a run.
func (r *Registration) Claim(now time.Time) {
	r.ClaimedAt = &now
}

// Reschedule moves the function to its next period.
func (r *Registration) Reschedule(now time.Time) {
	r.DueAt = now.Add(r.Period)
	r.Attempts = 0
	r.ClaimedAt = nil
}

// Retry makes the function due now, spending its one retry of the period.
func (r *Registration) Retry(now time.Time) {
	r.DueAt = now
	r.Attempts = 1
	r.ClaimedAt = nil
}
