package action

import (
	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
)

// RefreshMarketplacePricesAction force-refreshes the cached houston
// marketplace prices, bypassing the cache TTL.
type RefreshMarketplacePricesAction interface {
	// Run fetches the marketplace prices from houston and updates the cache.
	Run() error
}

type refreshMarketplacePricesAction struct {
	marketplaceRepository marketplace_data.MarketplaceRepository
}

func NewRefreshMarketplacePricesAction(
	marketplaceRepository marketplace_data.MarketplaceRepository,
) RefreshMarketplacePricesAction {
	return &refreshMarketplacePricesAction{
		marketplaceRepository: marketplaceRepository,
	}
}

func (ac *refreshMarketplacePricesAction) Run() error {
	return ac.marketplaceRepository.RefreshPrices()
}
