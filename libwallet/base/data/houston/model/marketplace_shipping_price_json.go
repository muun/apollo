package model

// MarketplaceShippingPriceJSON is one shipping price bucket: the price to
// ship to any of the listed countries. Country codes are uppercase ISO
// 3166-1 alpha-2. A country in no bucket is one the provider does not ship
// to. A country must appear in at most one bucket per provider: a repeated
// country is a data inconsistency, and clients drop the whole provider.
type MarketplaceShippingPriceJSON struct {
	Price        MarketplaceAmountJSON `json:"price"`
	CountryCodes []string              `json:"countryCodes"`
}
