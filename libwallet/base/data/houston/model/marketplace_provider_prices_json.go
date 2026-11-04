package model

// MarketplaceProviderPricesJSON carries one provider's card price and
// shipping prices. Shipping time bounds are inclusive, in days.
type MarketplaceProviderPricesJSON struct {
	ProviderUUID        string                         `json:"providerUuid"`
	CardPrice           MarketplaceAmountJSON          `json:"cardPrice"`
	ShippingPrices      []MarketplaceShippingPriceJSON `json:"shippingPrices"`
	MinShippingTimeDays int32                          `json:"minShippingTimeDays"`
	MaxShippingTimeDays int32                          `json:"maxShippingTimeDays"`
}
