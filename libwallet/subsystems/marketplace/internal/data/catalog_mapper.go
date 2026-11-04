package data

import (
	"log/slog"

	"github.com/go-errors/errors"
	"github.com/shopspring/decimal"

	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/legacy/newop"
	internal_model "github.com/muun/libwallet/subsystems/marketplace/internal/domain/model"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/card"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/provider"
)

// mapCatalog assembles the marketplace catalog from the two houston
// endpoints, the bundled provider registry and the control plane.
// A provider enters the catalog iff houston serves prices this build can
// parse for it, this build bundles its metadata, the control plane
// whitelists it, and it keeps at least one parseable whitelisted card and
// one whitelisted shipping country. Entries that don't line up are skipped,
// never errors: one bad entry must degrade to a smaller catalog, not to no
// marketplace at all. Expected version skew — an unknown provider UUID, or a
// card attribute newer than this build (material, secure element) — logs at
// Info; control-plane filtering is expected configuration and logs at Debug;
// every other mismatch is a data inconsistency and logs at Warn.
func mapCatalog(
	cardsJSON model.SecurityCardsMarketplaceCardsJSON,
	pricesJSON model.SecurityCardsMarketplacePricesJSON,
	bundledProviders map[string]provider.Provider,
	controlPlane internal_model.ControlPlane,
) *internal_model.Catalog {
	cardsByProvider := make(map[string][]card.Card)
	unparseableCards := make(map[string]int)
	for _, c := range cardsJSON.Items {
		mapped, err := mapCard(c)
		if err != nil {
			slog.Info(
				"Skipping marketplace card this build can't parse",
				slog.String("cardUuid", c.UUID),
				slog.Any("error", err),
			)
			unparseableCards[c.ProviderUUID]++
			continue
		}
		cardsByProvider[c.ProviderUUID] = append(
			cardsByProvider[c.ProviderUUID], *mapped,
		)
	}

	// A provider with more than one prices entry has conflicting prices for
	// every destination (starting with its card price): that's a data
	// inconsistency, so the provider is dropped rather than resolved by
	// entry order.
	pricesEntries := make(map[string]int, len(pricesJSON.Items))
	for _, p := range pricesJSON.Items {
		pricesEntries[p.ProviderUUID]++
	}

	providers := make(
		[]internal_model.ProviderCatalog, 0, len(pricesJSON.Items),
	)
	priced := make(map[string]bool, len(pricesJSON.Items))
	for _, p := range pricesJSON.Items {
		if pricesEntries[p.ProviderUUID] > 1 {
			if !priced[p.ProviderUUID] {
				slog.Warn(
					"Skipping marketplace provider with duplicated prices entries",
					slog.String("providerUuid", p.ProviderUUID),
				)
			}
			priced[p.ProviderUUID] = true
			continue
		}
		priced[p.ProviderUUID] = true

		if !controlPlane.AllowsProvider(p.ProviderUUID) {
			slog.Debug(
				"Marketplace provider filtered out by the control plane",
				slog.String("providerUuid", p.ProviderUUID),
				slog.String("reason", "provider has no listings entry"),
			)
			continue
		}

		bundled, known := bundledProviders[p.ProviderUUID]
		if !known {
			slog.Info(
				"Skipping marketplace prices for unknown provider",
				slog.String("providerUuid", p.ProviderUUID),
			)
			continue
		}
		cards := cardsByProvider[p.ProviderUUID]
		if len(cards) == 0 {
			if unparseableCards[p.ProviderUUID] > 0 {
				slog.Info(
					"Skipping marketplace provider with no parseable cards",
					slog.String("providerUuid", p.ProviderUUID),
				)
			} else {
				slog.Warn(
					"Skipping marketplace provider with no cards",
					slog.String("providerUuid", p.ProviderUUID),
				)
			}
			continue
		}
		visibleCards := make([]card.Card, 0, len(cards))
		for _, c := range cards {
			if controlPlane.AllowsCard(c.UUID) {
				visibleCards = append(visibleCards, c)
			}
		}
		if len(visibleCards) == 0 {
			slog.Debug(
				"Marketplace provider filtered out by the control plane",
				slog.String("providerUuid", p.ProviderUUID),
				slog.String("reason", "no whitelisted cards left"),
			)
			continue
		}

		cardPrice, err := mapMonetaryAmount(p.CardPrice)
		if err != nil {
			slog.Warn(
				"Skipping marketplace provider with an unparseable card price",
				slog.String("providerUuid", p.ProviderUUID),
				slog.Any("error", err),
			)
			continue
		}
		// A free shipping bucket is a legal configuration; a free card is not.
		if !cardPrice.Value.IsPositive() {
			slog.Warn(
				"Skipping marketplace provider with a non-positive card price",
				slog.String("providerUuid", p.ProviderUUID),
				slog.String("amount", p.CardPrice.Amount),
			)
			continue
		}
		shippingPrices, err := mapShippingPrices(
			p.ShippingPrices,
			controlPlane.ShippableCountriesFor(p.ProviderUUID),
		)
		if err != nil {
			slog.Warn(
				"Skipping marketplace provider with invalid shipping prices",
				slog.String("providerUuid", p.ProviderUUID),
				slog.Any("error", err),
			)
			continue
		}
		if p.MinShippingTimeDays < 0 ||
			p.MinShippingTimeDays > p.MaxShippingTimeDays {
			slog.Warn(
				"Skipping marketplace provider with invalid shipping times",
				slog.String("providerUuid", p.ProviderUUID),
				slog.Int("minDays", int(p.MinShippingTimeDays)),
				slog.Int("maxDays", int(p.MaxShippingTimeDays)),
			)
			continue
		}
		if len(shippingPrices) == 0 {
			slog.Debug(
				"Marketplace provider filtered out by the control plane",
				slog.String("providerUuid", p.ProviderUUID),
				slog.String("reason", "no whitelisted shipping countries left"),
			)
			continue
		}

		providers = append(
			providers,
			internal_model.NewProviderCatalog(
				bundled,
				visibleCards,
				cardPrice,
				shippingPrices,
				p.MinShippingTimeDays,
				p.MaxShippingTimeDays,
			),
		)
	}

	for providerUUID, cards := range cardsByProvider {
		if !priced[providerUUID] {
			slog.Warn(
				"Ignoring marketplace cards for a provider with no prices",
				slog.String("providerUuid", providerUUID),
				slog.Int("cardCount", len(cards)),
			)
		}
	}

	return internal_model.NewCatalog(providers)
}

