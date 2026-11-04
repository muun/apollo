package data

import (
	"github.com/muun/libwallet/subsystems/marketplace/internal/data/provider_api"
)

// providerAPIConfigurator reaches each provider at its base URL in
// baseURLsByProviderUUID.
type providerAPIConfigurator struct {
	baseURLsByProviderUUID map[string]string
}

// NewProviderAPIConfigurator creates a configurator that reaches each provider
// at its base URL in baseURLsByProviderUUID.
func NewProviderAPIConfigurator(
	baseURLsByProviderUUID map[string]string,
) provider_api.Configurator {
	return &providerAPIConfigurator{
		baseURLsByProviderUUID: baseURLsByProviderUUID,
	}
}

func (c *providerAPIConfigurator) BaseURL(providerUUID string) (string, error) {
	baseURL, ok := c.baseURLsByProviderUUID[providerUUID]
	if !ok {
		return "", newProviderWithoutAPIError(providerUUID)
	}
	return baseURL, nil
}
