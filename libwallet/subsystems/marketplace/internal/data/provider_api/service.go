package provider_api

import (
	"context"
	"net/http"

	"github.com/muun/libwallet/subsystems/marketplace/internal/data/provider_api/model"
)

// ProviderAPIService calls the providers' Provider APIs, one method per
// endpoint.
type ProviderAPIService interface {
	// FetchShippingMethods asks the given provider which shipping methods it
	// offers for the given device model to the given country
	// (GET /shipping_methods).
	FetchShippingMethods(
		ctx context.Context,
		providerUUID string,
		deviceModelUUID string,
		countryCode string,
	) (*model.ShippingMethodsJSON, error)
}

type providerAPIClient struct {
	client client
}

var _ ProviderAPIService = (*providerAPIClient)(nil)

// NewProviderAPIService creates a service that reaches each provider at the
// base URL configurator resolves for it, sending its requests through
// httpClient.
func NewProviderAPIService(
	configurator Configurator,
	httpClient *http.Client,
) ProviderAPIService {
	return &providerAPIClient{
		client: client{
			configurator: configurator,
			httpClient:   httpClient,
		},
	}
}

func (p *providerAPIClient) FetchShippingMethods(
	ctx context.Context,
	providerUUID string,
	deviceModelUUID string,
	countryCode string,
) (*model.ShippingMethodsJSON, error) {
	r := request[model.ShippingMethodsJSON]{
		Method: http.MethodGet,
		Path:   "/shipping_methods",
		Params: map[string]string{
			"device_model_uuid": deviceModelUUID,
			"country_code":      countryCode,
		},
	}
	return r.do(ctx, &p.client, providerUUID)
}
