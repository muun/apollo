package data

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/data/houston/model"
	internal_model "github.com/muun/libwallet/subsystems/marketplace/internal/domain/model"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/card"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/provider"
)

// Houston may serve cards and prices for providers this app version doesn't
// bundle (a newer provider, or one no longer shipped). Those entries must be
// skipped, never fail the whole catalog.
func TestMapCatalogSkipsUnknownProviders(t *testing.T) {
	const knownUUID = "known-provider-uuid"
	const unknownUUID = "unknown-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  knownUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
			{
				UUID:          "card-2",
				ProviderUUID:  unknownUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}
	pricesJSON := model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: knownUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "USD", Amount: "10.00",
				},
				ShippingPrices: usShippingBucket(),
			},
			{
				ProviderUUID: unknownUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "USD", Amount: "20.00",
				},
				ShippingPrices: usShippingBucket(),
			},
		},
	}
	controlPlane := internal_model.NewControlPlane(
		[]string{"US"},
		map[string][]string{knownUUID: {"US"}, unknownUUID: {"US"}},
		[]string{"card-1", "card-2"},
	)

	catalog := mapCatalog(
		cardsJSON, pricesJSON, testBundledProviders(knownUUID), controlPlane,
	)

	require.Len(t, catalog.Providers, 1)
	p := catalog.Providers[0]
	assert.Equal(t, knownUUID, p.Provider.UUID)
	require.Len(t, p.Cards, 1)
	assert.Equal(t, "card-1", p.Cards[0].UUID)
}

// A prices entry for a provider with no cards would produce a listing with
// nothing to buy: the provider is skipped instead.
func TestMapCatalogSkipsProvidersWithoutCards(t *testing.T) {
	const cardlessUUID = "cardless-provider-uuid"

	catalog := mapCatalog(
		model.SecurityCardsMarketplaceCardsJSON{},
		validPricesFor(cardlessUUID),
		testBundledProviders(cardlessUUID),
		allowingControlPlane(cardlessUUID, nil, []string{"US"}),
	)
	assert.Empty(t, catalog.Providers)
}

// A malformed amount from one provider degrades to a smaller catalog: the
// offending provider is skipped, the rest survive.
func TestMapCatalogSkipsProvidersWithUnparseableAmounts(t *testing.T) {
	const brokenUUID = "broken-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  brokenUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}
	pricesJSON := model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: brokenUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "USD", Amount: "not-a-number",
				},
				ShippingPrices: usShippingBucket(),
			},
		},
	}

	catalog := mapCatalog(
		cardsJSON,
		pricesJSON,
		testBundledProviders(brokenUUID),
		allowingControlPlane(brokenUUID, []string{"card-1"}, []string{"US"}),
	)
	assert.Empty(t, catalog.Providers)
}

// One malformed shipping price drops its whole provider, even when the card
// price and the other shipping entries parse fine: the rest of the catalog
// survives.
func TestMapCatalogSkipsProvidersWithUnparseableShippingPrices(t *testing.T) {
	const brokenUUID = "broken-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  brokenUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}
	pricesJSON := model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: brokenUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "USD", Amount: "10.00",
				},
				ShippingPrices: []model.MarketplaceShippingPriceJSON{
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "5.00",
						},
						CountryCodes: []string{"US"},
					},
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "not-a-number",
						},
						CountryCodes: []string{"DE"},
					},
				},
			},
		},
	}

	catalog := mapCatalog(
		cardsJSON,
		pricesJSON,
		testBundledProviders(brokenUUID),
		allowingControlPlane(brokenUUID, []string{"card-1"}, []string{"US", "DE"}),
	)
	assert.Empty(t, catalog.Providers)
}

// Duplicated prices entries for the same provider mean conflicting prices
// for every destination, starting with the card price: a data inconsistency,
// so the provider is dropped rather than resolved by entry order.
func TestMapCatalogSkipsProvidersWithDuplicatedPricesEntries(t *testing.T) {
	const dupUUID = "duplicated-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  dupUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}
	pricesJSON := model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: dupUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "USD", Amount: "10.00",
				},
				ShippingPrices: usShippingBucket(),
			},
			{
				ProviderUUID: dupUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "USD", Amount: "20.00",
				},
				ShippingPrices: usShippingBucket(),
			},
		},
	}

	catalog := mapCatalog(
		cardsJSON,
		pricesJSON,
		testBundledProviders(dupUUID),
		allowingControlPlane(dupUUID, []string{"card-1"}, []string{"US"}),
	)
	assert.Empty(t, catalog.Providers)
}

