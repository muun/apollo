package data

import (
	"github.com/go-errors/errors"
)

// providerWithoutAPIError means Muun doesn't know how to reach a provider's
// Provider API on this network. It isn't the provider's failure, so it's not
// classified as a ProviderError.
type providerWithoutAPIError struct{ error }

func newProviderWithoutAPIError(providerUUID string) error {
	return &providerWithoutAPIError{
		errors.Errorf("provider %q has no Provider API on this network", providerUUID),
	}
}
