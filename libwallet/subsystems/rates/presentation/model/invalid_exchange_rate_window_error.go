package model

import "fmt"

// InvalidExchangeRateWindowError reports a window to save with no rates or a non-positive id or
// fetch date.
type InvalidExchangeRateWindowError struct {
	cause error
}

// NewInvalidExchangeRateWindowError creates an InvalidExchangeRateWindowError for the given
// validation failure.
func NewInvalidExchangeRateWindowError(cause error) *InvalidExchangeRateWindowError {
	return &InvalidExchangeRateWindowError{cause: cause}
}

func (e InvalidExchangeRateWindowError) Error() string {
	return fmt.Sprintf("invalid exchange rate window: %v", e.cause)
}

func (e InvalidExchangeRateWindowError) Unwrap() error {
	return e.cause
}
