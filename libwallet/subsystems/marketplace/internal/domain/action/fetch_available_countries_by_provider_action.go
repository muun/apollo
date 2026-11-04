package action

import (
	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	marketplace_model "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// FetchAvailableCountriesByProviderAction answers in which countries a
// given security cards provider operates.
type FetchAvailableCountriesByProviderAction interface {
	// Run returns the available countries the given provider ships to.
	Run(providerUUID string) ([]marketplace_model.CountryInfo, error)
}

type fetchAvailableCountriesByProviderAction struct {
	marketplaceRepository marketplace_data.MarketplaceRepository
}

func NewFetchAvailableCountriesByProviderAction(
	marketplaceRepository marketplace_data.MarketplaceRepository,
) FetchAvailableCountriesByProviderAction {
	return &fetchAvailableCountriesByProviderAction{
		marketplaceRepository: marketplaceRepository,
	}
}

func (ac *fetchAvailableCountriesByProviderAction) Run(
	providerUUID string,
) ([]marketplace_model.CountryInfo, error) {
	countries, err := ac.marketplaceRepository.AvailableCountries()
	if err != nil {
		return nil, err
	}

	catalog, err := ac.marketplaceRepository.Catalog()
	if err != nil {
		return nil, err
	}

	return catalog.CountriesServedBy(providerUUID, countries)
}
