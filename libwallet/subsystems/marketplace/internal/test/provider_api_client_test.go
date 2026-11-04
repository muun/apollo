package test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/go-errors/errors"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	"github.com/muun/libwallet/subsystems/marketplace/internal/data/provider_api"
	provider_api_model "github.com/muun/libwallet/subsystems/marketplace/internal/data/provider_api/model"
)

// The provider is a third party the integration harness doesn't run, so its
// answers are simulated with an httptest server.

const (
	testProviderUUID    = "4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14"
	testDeviceModelUUID = "a1b2c3d4-1111-4000-8000-000000000001"
	testCountryCode     = "AR"
)

func TestFetchShippingMethods(t *testing.T) {
	provider := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			assert.Equal(t, http.MethodGet, r.Method)
			assert.Equal(t, "/api/v1/shipping_methods", r.URL.Path)
			assert.Equal(t, testDeviceModelUUID, r.URL.Query().Get("device_model_uuid"))
			assert.Equal(t, testCountryCode, r.URL.Query().Get("country_code"))
			assert.Empty(t, r.Header.Get("Authorization"))

			_, _ = w.Write([]byte(`{"shipping_method": "mail_and_courier"}`))
		},
	))
	defer provider.Close()

	service := newTestProviderAPIService(provider.URL + "/api/v1/")

	shippingMethods, err := service.FetchShippingMethods(
		t.Context(), testProviderUUID, testDeviceModelUUID, testCountryCode,
	)

	require.NoError(t, err)
	assert.Equal(t, provider_api_model.ShippingMethodMailAndCourier, shippingMethods.ShippingMethod)
}

func TestFetchShippingMethodsReportsErrorStatus(t *testing.T) {
	var requests atomic.Int32
	provider := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, _ *http.Request) {
			requests.Add(1)
			w.WriteHeader(http.StatusConflict)
			_, _ = w.Write([]byte(`{"error": "Product out of stock"}`))
		},
	))
	defer provider.Close()

	service := newTestProviderAPIService(provider.URL)

	_, err := service.FetchShippingMethods(
		t.Context(), testProviderUUID, testDeviceModelUUID, testCountryCode,
	)

	var responseErr *provider_api.ResponseError
	require.True(t, errors.As(err, &responseErr), "expected a ResponseError, got %v", err)
	assert.Equal(t, http.StatusConflict, responseErr.StatusCode)
	assert.Equal(t, "Product out of stock", responseErr.Message)
	assert.EqualValues(t, 1, requests.Load(), "client errors must not be retried")
}

func TestFetchShippingMethodsRetriesServerErrors(t *testing.T) {
	var requests atomic.Int32
	provider := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, _ *http.Request) {
			if requests.Add(1) < 3 {
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			_, _ = w.Write([]byte(`{"shipping_method": "mail"}`))
		},
	))
	defer provider.Close()

	service := newTestProviderAPIService(provider.URL)

	shippingMethods, err := service.FetchShippingMethods(
		t.Context(), testProviderUUID, testDeviceModelUUID, testCountryCode,
	)

	require.NoError(t, err)
	assert.Equal(t, provider_api_model.ShippingMethodMail, shippingMethods.ShippingMethod)
	assert.EqualValues(t, 3, requests.Load())
}

func TestFetchShippingMethodsGivesUpAfterRepeatedServerErrors(t *testing.T) {
	var requests atomic.Int32
	provider := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, _ *http.Request) {
			requests.Add(1)
			w.WriteHeader(http.StatusServiceUnavailable)
		},
	))
	defer provider.Close()

	service := newTestProviderAPIService(provider.URL)

	_, err := service.FetchShippingMethods(
		t.Context(), testProviderUUID, testDeviceModelUUID, testCountryCode,
	)

	var responseErr *provider_api.ResponseError
	require.True(t, errors.As(err, &responseErr), "expected a ResponseError, got %v", err)
	assert.Equal(t, http.StatusServiceUnavailable, responseErr.StatusCode)
	assert.Equal(t, http.StatusText(http.StatusServiceUnavailable), responseErr.Message)
	assert.EqualValues(t, 3, requests.Load())
}

func TestFetchShippingMethodsRetriesTimeouts(t *testing.T) {
	var requests atomic.Int32
	provider := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			if requests.Add(1) == 1 {
				// Hold the first request until the client gives up on it.
				<-r.Context().Done()
				return
			}
			_, _ = w.Write([]byte(`{"shipping_method": "courier"}`))
		},
	))
	defer provider.Close()

	service := provider_api.NewProviderAPIService(
		newTestProviderAPIConfigurator(provider.URL),
		&http.Client{Timeout: 50 * time.Millisecond},
	)

	shippingMethods, err := service.FetchShippingMethods(
		t.Context(), testProviderUUID, testDeviceModelUUID, testCountryCode,
	)

	require.NoError(t, err)
	assert.Equal(t, provider_api_model.ShippingMethodCourier, shippingMethods.ShippingMethod)
	assert.EqualValues(t, 2, requests.Load())
}

func TestFetchShippingMethodsRetriesTimeoutsReadingTheResponse(t *testing.T) {
	var requests atomic.Int32
	provider := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			if requests.Add(1) == 1 {
				// Send the headers, then hold the body until the client gives
				// up on it.
				w.WriteHeader(http.StatusOK)
				_ = http.NewResponseController(w).Flush()
				<-r.Context().Done()
				return
			}
			_, _ = w.Write([]byte(`{"shipping_method": "courier"}`))
		},
	))
	defer provider.Close()

	service := provider_api.NewProviderAPIService(
		newTestProviderAPIConfigurator(provider.URL),
		&http.Client{Timeout: 50 * time.Millisecond},
	)

	shippingMethods, err := service.FetchShippingMethods(
		t.Context(), testProviderUUID, testDeviceModelUUID, testCountryCode,
	)

	require.NoError(t, err)
	assert.Equal(t, provider_api_model.ShippingMethodCourier, shippingMethods.ShippingMethod)
	assert.EqualValues(t, 2, requests.Load())
}

func TestFetchShippingMethodsStopsWhenContextIsDone(t *testing.T) {
	var requests atomic.Int32
	provider := httptest.NewServer(http.HandlerFunc(
		func(_ http.ResponseWriter, r *http.Request) {
			requests.Add(1)
			// Hold every request until the client gives up on it.
			<-r.Context().Done()
		},
	))
	defer provider.Close()

	service := newTestProviderAPIService(provider.URL)
	ctx, cancel := context.WithTimeout(t.Context(), 50*time.Millisecond)
	defer cancel()

	_, err := service.FetchShippingMethods(
		ctx, testProviderUUID, testDeviceModelUUID, testCountryCode,
	)

	require.ErrorIs(t, err, context.DeadlineExceeded)
	assert.EqualValues(t, 1, requests.Load(), "no request may reach the provider once ctx is done")
}

func newTestProviderAPIService(baseURL string) provider_api.ProviderAPIService {
	return provider_api.NewProviderAPIService(
		newTestProviderAPIConfigurator(baseURL), &http.Client{Timeout: 5 * time.Second},
	)
}

// newTestProviderAPIConfigurator reaches the test provider at baseURL.
func newTestProviderAPIConfigurator(baseURL string) provider_api.Configurator {
	return marketplace_data.NewProviderAPIConfigurator(map[string]string{
		testProviderUUID: baseURL,
	})
}
