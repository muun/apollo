package data

import (
	"context"
	"net/http"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/subsystems/marketplace/internal/data/provider_api"
	provider_api_model "github.com/muun/libwallet/subsystems/marketplace/internal/data/provider_api/model"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// The apps wait on a provider's Provider API while the user looks at a
// loading screen, so a provider that doesn't answer fails the call as
// ProviderErrorUnavailable after providerAPICallTimeout, retries included.
// Each attempt gets providerAPIAttemptTimeout, short enough to leave room
// for retrying a request lost to a flaky connection.
const (
	providerAPIAttemptTimeout = 5 * time.Second
	providerAPICallTimeout    = 15 * time.Second
)

// ProviderAPIRepository talks to each provider through its Provider API,
// classifying the provider's refusals and failures as *pmodel.ProviderError.
type ProviderAPIRepository interface {
	// ShippingMethods asks the given provider which shipping methods it offers
	// for the given card to the given country.
	ShippingMethods(
		ctx context.Context,
		providerUUID string,
		cardUUID string,
		country pmodel.CountryInfo,
	) ([]pmodel.ShippingMethod, error)
}

type httpProviderAPIRepository struct {
	service provider_api.ProviderAPIService
}

// NewProviderAPIRepository creates a repository that reaches each provider at
// the base URL configurator resolves for it.
func NewProviderAPIRepository(
	configurator provider_api.Configurator,
) ProviderAPIRepository {
	return &httpProviderAPIRepository{
		service: provider_api.NewProviderAPIService(
			configurator, &http.Client{Timeout: providerAPIAttemptTimeout},
		),
	}
}

func (r *httpProviderAPIRepository) ShippingMethods(
	ctx context.Context,
	providerUUID string,
	cardUUID string,
	country pmodel.CountryInfo,
) ([]pmodel.ShippingMethod, error) {
	ctx, cancel := context.WithTimeout(ctx, providerAPICallTimeout)
	defer cancel()

	// The catalog's card UUID is the provider's device model UUID.
	response, err := r.service.FetchShippingMethods(
		ctx, providerUUID, cardUUID, country.Code,
	)
	if err != nil {
		var withoutAPIErr *providerWithoutAPIError
		if errors.As(err, &withoutAPIErr) {
			return nil, err
		}

		var responseErr *provider_api.ResponseError
		if !errors.As(err, &responseErr) ||
			responseErr.StatusCode >= http.StatusInternalServerError {
			return nil, pmodel.NewProviderError(pmodel.ProviderErrorUnavailable, err)
		}

		switch responseErr.StatusCode {
		case http.StatusConflict:
			return nil, pmodel.NewProviderError(pmodel.ProviderErrorOutOfStock, err)
		case http.StatusGone:
			return nil, pmodel.NewProviderError(pmodel.ProviderErrorDeprecatedCard, err)
		case http.StatusUnprocessableEntity:
			return nil, pmodel.NewProviderError(pmodel.ProviderErrorUnreachableCountry, err)
		default:
			return nil, err
		}
	}

	switch response.ShippingMethod {
	case provider_api_model.ShippingMethodMail:
		return []pmodel.ShippingMethod{pmodel.ShippingMethodMail}, nil
	case provider_api_model.ShippingMethodCourier:
		return []pmodel.ShippingMethod{pmodel.ShippingMethodCourier}, nil
	case provider_api_model.ShippingMethodMailAndCourier:
		return []pmodel.ShippingMethod{
			pmodel.ShippingMethodMail,
			pmodel.ShippingMethodCourier,
		}, nil
	default:
		return nil, errors.Errorf(
			"provider %q answered unknown shipping method %q",
			providerUUID, response.ShippingMethod,
		)
	}
}