// Shipping prices are driven by the destination country. A country listed in
// more than one price bucket has an ambiguous shipping price: a data
// inconsistency, so the provider is dropped.
func TestMapCatalogSkipsProvidersWithConflictingShippingPrices(t *testing.T) {
	const conflictedUUID = "conflicted-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  conflictedUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}
	pricesJSON := model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: conflictedUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "USD", Amount: "10.00",
				},
				ShippingPrices: []model.MarketplaceShippingPriceJSON{
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "5.00",
						},
						CountryCodes: []string{"AR", "US"},
					},
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "25.00",
						},
						CountryCodes: []string{"BE", "AR"},
					},
				},
			},
		},
	}

	catalog := mapCatalog(
		cardsJSON,
		pricesJSON,
		testBundledProviders(conflictedUUID),
		allowingControlPlane(
			conflictedUUID, []string{"card-1"}, []string{"AR", "US", "BE"},
		),
	)
	assert.Empty(t, catalog.Providers)
}

// The happy path: a fully populated, fully whitelisted provider maps every
// field — the card, the prices with their country buckets, the shipping
// window, and the metadata coming from the bundled registry rather than
// houston.
func TestMapCatalogMapsFullProvider(t *testing.T) {
	const fullUUID = "full-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  fullUUID,
				SKU:           "FULL-01",
				ImageURL:      "https://example.com/card.png",
				Material:      "METAL",
				WidthMm:       86.6,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5,
				SecureElement: "EAL_6_PLUS",
				HasStock:      true,
			},
		},
	}
	pricesJSON := model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: fullUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "EUR", Amount: "200.00",
				},
				ShippingPrices: []model.MarketplaceShippingPriceJSON{
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "EUR", Amount: "15.00",
						},
						CountryCodes: []string{"US", "DE"},
					},
				},
				MinShippingTimeDays: 5,
				MaxShippingTimeDays: 10,
			},
		},
	}
	bundled := testBundledProviders(fullUUID)

	catalog := mapCatalog(
		cardsJSON,
		pricesJSON,
		bundled,
		allowingControlPlane(fullUUID, []string{"card-1"}, []string{"US", "DE"}),
	)

	require.Len(t, catalog.Providers, 1)
	p := catalog.Providers[0]

	assert.Equal(t, bundled[fullUUID], p.Provider)

	require.Len(t, p.Cards, 1)
	c := p.Cards[0]
	assert.Equal(t, "card-1", c.UUID)
	assert.Equal(t, "FULL-01", c.SKU)
	assert.Equal(t, "https://example.com/card.png", c.ImageURL)
	assert.Equal(t, card.MaterialMetal, c.Material)
	assert.Equal(t, float32(86.6), c.WidthMm)
	assert.Equal(t, float32(54), c.HeightMm)
	assert.Equal(t, float32(0.8), c.ThicknessMm)
	assert.Equal(t, float32(5), c.WeightGrams)
	assert.Equal(t, card.SecureElementEAL6Plus, c.SecureElement)
	assert.True(t, c.HasStock)

	assert.Equal(t, "200.00", p.CardPrice.Value.StringFixed(2))
	assert.Equal(t, "EUR", p.CardPrice.Currency)

	require.Len(t, p.ShippingPrices, 1)
	sp := p.ShippingPrices[0]
	assert.Equal(t, "15.00", sp.Price.Value.StringFixed(2))
	assert.Equal(t, "EUR", sp.Price.Currency)
	assert.Equal(
		t,
		[]pmodel.CountryInfo{
			pmodel.NewCountryInfo("US"),
			pmodel.NewCountryInfo("DE"),
		},
		sp.Countries,
	)

	assert.Equal(t, int32(5), p.MinShippingTimeDays)
	assert.Equal(t, int32(10), p.MaxShippingTimeDays)
}

