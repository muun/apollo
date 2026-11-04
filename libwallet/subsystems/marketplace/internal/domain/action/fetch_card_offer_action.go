package action

import (
	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	marketplace_model "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// FetchCardOfferAction answers a provider's offer of a specific card for a
// given country.
type FetchCardOfferAction interface {
	// Run returns the offer for the given card in the given country.
	Run(
		country marketplace_model.CountryInfo,
		securityCardUUID string,
	) (*marketplace_model.CardOffer, error)
}

type fetchCardOfferAction struct {
	marketplaceRepository marketplace_data.MarketplaceRepository
}

func NewFetchCardOfferAction(
	marketplaceRepository marketplace_data.MarketplaceRepository,
) FetchCardOfferAction {
	return &fetchCardOfferAction{
		marketplaceRepository: marketplaceRepository,
	}
}

func (ac *fetchCardOfferAction) Run(
	country marketplace_model.CountryInfo,
	securityCardUUID string,
) (*marketplace_model.CardOffer, error) {
	catalog, err := ac.marketplaceRepository.Catalog()
	if err != nil {
		return nil, err
	}

	return catalog.OfferFor(
		country, securityCardUUID, hardcodedRates,
		hardcodedPrimaryCurrency,
	)
}