func mapCard(in model.MarketplaceCardJSON) (*card.Card, error) {
	material, err := parseMaterial(in.Material)
	if err != nil {
		return nil, err
	}
	secureElement, err := parseSecureElement(in.SecureElement)
	if err != nil {
		return nil, err
	}

	mapped := card.NewCard(
		in.UUID,
		in.SKU,
		in.ImageURL,
		in.HasStock,
		*material,
		in.WidthMm,
		in.HeightMm,
		in.ThicknessMm,
		in.WeightGrams,
		*secureElement,
	)
	return &mapped, nil
}

// mapShippingPrices translates a provider's shipping table, keeping only the
// countries in the control plane's shippable set; buckets left with no
// country are dropped. Shipping (and taxes) are driven by the destination
// country, so a country appearing in more than one price bucket would make
// its shipping price ambiguous: that's a data inconsistency, reported as an
// error and checked on the raw data, before any filtering. So are an empty
// raw table (a provider that ships nowhere sells nothing) and a malformed
// country code (it would silently match no country).
func mapShippingPrices(
	in []model.MarketplaceShippingPriceJSON,
	shippable map[string]bool,
) ([]internal_model.ShippingPrice, error) {
	if len(in) == 0 {
		return nil, errors.New("provider has no shipping prices")
	}
	out := make([]internal_model.ShippingPrice, 0, len(in))
	pricedCountries := make(map[string]bool)
	for _, sp := range in {
		price, err := mapMonetaryAmount(sp.Price)
		if err != nil {
			return nil, err
		}

		countries := make(
			[]pmodel.CountryInfo, 0, len(sp.CountryCodes),
		)
		for _, code := range sp.CountryCodes {
			if !isIso3166Country(code) {
				return nil, errors.Errorf(
					"invalid country code %q", code,
				)
			}
			if pricedCountries[code] {
				return nil, errors.Errorf(
					"country %q has more than one shipping price", code,
				)
			}
			pricedCountries[code] = true
			if !shippable[code] {
				continue
			}
			countries = append(
				countries,
				pmodel.NewCountryInfo(code),
			)
		}
		if len(countries) == 0 {
			continue
		}
		out = append(
			out,
			internal_model.NewShippingPrice(price, countries),
		)
	}
	return out, nil
}

// mapMonetaryAmount translates a fiat price into a monetary amount. Its
// sats and primary currency equivalents are not part of the source data:
// the domain computes them, from the rates and primary currency the actions
// provide, when answering each screen. The currency code is only checked
// for shape here (see iso_codes.go for why): a well-formed code the rate
// window doesn't know fails at conversion time, where the rates are at hand.
func mapMonetaryAmount(
	in model.MarketplaceAmountJSON,
) (*newop.MonetaryAmount, error) {
	if !isIso4217Currency(in.CurrencyCode) {
		return nil, errors.Errorf(
			"invalid currency code %q", in.CurrencyCode,
		)
	}
	value, err := decimal.NewFromString(in.Amount)
	if err != nil {
		return nil, errors.Errorf(
			"unparseable price amount %q: %w", in.Amount, err,
		)
	}
	if value.IsNegative() {
		return nil, errors.Errorf(
			"negative price amount %q", in.Amount,
		)
	}
	return &newop.MonetaryAmount{
		Value:    value,
		Currency: in.CurrencyCode,
	}, nil
}

// parseMaterial maps the wire material to the presentation enum. An unknown
// value is an error, never a fallback: houston may serve materials newer
// than this build, and a card must be skipped rather than mislabeled.
func parseMaterial(s string) (*card.Material, error) {
	var material card.Material
	switch s {
	case "PLASTIC":
		material = card.MaterialPlastic
	case "METAL":
		material = card.MaterialMetal
	default:
		return nil, errors.Errorf("unknown card material %q", s)
	}
	return &material, nil
}

// parseSecureElement maps the wire secure element to the presentation enum.
// Same contract as parseMaterial: unknown values are errors, not fallbacks.
func parseSecureElement(s string) (*card.SecureElement, error) {
	var secureElement card.SecureElement
	switch s {
	case "EAL_5":
		secureElement = card.SecureElementEAL5
	case "EAL_5_PLUS":
		secureElement = card.SecureElementEAL5Plus
	case "EAL_6":
		secureElement = card.SecureElementEAL6
	case "EAL_6_PLUS":
		secureElement = card.SecureElementEAL6Plus
	default:
		return nil, errors.Errorf("unknown secure element %q", s)
	}
	return &secureElement, nil
}
