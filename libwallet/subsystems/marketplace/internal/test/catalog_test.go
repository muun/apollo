package test

import (
	"testing"

	"github.com/muun/libwallet/legacy/newop"
	"github.com/muun/libwallet/subsystems/marketplace/internal/domain/model"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/card"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/provider"
)

const (
	cardUUID      = "card-uuid-1"
	otherCardUUID = "card-uuid-2"

	// testPrimaryCurrency differs from the providers' currency so the
	// primary-currency conversion is observable.
	testPrimaryCurrency = "EUR"
)

// testRates price one BTC at 100_000 USD (so 1 USD = 1_000 sats) and at
// 50_000 EUR (so 1 USD = 0.5 EUR).
var testRates = map[string]float64{
	"USD": 100_000,
	"EUR": 50_000,
}

// testCatalog builds a catalog with two providers: one shipping to the US
// and one shipping to Germany, both priced in USD.
func testCatalog() *model.Catalog {
	usProvider := model.NewProviderCatalog(
		provider.NewProvider(
			"us-provider",
			"US Provider",
			"https://us-provider.example",
			provider.NewTheme(0xAABBCC, 0x112233),
			provider.NewTheme(0x445566, 0x778899),
		),
		[]card.Card{
			card.NewCard(
				cardUUID, "SKU-1", "https://img/1", true,
				card.MaterialPlastic, 86.6, 54, 0.8, 5,
				card.SecureElementEAL5Plus,
			),
		},
		newop.NewMonetaryAmountFromFiat("150", "USD"),
		[]model.ShippingPrice{
			model.NewShippingPrice(
				newop.NewMonetaryAmountFromFiat("20", "USD"),
				[]pmodel.CountryInfo{
					pmodel.NewCountryInfo("US"),
					pmodel.NewCountryInfo("CA"),
				},
			),
		},
		3, 7,
	)

	deProvider := model.NewProviderCatalog(
		provider.NewProvider(
			"de-provider",
			"DE Provider",
			"https://de-provider.example",
			provider.NewTheme(0, 0),
			provider.NewTheme(0, 0),
		),
		[]card.Card{
			card.NewCard(
				otherCardUUID, "SKU-2", "https://img/2", false,
				card.MaterialMetal, 86.6, 54, 0.8, 5,
				card.SecureElementEAL6,
			),
		},
		newop.NewMonetaryAmountFromFiat("200", "USD"),
		[]model.ShippingPrice{
			model.NewShippingPrice(
				newop.NewMonetaryAmountFromFiat("35", "USD"),
				[]pmodel.CountryInfo{pmodel.NewCountryInfo("DE")},
			),
		},
		5, 10,
	)

	return model.NewCatalog([]model.ProviderCatalog{usProvider, deProvider})
}

func TestListingsForFiltersProvidersByCountry(t *testing.T) {
	listings, err := testCatalog().ListingsFor(
		pmodel.NewCountryInfo("US"), testRates, testPrimaryCurrency,
	)
	if err != nil {
		t.Fatalf("ListingsFor: %v", err)
	}

	if len(listings) != 1 {
		t.Fatalf("expected 1 listing, got %d", len(listings))
	}
	listing := listings[0]
	if listing.Provider.UUID != "us-provider" {
		t.Errorf("expected us-provider, got %q", listing.Provider.UUID)
	}
	if len(listing.Cards) != 1 || listing.Cards[0].UUID != cardUUID {
		t.Errorf("unexpected cards: %+v", listing.Cards)
	}
	if got := listing.ShippingPrice.InInputCurrency.ValueAsString(); got != "20" {
		t.Errorf("expected US shipping price of 20, got %v", got)
	}
}

func TestListingsForPricesAmounts(t *testing.T) {
	listings, err := testCatalog().ListingsFor(
		pmodel.NewCountryInfo("US"), testRates, testPrimaryCurrency,
	)
	if err != nil {
		t.Fatalf("ListingsFor: %v", err)
	}

	// 150 USD at 100_000 USD/BTC = 150_000 sats = 75 EUR at 50_000 EUR/BTC.
	cardPrice := listings[0].CardPrice
	if cardPrice.InSat != 150_000 {
		t.Errorf(
			"expected card price of 150000 sats, got %d", cardPrice.InSat,
		)
	}
	if cardPrice.InInputCurrency.Currency != "USD" ||
		cardPrice.InInputCurrency.ValueAsString() != "150" {
		t.Errorf(
			"unexpected input currency price: %v", cardPrice.InInputCurrency,
		)
	}
	if cardPrice.InPrimaryCurrency.Currency != "EUR" ||
		cardPrice.InPrimaryCurrency.ValueAsString() != "75" {
		t.Errorf(
			"unexpected primary currency price: %v",
			cardPrice.InPrimaryCurrency,
		)
	}

	// 20 USD = 20_000 sats = 10 EUR.
	shippingPrice := listings[0].ShippingPrice
	if shippingPrice.InSat != 20_000 {
		t.Errorf(
			"expected shipping price of 20000 sats, got %d",
			shippingPrice.InSat,
		)
	}
	if shippingPrice.InPrimaryCurrency.ValueAsString() != "10" {
		t.Errorf(
			"unexpected primary currency shipping price: %v",
			shippingPrice.InPrimaryCurrency,
		)
	}
}

