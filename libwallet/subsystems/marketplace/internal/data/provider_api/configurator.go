package provider_api

// Configurator resolves how to reach each provider's Provider API.
type Configurator interface {
	// BaseURL returns the base URL of the given provider's Provider API.
	BaseURL(providerUUID string) (string, error)
}
