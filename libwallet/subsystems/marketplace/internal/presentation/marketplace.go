package presentation

import (
	"context"

	"github.com/muun/libwallet/subsystems/marketplace/internal/domain/action"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

type Marketplace struct {
	fetchAvailableCountries           action.FetchAvailableCountriesAction
	fetchAvailableCountriesByProvider action.FetchAvailableCountriesByProviderAction
	fetchCardOffer                    action.FetchCardOfferAction
	fetchProviderListingsByCountry    action.FetchProviderListingsByCountryAction
	fetchShippingMethods              action.FetchShippingMethodsAction
	refreshMarketplaceCards           action.RefreshMarketplaceCardsAction
	refreshMarketplacePrices          action.RefreshMarketplacePricesAction
	saveControlPlane                  action.SaveControlPlaneAction
}

func NewMarketplace(
	fetchAvailableCountries action.FetchAvailableCountriesAction,
	fetchAvailableCountriesByProvider action.FetchAvailableCountriesByProviderAction,
	fetchCardOffer action.FetchCardOfferAction,
	fetchProviderListingsByCountry action.FetchProviderListingsByCountryAction,
	fetchShippingMethods action.FetchShippingMethodsAction,
	refreshMarketplaceCards action.RefreshMarketplaceCardsAction,
	refreshMarketplacePrices action.RefreshMarketplacePricesAction,
	saveControlPlane action.SaveControlPlaneAction,
) Marketplace {
	return Marketplace{
		fetchAvailableCountries:           fetchAvailableCountries,
		fetchAvailableCountriesByProvider: fetchAvailableCountriesByProvider,
		fetchCardOffer:                    fetchCardOffer,
		fetchProviderListingsByCountry:    fetchProviderListingsByCountry,
		fetchShippingMethods:              fetchShippingMethods,
		refreshMarketplaceCards:           refreshMarketplaceCards,
		refreshMarketplacePrices:          refreshMarketplacePrices,
		saveControlPlane:                  saveControlPlane,
	}
}

func (m *Marketplace) FetchAvailableCountries() ([]model.CountryInfo, error) {
	return m.fetchAvailableCountries.Run()
}

func (m *Marketplace) FetchAvailableCountriesByProvider(
	providerUUID string,
) ([]model.CountryInfo, error) {
	return m.fetchAvailableCountriesByProvider.Run(providerUUID)
}

func (m *Marketplace) FetchCardOffer(
	country model.CountryInfo,
	securityCardUUID string,
) (*model.CardOffer, error) {
	return m.fetchCardOffer.Run(country, securityCardUUID)
}

func (m *Marketplace) FetchProviderListingsByCountry(
	country model.CountryInfo,
) ([]model.ProviderListing, error) {
	return m.fetchProviderListingsByCountry.Run(country)
}

func (m *Marketplace) FetchShippingMethods(
	ctx context.Context,
	country model.CountryInfo,
	securityCardUUID string,
) ([]model.ShippingMethod, error) {
	return m.fetchShippingMethods.Run(ctx, country, securityCardUUID)
}

func (m *Marketplace) RefreshCards() error {
	return m.refreshMarketplaceCards.Run()
}

func (m *Marketplace) RefreshPrices() error {
	return m.refreshMarketplacePrices.Run()
}

func (m *Marketplace) SaveControlPlane(controlPlane model.ControlPlane) error {
	return m.saveControlPlane.Run(controlPlane)
}
