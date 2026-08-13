package security_cards_marketplace

import (
	"fmt"

	"github.com/muun/libwallet/domain/model/security_cards_marketplace"
	"github.com/muun/libwallet/service"
)

type GetSecurityCardsMarketplaceAction struct {
	houstonService service.HoustonService
}

func NewGetSecurityCardsMarketplaceAction(houstonService service.HoustonService) *GetSecurityCardsMarketplaceAction { //nolint:lll // TODO: line too long
	return &GetSecurityCardsMarketplaceAction{houstonService: houstonService}
}

func (ac *GetSecurityCardsMarketplaceAction) Run() (*security_cards_marketplace.Marketplace, error) { //nolint:lll // TODO: line too long
	marketplaceJson, err := ac.houstonService.FetchSecurityCardsMarketplace() //nolint:staticcheck // TODO: var marketplaceJson should be marketplaceJSON
	if err != nil {
		return nil, fmt.Errorf("error fetching sc marketplace from server: %w", err) //nolint:forbidigo // TODO: use errors.Errorf from go-errors for stack traces
	}

	return service.MapSecurityCardsMarketplace(marketplaceJson)
}
