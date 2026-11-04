package test

import (
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"github.com/go-errors/errors"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/data/houston/simulator"
	"github.com/muun/libwallet/platform/test/teststorage"
	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	"github.com/muun/libwallet/subsystems/marketplace/internal/domain/action"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// The catalog comes from the simulated Houston filtered through the permissive
// control plane, where the Constellations provider sells the Scorpius card.
// The provider is simulated with an httptest server, since the integration
// harness doesn't run one.
const (
	constellationsProviderUUID = "4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14"
	scorpiusCardUUID           = "a1b2c3d4-1111-4000-8000-000000000001"
)

var argentina = pmodel.NewCountryInfo("AR")

func TestFetchShippingMethodsActionAsksTheCardsProvider(t *testing.T) {
	provider := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			assert.Equal(t, scorpiusCardUUID, r.URL.Query().Get("device_model_uuid"))
			assert.Equal(t, argentina.Code, r.URL.Query().Get("country_code"))

			_, _ = w.Write([]byte(`{"shipping_method": "mail_and_courier"}`))
		},
	))
	defer provider.Close()

	fetchShippingMethods := newTestFetchShippingMethodsAction(t, map[string]string{
		constellationsProviderUUID: provider.URL,
	})

	shippingMethods, err := fetchShippingMethods.Run(t.Context(), argentina, scorpiusCardUUID)

	require.NoError(t, err)
	assert.Equal(t, []pmodel.ShippingMethod{
		pmodel.ShippingMethodMail,
		pmodel.ShippingMethodCourier,
	}, shippingMethods)
}

func TestFetchShippingMethodsActionClassifiesProviderErrors(t *testing.T) {
	testCases := []struct {
		status int
		kind   pmodel.ProviderErrorKind
	}{
		{http.StatusConflict, pmodel.ProviderErrorOutOfStock},
		{http.StatusGone, pmodel.ProviderErrorDeprecatedCard},
		{http.StatusUnprocessableEntity, pmodel.ProviderErrorUnreachableCountry},
		{http.StatusServiceUnavailable, pmodel.ProviderErrorUnavailable},
	}

	for _, testCase := range testCases {
		t.Run(http.StatusText(testCase.status), func(t *testing.T) {
			provider := httptest.NewServer(http.HandlerFunc(
				func(w http.ResponseWriter, _ *http.Request) {
					w.WriteHeader(testCase.status)
				},
			))
			defer provider.Close()

			fetchShippingMethods := newTestFetchShippingMethodsAction(t, map[string]string{
				constellationsProviderUUID: provider.URL,
			})

			_, err := fetchShippingMethods.Run(t.Context(), argentina, scorpiusCardUUID)

			var providerErr *pmodel.ProviderError
			require.True(t, errors.As(err, &providerErr), "expected a ProviderError, got %v", err)
			assert.Equal(t, testCase.kind, providerErr.Kind)
		})
	}
}

func TestFetchShippingMethodsActionDoesNotClassifyBadRequests(t *testing.T) {
	provider := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusBadRequest)
		},
	))
	defer provider.Close()

	fetchShippingMethods := newTestFetchShippingMethodsAction(t, map[string]string{
		constellationsProviderUUID: provider.URL,
	})

	_, err := fetchShippingMethods.Run(t.Context(), argentina, scorpiusCardUUID)

	require.Error(t, err)
	var providerErr *pmodel.ProviderError
	assert.False(t, errors.As(err, &providerErr), "a bad request is not the provider's refusal")
}

func TestFetchShippingMethodsActionReportsUnreachableProvidersAsUnavailable(t *testing.T) {
	provider := httptest.NewServer(http.NotFoundHandler())
	providerURL := provider.URL
	provider.Close()

	fetchShippingMethods := newTestFetchShippingMethodsAction(t, map[string]string{
		constellationsProviderUUID: providerURL,
	})

	_, err := fetchShippingMethods.Run(t.Context(), argentina, scorpiusCardUUID)

	var providerErr *pmodel.ProviderError
	require.True(t, errors.As(err, &providerErr), "expected a ProviderError, got %v", err)
	assert.Equal(t, pmodel.ProviderErrorUnavailable, providerErr.Kind)
}

func TestFetchShippingMethodsActionFailsForUnknownCards(t *testing.T) {
	var requests atomic.Int32
	provider := httptest.NewServer(http.HandlerFunc(
		func(_ http.ResponseWriter, _ *http.Request) {
			requests.Add(1)
		},
	))
	defer provider.Close()

	fetchShippingMethods := newTestFetchShippingMethodsAction(t, map[string]string{
		constellationsProviderUUID: provider.URL,
	})

	_, err := fetchShippingMethods.Run(t.Context(), argentina, "unknown-card-uuid")

	require.Error(t, err)
	assert.Zero(t, requests.Load())
}

func TestFetchShippingMethodsActionFailsForProvidersWithoutAPI(t *testing.T) {
	fetchShippingMethods := newTestFetchShippingMethodsAction(t, map[string]string{})

	_, err := fetchShippingMethods.Run(t.Context(), argentina, scorpiusCardUUID)

	require.Error(t, err)
	var providerErr *pmodel.ProviderError
	assert.False(
		t, errors.As(err, &providerErr), "a provider without API is not the provider's failure",
	)
}

func newTestFetchShippingMethodsAction(
	t *testing.T,
	providerAPIBaseURLs map[string]string,
) action.FetchShippingMethodsAction {
	t.Helper()

	repository := marketplace_data.NewMarketplaceRepository(
		simulator.NewSimulatedHoustonService(nil, nil),
		teststorage.NewTestKeyValueStorage(t),
	)
	require.NoError(t, action.NewSaveControlPlaneAction(repository).Run(
		PermissiveControlPlane(),
	))

	return action.NewFetchShippingMethodsAction(
		repository,
		marketplace_data.NewProviderAPIRepository(
			marketplace_data.NewProviderAPIConfigurator(providerAPIBaseURLs),
		),
	)
}
