package action

import (
	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	marketplace_model "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// FetchProviderListingsByCountryAction answers which providers ship to a
// given country and what they offer there.
type FetchProviderListingsByCountryAction interface {
	// Run returns each provider's listing for the given country.
	Run(
		country marketplace_model.CountryInfo,
	) ([]marketplace_model.ProviderListing, error)
}

type fetchProviderListingsByCountryAction struct {
	marketplaceRepository marketplace_data.MarketplaceRepository
}

func NewFetchProviderListingsByCountryAction(
	marketplaceRepository marketplace_data.MarketplaceRepository,
) FetchProviderListingsByCountryAction {
	return &fetchProviderListingsByCountryAction{
		marketplaceRepository: marketplaceRepository,
	}
}

func (ac *fetchProviderListingsByCountryAction) Run(
	country marketplace_model.CountryInfo,
) ([]marketplace_model.ProviderListing, error) {
	catalog, err := ac.marketplaceRepository.Catalog()
	if err != nil {
		return nil, err
	}

	return catalog.ListingsFor(
		country, hardcodedRates, hardcodedPrimaryCurrency,
	)
}
