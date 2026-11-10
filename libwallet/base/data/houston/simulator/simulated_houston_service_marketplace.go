package simulator

import (
	"github.com/muun/libwallet/base/data/houston/model"
)

const (
	constellationsProviderUUID = "4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14"
	numbersProviderUUID        = "9e2c6b4a-1f8d-473e-b9a5-c3d2e1f4a7b8"
	planetsProviderUUID        = "b3f5c1d8-4a72-4e9b-8c61-d3a9e2f4b7c0"
)

func (m *SimulatedHoustonService) FetchSecurityCardsMarketplaceCards() (
	model.SecurityCardsMarketplaceCardsJSON,
	error,
) {
	return model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "a1b2c3d4-1111-4000-8000-000000000001",
				ProviderUUID:  constellationsProviderUUID,
				SKU:           "CONST-SCORP-01",
				ImageURL:      "https://placehold.co/2594x1632/FFF8E7/AFC9FF/png?text=SCORPIUS",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "a1b2c3d4-1111-4000-8000-000000000002",
				ProviderUUID:  constellationsProviderUUID,
				SKU:           "CONST-GEMI-01",
				ImageURL:      "https://placehold.co/2594x1632/FFF8E7/AFC9FF/png?text=GEMINI",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "a1b2c3d4-1111-4000-8000-000000000003",
				ProviderUUID:  constellationsProviderUUID,
				SKU:           "CONST-SAGI-01",
				ImageURL:      "https://placehold.co/2594x1632/FFF8E7/AFC9FF/png?text=SAGITARIUS",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "a1b2c3d4-1111-4000-8000-000000000004",
				ProviderUUID:  constellationsProviderUUID,
				SKU:           "CONST-VIRG-01",
				ImageURL:      "https://placehold.co/2594x1632/FFF8E7/AFC9FF/png?text=VIRGO",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      false,
			},
			{
				UUID:          "b2c3d4e5-2222-4000-8000-000000000001",
				ProviderUUID:  numbersProviderUUID,
				SKU:           "NUM-01",
				ImageURL:      "https://placehold.co/2594x1632/8B1A1A/4A0D0D/png?text=1",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "b2c3d4e5-2222-4000-8000-000000000002",
				ProviderUUID:  numbersProviderUUID,
				SKU:           "NUM-02",
				ImageURL:      "https://placehold.co/2594x1632/C9A227/7A5C10/png?text=2",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "b2c3d4e5-2222-4000-8000-000000000003",
				ProviderUUID:  numbersProviderUUID,
				SKU:           "NUM-03",
				ImageURL:      "https://placehold.co/2594x1632/1A4E8C/0C2E5E/png?text=3",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "b2c3d4e5-2222-4000-8000-000000000004",
				ProviderUUID:  numbersProviderUUID,
				SKU:           "NUM-04",
				ImageURL:      "https://placehold.co/2594x1632/C45A1A/6E2E08/png?text=4",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "b2c3d4e5-2222-4000-8000-000000000005",
				ProviderUUID:  numbersProviderUUID,
				SKU:           "NUM-05",
				ImageURL:      "https://placehold.co/2594x1632/1A6E3A/0B3D1F/png?text=5",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "b2c3d4e5-2222-4000-8000-000000000006",
				ProviderUUID:  numbersProviderUUID,
				SKU:           "NUM-06",
				ImageURL:      "https://placehold.co/2594x1632/5E2A7A/2E1240/png?text=6",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "b2c3d4e5-2222-4000-8000-000000000007",
				ProviderUUID:  numbersProviderUUID,
				SKU:           "NUM-07",
				ImageURL:      "https://placehold.co/2594x1632/0E5E6E/04323E/png?text=7",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      false,
			},
			{
				UUID:          "b2c3d4e5-2222-4000-8000-000000000008",
				ProviderUUID:  numbersProviderUUID,
				SKU:           "NUM-08",
				ImageURL:      "https://placehold.co/2594x1632/19A0B0/0A5E6A/png?text=8",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "b2c3d4e5-2222-4000-8000-000000000009",
				ProviderUUID:  numbersProviderUUID,
				SKU:           "NUM-09",
				ImageURL:      "https://placehold.co/2594x1632/B02A8E/5C1149/png?text=9",
				Material:      "PLASTIC",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_5_PLUS",
				HasStock:      false,
			},
			{
				UUID:          "c3d4e5f6-3333-4000-8000-000000000001",
				ProviderUUID:  planetsProviderUUID,
				SKU:           "PLAN-EARTH-01",
				ImageURL:      "https://placehold.co/2594x1632/081448/3B5D38/png?text=EARTH",
				Material:      "METAL",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_6_PLUS",
				HasStock:      true,
			},
			{
				UUID:          "c3d4e5f6-3333-4000-8000-000000000002",
				ProviderUUID:  planetsProviderUUID,
				SKU:           "PLAN-MARS-01",
				ImageURL:      "https://placehold.co/2594x1632/081448/C1440E/png?text=MARS",
				Material:      "METAL",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_6_PLUS",
				HasStock:      true,
			},
		},
	}, nil
}

func (m *SimulatedHoustonService) FetchSecurityCardsMarketplacePrices() (
	model.SecurityCardsMarketplacePricesJSON,
	error,
) {
	return model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: constellationsProviderUUID,
				CardPrice:    model.MarketplaceAmountJSON{CurrencyCode: "EUR", Amount: "200.00"},
				ShippingPrices: []model.MarketplaceShippingPriceJSON{
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "EUR", Amount: "15.00",
						},
						CountryCodes: []string{"US", "DE", "ES"},
					},
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "EUR", Amount: "30.00",
						},
						CountryCodes: []string{"GB", "BR"},
					},
				},
				MinShippingTimeDays: 5,
				MaxShippingTimeDays: 10,
			},
			{
				ProviderUUID: numbersProviderUUID,
				CardPrice:    model.MarketplaceAmountJSON{CurrencyCode: "ARS", Amount: "50000.00"},
				ShippingPrices: []model.MarketplaceShippingPriceJSON{
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "10.00",
						},
						CountryCodes: []string{"US", "FR"},
					},
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "20.00",
						},
						CountryCodes: []string{"CA", "MX"},
					},
				},
				MinShippingTimeDays: 7,
				MaxShippingTimeDays: 14,
			},
			{
				ProviderUUID: planetsProviderUUID,
				CardPrice:    model.MarketplaceAmountJSON{CurrencyCode: "USD", Amount: "100.00"},
				ShippingPrices: []model.MarketplaceShippingPriceJSON{
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "10.00",
						},
						CountryCodes: []string{"DE", "IT"},
					},
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "20.00",
						},
						CountryCodes: []string{"NL"},
					},
				},
				MinShippingTimeDays: 10,
				MaxShippingTimeDays: 21,
			},
		},
	}, nil
}
