package action

import (
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

// mapHoustonCardError returns the action-level error for a security card failure reported
// by Houston, or nil when the failure is not one the presentation layer discriminates on —
// the caller wraps those itself, naming the step that failed.
func mapHoustonCardError(err error) error {
	var houstonError *houston.HoustonResponseError
	if !errors.As(err, &houstonError) {
		return nil
	}

	switch houstonError.ErrorCode {
	case houston.ErrInvalidMac, houston.ErrInvalidSignature:
		return &sc_model.InvalidMacError{
			Message: "mac verification failed",
			Cause:   houstonError,
		}
	case houston.ErrChallengeExpired:
		return &sc_model.ChallengeExpiredError{
			Message: "challenge has expired",
			Cause:   houstonError,
		}
	}
	return nil
}