// The provider-level kill-switch: a provider missing from the providers
// whitelist never enters the catalog, however valid its data.
func TestMapCatalogDropsKillSwitchedProviders(t *testing.T) {
	const killedUUID = "killed-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  killedUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}
	// Killing a provider is removing its listings entry: there is no
	// separate providers whitelist to toggle.
	controlPlane := internal_model.NewControlPlane(
		[]string{"US"},
		map[string][]string{},
		[]string{"card-1"},
	)

	catalog := mapCatalog(
		cardsJSON,
		validPricesFor(killedUUID),
		testBundledProviders(killedUUID),
		controlPlane,
	)
	assert.Empty(t, catalog.Providers)
}

// Cards are individually whitelisted; a provider whose cards are all
// filtered out drops entirely.
func TestMapCatalogFiltersCardsThroughTheControlPlane(t *testing.T) {
	const providerUUID = "filtered-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  providerUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
			{
				UUID:          "card-2",
				ProviderUUID:  providerUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}
	pricesJSON := validPricesFor(providerUUID)
	bundled := testBundledProviders(providerUUID)

	catalog := mapCatalog(
		cardsJSON,
		pricesJSON,
		bundled,
		allowingControlPlane(providerUUID, []string{"card-2"}, []string{"US"}),
	)
	require.Len(t, catalog.Providers, 1)
	require.Len(t, catalog.Providers[0].Cards, 1)
	assert.Equal(t, "card-2", catalog.Providers[0].Cards[0].UUID)

	catalog = mapCatalog(
		cardsJSON,
		pricesJSON,
		bundled,
		allowingControlPlane(providerUUID, nil, []string{"US"}),
	)
	assert.Empty(
		t, catalog.Providers,
		"a provider with every card filtered out should drop",
	)
}

// Shipping buckets are intersected with the global country whitelist and the
// provider's listing whitelist; emptied buckets are dropped, and a provider
// left with no bucket drops entirely.
func TestMapCatalogIntersectsShippingWithTheControlPlane(t *testing.T) {
	const providerUUID = "listed-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  providerUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}
	pricesJSON := model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: providerUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "USD", Amount: "10.00",
				},
				ShippingPrices: []model.MarketplaceShippingPriceJSON{
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "5.00",
						},
						CountryCodes: []string{"US", "DE"},
					},
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "USD", Amount: "25.00",
						},
						CountryCodes: []string{"GB"},
					},
				},
			},
		},
	}
	bundled := testBundledProviders(providerUUID)

	// Globally whitelisted: US, GB. Listed for the provider: only US.
	controlPlane := internal_model.NewControlPlane(
		[]string{"US", "GB"},
		map[string][]string{providerUUID: {"US"}},
		[]string{"card-1"},
	)

	catalog := mapCatalog(cardsJSON, pricesJSON, bundled, controlPlane)
	require.Len(t, catalog.Providers, 1)
	shippingPrices := catalog.Providers[0].ShippingPrices
	require.Len(
		t, shippingPrices, 1, "the GB-only bucket should be dropped",
	)
	assert.Equal(
		t,
		[]pmodel.CountryInfo{pmodel.NewCountryInfo("US")},
		shippingPrices[0].Countries,
	)

	// No listing entry at all is the kill-switch: the provider drops.
	controlPlane = internal_model.NewControlPlane(
		[]string{"US", "GB"},
		nil,
		[]string{"card-1"},
	)
	catalog = mapCatalog(cardsJSON, pricesJSON, bundled, controlPlane)
	assert.Empty(t, catalog.Providers)
}

// The zero-value control plane (absent, unreadable, or not yet wired) hides
// the whole marketplace: fail closed.
func TestMapCatalogWithClosedControlPlaneIsEmpty(t *testing.T) {
	const providerUUID = "any-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  providerUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}

	catalog := mapCatalog(
		cardsJSON,
		validPricesFor(providerUUID),
		testBundledProviders(providerUUID),
		internal_model.ControlPlane{},
	)
	assert.Empty(t, catalog.Providers)
}

