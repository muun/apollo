package security_cards_marketplace

import (
	"github.com/muun/libwallet/domain/model/security_cards_marketplace"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
)

type GetSecurityCardsMarketplaceAction struct {
}

func NewGetSecurityCardsMarketplaceAction() *GetSecurityCardsMarketplaceAction {
	return &GetSecurityCardsMarketplaceAction{}
}

func (ac *GetSecurityCardsMarketplaceAction) Run() (*security_cards_marketplace.Marketplace, error) {
	marketplaceJson := model.SecurityCardsMarketplaceJson{
		Providers: []model.SecurityCardsProviderJson{
			{
				Name:         "Constellations",
				ColorHex:     "#B19B6A",
				Material:     "plastic",
				Price:        37500,
				ShippingCost: 30000,
				CurrencyCode: "ARS",
				SecurityCards: []model.SecurityCardJson{
					{Image: "sc_constellations_scorpius", Stock: 10},
					{Image: "sc_constellations_gemini", Stock: 10},
					{Image: "sc_constellations_sagitarius", Stock: 10},
					{Image: "sc_constellations_virgo", Stock: 10},
				},
			},
			{
				Name:         "Numbers",
				ColorHex:     "#D9DBDD",
				Material:     "plastic",
				Price:        30000,
				ShippingCost: 15000,
				CurrencyCode: "ARS",
				SecurityCards: []model.SecurityCardJson{
					{Image: "sc_numbers_1", Stock: 10},
					{Image: "sc_numbers_2", Stock: 10},
					{Image: "sc_numbers_3", Stock: 10},
					{Image: "sc_numbers_4", Stock: 10},
					{Image: "sc_numbers_5", Stock: 10},
					{Image: "sc_numbers_6", Stock: 10},
					{Image: "sc_numbers_7", Stock: 10},
					{Image: "sc_numbers_8", Stock: 10},
					{Image: "sc_numbers_9", Stock: 10},
				},
			},
			{
				Name:         "Planets",
				ColorHex:     "#158E5A",
				Material:     "plastic",
				Price:        76485,
				ShippingCost: 43500,
				CurrencyCode: "ARS",
				SecurityCards: []model.SecurityCardJson{
					{Image: "sc_planets_earth", Stock: 10},
					{Image: "sc_planets_mars", Stock: 10},
				},
			},
		},
	}

	marketplace, err := service.MapSecurityCardsMarketplace(marketplaceJson)
	return marketplace, err
}
