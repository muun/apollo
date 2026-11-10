package presentation

import (
	"context"

	"github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

type Marketplace interface {
	FetchAvailableCountries() ([]model.CountryInfo, error)
	FetchAvailableCountriesByProvider(
		providerUUID string,
	) ([]model.CountryInfo, error)
	FetchCardOffer(
		country model.CountryInfo,
		securityCardUUID string,
	) (*model.CardOffer, error)
	FetchProviderListingsByCountry(
		country model.CountryInfo,
	) ([]model.ProviderListing, error)
	// FetchShippingMethods asks the given card's provider which shipping
	// methods it offers to the given country. A refusal or failure of the
	// provider is a *model.ProviderError.
	FetchShippingMethods(
		ctx context.Context,
		country model.CountryInfo,
		securityCardUUID string,
	) ([]model.ShippingMethod, error)
	// RefreshCards force-refreshes the cached marketplace cards,
	// bypassing the cache TTL.
	RefreshCards() error
	// RefreshPrices force-refreshes the cached marketplace prices,
	// bypassing the cache TTL.
	RefreshPrices() error
	// SaveControlPlane persists the control plane the app pushed, replacing
	// the stored one. Until the first push the marketplace fails closed.
	SaveControlPlane(controlPlane model.ControlPlane) error
}
