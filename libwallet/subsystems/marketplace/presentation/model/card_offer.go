package model

import (
	"github.com/muun/libwallet/legacy/newop"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/card"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/provider"
)

// CardOffer is a provider's offer of a specific card for a given country:
// the card itself plus the provider's shipping terms for that country.
type CardOffer struct {
	Provider            provider.Provider
	Card                card.Card
	ShippingCountry     CountryInfo
	MinShippingTimeDays int32
	MaxShippingTimeDays int32
	ShippingPrice       *newop.BitcoinAmount
	CardPrice           *newop.BitcoinAmount
}

func NewCardOffer(
	provider provider.Provider,
	card card.Card,
	shippingCountry CountryInfo,
	minShippingTimeDays int32,
	maxShippingTimeDays int32,
	shippingPrice *newop.BitcoinAmount,
	cardPrice *newop.BitcoinAmount,
) *CardOffer {
	return &CardOffer{
		Provider:            provider,
		Card:                card,
		ShippingCountry:     shippingCountry,
		MinShippingTimeDays: minShippingTimeDays,
		MaxShippingTimeDays: maxShippingTimeDays,
		ShippingPrice:       shippingPrice,
		CardPrice:           cardPrice,
	}
}
