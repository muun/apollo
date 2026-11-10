package action

import (
	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	marketplace_model "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// FetchAvailableCountriesAction answers in which countries the security
// cards marketplace is available.
type FetchAvailableCountriesAction interface {
	// Run returns the countries where security cards can be shipped.
	Run() ([]marketplace_model.CountryInfo, error)
}

type fetchAvailableCountriesAction struct {
	marketplaceRepository marketplace_data.MarketplaceRepository
}

func NewFetchAvailableCountriesAction(
	marketplaceRepository marketplace_data.MarketplaceRepository,
) FetchAvailableCountriesAction {
	return &fetchAvailableCountriesAction{
		marketplaceRepository: marketplaceRepository,
	}
}

func (ac *fetchAvailableCountriesAction) Run() (
	[]marketplace_model.CountryInfo,
	error,
) {
	return ac.marketplaceRepository.AvailableCountries()
}