// Houston may serve card attributes newer than this build (a new material, a
// new secure element). Such a card is skipped, never mislabeled; a provider
// whose cards are all unparseable drops out.
func TestMapCatalogSkipsCardsWithUnknownAttributes(t *testing.T) {
	const providerUUID = "skewed-provider-uuid"

	cardsJSON := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  providerUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
			{
				UUID:          "card-2",
				ProviderUUID:  providerUUID,
				Material:      "TITANIUM",
				SecureElement: "EAL_5",
			},
			{
				UUID:          "card-3",
				ProviderUUID:  providerUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_7",
			},
		},
	}
	pricesJSON := validPricesFor(providerUUID)
	bundled := testBundledProviders(providerUUID)
	controlPlane := allowingControlPlane(
		providerUUID,
		[]string{"card-1", "card-2", "card-3"},
		[]string{"US"},
	)

	catalog := mapCatalog(cardsJSON, pricesJSON, bundled, controlPlane)
	require.Len(t, catalog.Providers, 1)
	require.Len(t, catalog.Providers[0].Cards, 1)
	assert.Equal(t, "card-1", catalog.Providers[0].Cards[0].UUID)

	// Every card unparseable: the provider drops out entirely.
	cardsJSON.Items = cardsJSON.Items[1:]
	catalog = mapCatalog(cardsJSON, pricesJSON, bundled, controlPlane)
	assert.Empty(t, catalog.Providers)
}

// A provider with an empty shipping table ships nowhere, so it sells
// nothing: the same inconsistency as a provider with no cards.
func TestMapCatalogSkipsProvidersWithNoShippingPrices(t *testing.T) {
	const providerUUID = "shipless-provider-uuid"

	cardsJSON := validCardsFor(providerUUID)
	pricesJSON := validPricesFor(providerUUID)
	pricesJSON.Items[0].ShippingPrices = nil

	catalog := mapCatalog(
		cardsJSON,
		pricesJSON,
		testBundledProviders(providerUUID),
		allowingControlPlane(providerUUID, []string{"card-1"}, []string{"US"}),
	)
	assert.Empty(t, catalog.Providers)
}

// A malformed country code would silently match no real country, dropping
// the destination with no signal: a data inconsistency, so the provider is
// dropped loudly instead.
func TestMapCatalogSkipsProvidersWithMalformedCountryCodes(t *testing.T) {
	const providerUUID = "typoed-provider-uuid"

	for _, code := range []string{"us", "USA", "U", ""} {
		cardsJSON := validCardsFor(providerUUID)
		pricesJSON := validPricesFor(providerUUID)
		pricesJSON.Items[0].ShippingPrices[0].CountryCodes = []string{code}

		catalog := mapCatalog(
			cardsJSON,
			pricesJSON,
			testBundledProviders(providerUUID),
			allowingControlPlane(providerUUID, []string{"card-1"}, []string{"US"}),
		)
		assert.Empty(t, catalog.Providers, "country code %q", code)
	}
}

// A negative price and a malformed currency code are data inconsistencies,
// wherever they appear.
func TestMapCatalogSkipsProvidersWithInvalidAmounts(t *testing.T) {
	const providerUUID = "mispriced-provider-uuid"

	for _, amount := range []model.MarketplaceAmountJSON{
		{CurrencyCode: "USD", Amount: "-5.00"},
		{CurrencyCode: "usd", Amount: "5.00"},
		{CurrencyCode: "US", Amount: "5.00"},
		{CurrencyCode: "", Amount: "5.00"},
	} {
		cardsJSON := validCardsFor(providerUUID)
		pricesJSON := validPricesFor(providerUUID)
		pricesJSON.Items[0].CardPrice = amount

		catalog := mapCatalog(
			cardsJSON,
			pricesJSON,
			testBundledProviders(providerUUID),
			allowingControlPlane(providerUUID, []string{"card-1"}, []string{"US"}),
		)
		assert.Empty(t, catalog.Providers, "card price %+v", amount)

		pricesJSON = validPricesFor(providerUUID)
		pricesJSON.Items[0].ShippingPrices[0].Price = amount

		catalog = mapCatalog(
			cardsJSON,
			pricesJSON,
			testBundledProviders(providerUUID),
			allowingControlPlane(providerUUID, []string{"card-1"}, []string{"US"}),
		)
		assert.Empty(t, catalog.Providers, "shipping price %+v", amount)
	}
}