func TestListingsForCountryNobodyShipsTo(t *testing.T) {
	listings, err := testCatalog().ListingsFor(
		pmodel.NewCountryInfo("JP"), testRates, testPrimaryCurrency,
	)
	if err != nil {
		t.Fatalf("ListingsFor: %v", err)
	}
	if len(listings) != 0 {
		t.Fatalf("expected no listings, got %d", len(listings))
	}
}

func TestListingsForUnknownCurrency(t *testing.T) {
	catalog := model.NewCatalog([]model.ProviderCatalog{
		model.NewProviderCatalog(
			provider.NewProvider(
				"p", "P", "https://p.example",
				provider.NewTheme(0, 0), provider.NewTheme(0, 0),
			),
			nil,
			newop.NewMonetaryAmountFromFiat("10", "XXX"),
			[]model.ShippingPrice{
				model.NewShippingPrice(
					newop.NewMonetaryAmountFromFiat("1", "XXX"),
					[]pmodel.CountryInfo{pmodel.NewCountryInfo("US")},
				),
			},
			1, 2,
		),
	})

	_, err := catalog.ListingsFor(
		pmodel.NewCountryInfo("US"), testRates, testPrimaryCurrency,
	)
	if err == nil {
		t.Fatal("expected an error for an unknown currency")
	}
}

func TestListingsForUnknownPrimaryCurrency(t *testing.T) {
	_, err := testCatalog().ListingsFor(
		pmodel.NewCountryInfo("US"), testRates, "XXX",
	)
	if err == nil {
		t.Fatal("expected an error for an unknown primary currency")
	}
}

func TestOfferForFindsCardByUUID(t *testing.T) {
	offer, err := testCatalog().OfferFor(
		pmodel.NewCountryInfo("US"), cardUUID, testRates, testPrimaryCurrency,
	)
	if err != nil {
		t.Fatalf("OfferFor: %v", err)
	}

	if offer.Provider.UUID != "us-provider" {
		t.Errorf("expected us-provider, got %q", offer.Provider.UUID)
	}
	if offer.Card.UUID != cardUUID {
		t.Errorf("expected card %q, got %q", cardUUID, offer.Card.UUID)
	}
	if offer.ShippingCountry.Code != "US" {
		t.Errorf(
			"expected shipping country US, got %q",
			offer.ShippingCountry.Code,
		)
	}
	if offer.MinShippingTimeDays != 3 || offer.MaxShippingTimeDays != 7 {
		t.Errorf(
			"unexpected shipping times: %d-%d",
			offer.MinShippingTimeDays, offer.MaxShippingTimeDays,
		)
	}
	if offer.CardPrice.InSat != 150_000 {
		t.Errorf(
			"expected card price of 150000 sats, got %d",
			offer.CardPrice.InSat,
		)
	}
	if offer.ShippingPrice.InSat != 20_000 {
		t.Errorf(
			"expected shipping price of 20000 sats, got %d",
			offer.ShippingPrice.InSat,
		)
	}
}

func TestOfferForUnknownUUID(t *testing.T) {
	_, err := testCatalog().OfferFor(
		pmodel.NewCountryInfo("US"), "nope", testRates, testPrimaryCurrency,
	)
	if err == nil {
		t.Fatal("expected an error for an unknown card uuid")
	}
}

func TestOfferForProviderNotShippingToCountry(t *testing.T) {
	// The card exists, but its provider only ships to the US and CA.
	_, err := testCatalog().OfferFor(
		pmodel.NewCountryInfo("DE"), cardUUID, testRates, testPrimaryCurrency,
	)
	if err == nil {
		t.Fatal("expected an error when the provider does not ship there")
	}
}

func TestCountriesServedBy(t *testing.T) {
	catalog := testCatalog()

	served, err := catalog.CountriesServedBy("us-provider", []pmodel.CountryInfo{
		pmodel.NewCountryInfo("US"),
		pmodel.NewCountryInfo("CA"),
		pmodel.NewCountryInfo("DE"),
		pmodel.NewCountryInfo("JP"),
	})
	if err != nil {
		t.Fatalf("filtering countries for us-provider: %v", err)
	}
	if len(served) != 2 || served[0].Code != "US" || served[1].Code != "CA" {
		t.Errorf("expected [US CA], got %v", served)
	}

	served, err = catalog.CountriesServedBy("de-provider", []pmodel.CountryInfo{
		pmodel.NewCountryInfo("US"),
	})
	if err != nil {
		t.Fatalf("filtering countries for de-provider: %v", err)
	}
	if len(served) != 0 {
		t.Errorf("expected no served countries, got %v", served)
	}

	_, err = catalog.CountriesServedBy("unknown-provider", []pmodel.CountryInfo{
		pmodel.NewCountryInfo("US"),
	})
	if err == nil {
		t.Fatal("expected an error for an unknown provider uuid")
	}
}
