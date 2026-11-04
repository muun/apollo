package action

import (
	"context"

	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	marketplace_model "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// FetchShippingMethodsAction asks a card's provider which shipping methods it
// offers to a given country.
type FetchShippingMethodsAction interface {
	// Run returns the shipping methods the given card's provider offers to the
	// given country.
	Run(
		ctx context.Context,
		country marketplace_model.CountryInfo,
		securityCardUUID string,
	) ([]marketplace_model.ShippingMethod, error)
}

type fetchShippingMethodsAction struct {
	marketplaceRepository marketplace_data.MarketplaceRepository
	providerAPIRepository marketplace_data.ProviderAPIRepository
}

func NewFetchShippingMethodsAction(
	marketplaceRepository marketplace_data.MarketplaceRepository,
	providerAPIRepository marketplace_data.ProviderAPIRepository,
) FetchShippingMethodsAction {
	return &fetchShippingMethodsAction{
		marketplaceRepository: marketplaceRepository,
		providerAPIRepository: providerAPIRepository,
	}
}

func (ac *fetchShippingMethodsAction) Run(
	ctx context.Context,
	country marketplace_model.CountryInfo,
	securityCardUUID string,
) ([]marketplace_model.ShippingMethod, error) {
	catalog, err := ac.marketplaceRepository.Catalog()
	if err != nil {
		return nil, err
	}

	seller, err := catalog.SellerOf(securityCardUUID)
	if err != nil {
		return nil, err
	}

	return ac.providerAPIRepository.ShippingMethods(
		ctx, seller.UUID, securityCardUUID, country,
	)
}
