package action

import (
	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
)

// RefreshMarketplaceCardsAction force-refreshes the cached houston
// marketplace cards, bypassing the cache TTL.
type RefreshMarketplaceCardsAction interface {
	// Run fetches the marketplace cards from houston and updates the cache.
	Run() error
}

type refreshMarketplaceCardsAction struct {
	marketplaceRepository marketplace_data.MarketplaceRepository
}

func NewRefreshMarketplaceCardsAction(
	marketplaceRepository marketplace_data.MarketplaceRepository,
) RefreshMarketplaceCardsAction {
	return &refreshMarketplaceCardsAction{
		marketplaceRepository: marketplaceRepository,
	}
}

func (ac *refreshMarketplaceCardsAction) Run() error {
	return ac.marketplaceRepository.RefreshCards()
}