// A free card is a configuration error; a free shipping bucket is a legal
// configuration.
func TestMapCatalogSkipsZeroCardPriceButKeepsFreeShipping(t *testing.T) {
	const providerUUID = "freebie-provider-uuid"

	cardsJSON := validCardsFor(providerUUID)
	pricesJSON := validPricesFor(providerUUID)
	pricesJSON.Items[0].CardPrice.Amount = "0.00"

	catalog := mapCatalog(
		cardsJSON,
		pricesJSON,
		testBundledProviders(providerUUID),
		allowingControlPlane(providerUUID, []string{"card-1"}, []string{"US"}),
	)
	assert.Empty(t, catalog.Providers)

	pricesJSON = validPricesFor(providerUUID)
	pricesJSON.Items[0].ShippingPrices[0].Price.Amount = "0.00"

	catalog = mapCatalog(
		cardsJSON,
		pricesJSON,
		testBundledProviders(providerUUID),
		allowingControlPlane(providerUUID, []string{"card-1"}, []string{"US"}),
	)
	require.Len(t, catalog.Providers, 1)
	assert.True(t, catalog.Providers[0].ShippingPrices[0].Price.Value.IsZero())
}

// Shipping time bounds must describe a real window: non-negative days, min
// no later than max.
func TestMapCatalogSkipsProvidersWithInvalidShippingTimes(t *testing.T) {
	const providerUUID = "timeless-provider-uuid"

	for _, days := range []struct{ min, max int32 }{
		{min: 10, max: 5},
		{min: -1, max: 5},
	} {
		cardsJSON := validCardsFor(providerUUID)
		pricesJSON := validPricesFor(providerUUID)
		pricesJSON.Items[0].MinShippingTimeDays = days.min
		pricesJSON.Items[0].MaxShippingTimeDays = days.max

		catalog := mapCatalog(
			cardsJSON,
			pricesJSON,
			testBundledProviders(providerUUID),
			allowingControlPlane(providerUUID, []string{"card-1"}, []string{"US"}),
		)
		assert.Empty(t, catalog.Providers, "min %d max %d", days.min, days.max)
	}
}

func testBundledProviders(uuid string) map[string]provider.Provider {
	return map[string]provider.Provider{
		uuid: provider.NewProvider(
			uuid,
			"Bundled",
			"https://example.com",
			provider.NewTheme(0x111111, 0x222222),
			provider.NewTheme(0x111111, 0x222222),
		),
	}
}

// validCardsFor builds a minimal parseable cards payload for the given
// provider.
func validCardsFor(
	providerUUID string,
) model.SecurityCardsMarketplaceCardsJSON {
	return model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-1",
				ProviderUUID:  providerUUID,
				Material:      "PLASTIC",
				SecureElement: "EAL_5",
			},
		},
	}
}

// allowingControlPlane builds a control plane that whitelists exactly the
// given provider (listed in all the given countries) and cards.
func allowingControlPlane(
	providerUUID string,
	cardUUIDs []string,
	countries []string,
) internal_model.ControlPlane {
	return internal_model.NewControlPlane(
		countries,
		map[string][]string{providerUUID: countries},
		cardUUIDs,
	)
}

// validPricesFor builds a minimal valid prices payload for the given
// provider: a parseable positive card price and one US-shipping bucket.
func validPricesFor(
	providerUUID string,
) model.SecurityCardsMarketplacePricesJSON {
	return model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: providerUUID,
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "USD", Amount: "10.00",
				},
				ShippingPrices:      usShippingBucket(),
				MinShippingTimeDays: 1,
				MaxShippingTimeDays: 5,
			},
		},
	}
}

func usShippingBucket() []model.MarketplaceShippingPriceJSON {
	return []model.MarketplaceShippingPriceJSON{
		{
			Price: model.MarketplaceAmountJSON{
				CurrencyCode: "USD", Amount: "5.00",
			},
			CountryCodes: []string{"US"},
		},
	}
}
