package model

import (
	"github.com/muun/libwallet/legacy/newop"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/card"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/provider"
)

// ProviderListing is a provider's offer for a given country: the cards it
// ships there, the estimated shipping price and the provider's card price.
type ProviderListing struct {
	Provider      provider.Provider
	Cards         []card.Card
	ShippingPrice *newop.BitcoinAmount
	CardPrice     *newop.BitcoinAmount
}

func NewProviderListing(
	provider provider.Provider,
	cards []card.Card,
	shippingPrice *newop.BitcoinAmount,
	cardPrice *newop.BitcoinAmount,
) ProviderListing {
	return ProviderListing{
		Provider:      provider,
		Cards:         cards,
		ShippingPrice: shippingPrice,
		CardPrice:     cardPrice,
	}
}
