package model

import (
	"github.com/go-errors/errors"
	"github.com/shopspring/decimal"

	"github.com/muun/libwallet/legacy/newop"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/provider"
)

const satsPerBitcoin = 100_000_000

func toBitcoinAmount(
	amount *newop.MonetaryAmount,
	rates map[string]float64,
	primaryCurrency string,
) (*newop.BitcoinAmount, error) {
	inputRate, ok := rates[amount.Currency]
	if !ok {
		return nil, errors.Errorf(
			"no exchange rate for currency %q", amount.Currency,
		)
	}
	primaryRate, ok := rates[primaryCurrency]
	if !ok {
		return nil, errors.Errorf(
			"no exchange rate for currency %q", primaryCurrency,
		)
	}

	valueInBtc := amount.Value.Div(decimal.NewFromFloat(inputRate))
	inSat := valueInBtc.
		Mul(decimal.NewFromInt(satsPerBitcoin)).
		RoundBank(0).
		IntPart()
	inPrimaryCurrency := &newop.MonetaryAmount{
		Value:    valueInBtc.Mul(decimal.NewFromFloat(primaryRate)),
		Currency: primaryCurrency,
	}

	return &newop.BitcoinAmount{
		InSat:             inSat,
		InInputCurrency:   amount,
		InPrimaryCurrency: inPrimaryCurrency,
	}, nil
}

// Catalog is the full security cards marketplace universe: every provider,
// the cards they sell and where they ship. The selection rules that answer
// each screen's question live here. Assembly guarantees at most one entry
// per provider (duplicated provider data is dropped as inconsistent), so
// lookups by provider UUID may take the first match.
type Catalog struct {
	Providers []ProviderCatalog
}

func NewCatalog(providers []ProviderCatalog) *Catalog {
	return &Catalog{Providers: providers}
}

// ListingsFor answers which providers ship to the given country: the cards
// each one offers there, its estimated shipping price and its card price.
// rates maps a currency code to the price of one BTC in that currency;
// together with primaryCurrency it prices every amount in sats, in the
// provider's currency and in the user's primary currency.
func (c *Catalog) ListingsFor(
	country pmodel.CountryInfo,
	rates map[string]float64,
	primaryCurrency string,
) ([]pmodel.ProviderListing, error) {
	var listings []pmodel.ProviderListing

	for _, p := range c.Providers {
		fiatShippingPrice, ships := p.ShippingTo(country)
		if !ships {
			continue
		}

		shippingPrice, err := toBitcoinAmount(fiatShippingPrice, rates, primaryCurrency)
		if err != nil {
			return nil, err
		}
		cardPrice, err := toBitcoinAmount(p.CardPrice, rates, primaryCurrency)
		if err != nil {
			return nil, err
		}

		listings = append(listings, pmodel.NewProviderListing(
			p.Provider,
			p.Cards,
			shippingPrice,
			cardPrice,
		))
	}

	return listings, nil
}

// CountriesServedBy filters the given countries down to the ones the
// given provider ships to.
func (c *Catalog) CountriesServedBy(
	providerUUID string,
	countries []pmodel.CountryInfo,
) ([]pmodel.CountryInfo, error) {
	providerCatalog, err := c.providerCatalog(providerUUID)
	if err != nil {
		return nil, err
	}
	return providerCatalog.ShippingDestinations(countries), nil
}

// OfferFor answers a provider's offer of the given card for the given
// country: the card itself plus the provider's shipping terms there.
// rates maps a currency code to the price of one BTC in that currency;
// together with primaryCurrency it prices every amount in sats, in the
// provider's currency and in the user's primary currency.
func (c *Catalog) OfferFor(
	country pmodel.CountryInfo,
	cardUUID string,
	rates map[string]float64,
	primaryCurrency string,
) (*pmodel.CardOffer, error) {
	for _, p := range c.Providers {
		for _, providerCard := range p.Cards {
			if providerCard.UUID != cardUUID {
				continue
			}

			fiatShippingPrice, ships := p.ShippingTo(country)
			if !ships {
				return nil, errors.Errorf(
					"provider %q does not ship to country %q",
					p.Provider.UUID, country.Code,
				)
			}

			shippingPrice, err := toBitcoinAmount(fiatShippingPrice, rates, primaryCurrency)
			if err != nil {
				return nil, err
			}
			cardPrice, err := toBitcoinAmount(p.CardPrice, rates, primaryCurrency)
			if err != nil {
				return nil, err
			}

			return pmodel.NewCardOffer(
				p.Provider,
				providerCard,
				country,
				p.MinShippingTimeDays,
				p.MaxShippingTimeDays,
				shippingPrice,
				cardPrice,
			), nil
		}
	}

	return nil, errors.Errorf(
		"security card with uuid %q not found", cardUUID,
	)
}

// SellerOf answers which provider sells the card with the given UUID.
func (c *Catalog) SellerOf(cardUUID string) (*provider.Provider, error) {
	for i := range c.Providers {
		for _, providerCard := range c.Providers[i].Cards {
			if providerCard.UUID == cardUUID {
				return &c.Providers[i].Provider, nil
			}
		}
	}

	return nil, errors.Errorf(
		"security card with uuid %q not found", cardUUID,
	)
}

// providerCatalog looks up a provider's catalog entry by UUID. Catalog
// assembly guarantees at most one entry per provider, so the first match is
// the only one.
func (c *Catalog) providerCatalog(uuid string) (*ProviderCatalog, error) {
	for i := range c.Providers {
		if c.Providers[i].Provider.UUID == uuid {
			return &c.Providers[i], nil
		}
	}
	return nil, errors.Errorf("provider with uuid %q not found", uuid)
}
