package test

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/data/houston/simulator"
	"github.com/muun/libwallet/platform/test/teststorage"
	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	"github.com/muun/libwallet/subsystems/marketplace/internal/domain/action"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/card"
)

// The round trip pins the selection contract across the marketplace screens
// using the simulated Houston data as fixture: every card listed for a country
// can be fetched as an offer for that same country, consistently.
func TestMarketplaceListToDetailRoundTrip(t *testing.T) {
	repository := marketplace_data.NewMarketplaceRepository(
		simulator.NewSimulatedHoustonService(nil, nil),
		teststorage.NewTestKeyValueStorage(t),
	)
	require.NoError(t, action.NewSaveControlPlaneAction(repository).Run(
		PermissiveControlPlane(),
	))
	fetchCountries := action.NewFetchAvailableCountriesAction(repository)
	fetchListings := action.NewFetchProviderListingsByCountryAction(repository)
	fetchOffer := action.NewFetchCardOfferAction(repository)

	countries, err := fetchCountries.Run()
	if err != nil {
		t.Fatalf("fetching countries: %v", err)
	}
	if len(countries) == 0 {
		t.Fatal("expected available countries")
	}

	listedSomewhere := false
	listedMetalCard := false
	for _, country := range countries {
		listings, err := fetchListings.Run(country)
		if err != nil {
			t.Fatalf("fetching listings for %q: %v", country.Code, err)
		}

		for _, listing := range listings {
			listedSomewhere = true
			for _, listedCard := range listing.Cards {
				offer, err := fetchOffer.Run(country, listedCard.UUID)
				if err != nil {
					t.Fatalf(
						"fetching offer for card %q in %q: %v",
						listedCard.UUID, country.Code, err,
					)
				}

				if offer.Provider.UUID != listing.Provider.UUID {
					t.Errorf(
						"card %q: offer provider %q != listing provider %q",
						listedCard.UUID,
						offer.Provider.UUID,
						listing.Provider.UUID,
					)
				}
				if offer.Card.UUID != listedCard.UUID {
					t.Errorf(
						"expected offered card %q, got %q",
						listedCard.UUID, offer.Card.UUID,
					)
				}
				if offer.ShippingCountry.Code != country.Code {
					t.Errorf(
						"card %q: expected shipping country %q, got %q",
						listedCard.UUID,
						country.Code,
						offer.ShippingCountry.Code,
					)
				}
				if offer.CardPrice.InSat <= 0 {
					t.Errorf(
						"card %q: expected a positive sats card price, got %d",
						listedCard.UUID, offer.CardPrice.InSat,
					)
				}
				if offer.CardPrice.InPrimaryCurrency.Currency != "USD" {
					t.Errorf(
						"card %q: expected a USD primary price, got %q",
						listedCard.UUID,
						offer.CardPrice.InPrimaryCurrency.Currency,
					)
				}
				if offer.Card.Material != listedCard.Material {
					t.Errorf(
						"card %q: offer material %v != listing material %v",
						listedCard.UUID,
						offer.Card.Material,
						listedCard.Material,
					)
				}
				if listedCard.Material == card.MaterialMetal {
					listedMetalCard = true
				}
			}
		}
	}

	if !listedSomewhere {
		t.Fatal(
			"mock data has no provider listing in any available country",
		)
	}
	if !listedMetalCard {
		t.Fatal("mock data has no metal card in any provider listing")
	}
}

func TestMarketplaceOfferForUnknownCard(t *testing.T) {
	repository := marketplace_data.NewMarketplaceRepository(
		simulator.NewSimulatedHoustonService(nil, nil),
		teststorage.NewTestKeyValueStorage(t),
	)
	require.NoError(t, action.NewSaveControlPlaneAction(repository).Run(
		PermissiveControlPlane(),
	))
	fetchCountries := action.NewFetchAvailableCountriesAction(repository)
	fetchOffer := action.NewFetchCardOfferAction(repository)

	countries, err := fetchCountries.Run()
	if err != nil {
		t.Fatalf("fetching countries: %v", err)
	}

	_, err = fetchOffer.Run(countries[0], "unknown-card-uuid")
	if err == nil {
		t.Fatal("expected an error for an unknown card uuid")
	}
}

// Filtering the available countries by provider must agree with the
// per-country listings: a country is in a provider's filtered list exactly
// when that provider is listed for the country.
func TestAvailableCountriesFilteredByProvider(t *testing.T) {
	repository := marketplace_data.NewMarketplaceRepository(
		simulator.NewSimulatedHoustonService(nil, nil),
		teststorage.NewTestKeyValueStorage(t),
	)
	require.NoError(t, action.NewSaveControlPlaneAction(repository).Run(
		PermissiveControlPlane(),
	))
	fetchCountries := action.NewFetchAvailableCountriesAction(repository)
	fetchCountriesByProvider := action.NewFetchAvailableCountriesByProviderAction(
		repository,
	)
	fetchListings := action.NewFetchProviderListingsByCountryAction(repository)

	allCountries, err := fetchCountries.Run()
	if err != nil {
		t.Fatalf("fetching countries: %v", err)
	}

	countriesByProvider := make(map[string]map[string]bool)
	for _, country := range allCountries {
		listings, err := fetchListings.Run(country)
		if err != nil {
			t.Fatalf("fetching listings for %q: %v", country.Code, err)
		}
		for _, listing := range listings {
			providerUUID := listing.Provider.UUID
			if countriesByProvider[providerUUID] == nil {
				countriesByProvider[providerUUID] = make(map[string]bool)
			}
			countriesByProvider[providerUUID][country.Code] = true
		}
	}
	if len(countriesByProvider) == 0 {
		t.Fatal("mock data has no provider listing in any available country")
	}

	for providerUUID, expected := range countriesByProvider {
		filtered, err := fetchCountriesByProvider.Run(providerUUID)
		if err != nil {
			t.Fatalf(
				"fetching countries for provider %q: %v",
				providerUUID, err,
			)
		}

		if len(filtered) != len(expected) {
			t.Errorf(
				"provider %q: expected %d countries, got %d",
				providerUUID, len(expected), len(filtered),
			)
		}
		for _, country := range filtered {
			if !expected[country.Code] {
				t.Errorf(
					"provider %q: country %q filtered in, but the provider is not listed there",
					providerUUID, country.Code,
				)
			}
		}
	}
}

func TestAvailableCountriesForUnknownProvider(t *testing.T) {
	repository := marketplace_data.NewMarketplaceRepository(
		simulator.NewSimulatedHoustonService(nil, nil),
		teststorage.NewTestKeyValueStorage(t),
	)
	require.NoError(t, action.NewSaveControlPlaneAction(repository).Run(
		PermissiveControlPlane(),
	))
	fetchCountriesByProvider := action.NewFetchAvailableCountriesByProviderAction(
		repository,
	)

	_, err := fetchCountriesByProvider.Run("unknown-provider-uuid")
	if err == nil {
		t.Fatal("expected an error for an unknown provider uuid")
	}
}

// A provider with more than one listings entry has conflicting country
// lists: the save drops the provider rather than guess, so it disappears
// like a killed one.
func TestDuplicatedControlPlaneListingsDropTheProvider(t *testing.T) {
	const dupUUID = "4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14"

	duplicated := PermissiveControlPlane()
	duplicated.Listings = append(
		duplicated.Listings,
		pmodel.NewControlPlaneListing(dupUUID, []string{"US"}),
	)

	repository := marketplace_data.NewMarketplaceRepository(
		simulator.NewSimulatedHoustonService(nil, nil),
		teststorage.NewTestKeyValueStorage(t),
	)
	require.NoError(
		t, action.NewSaveControlPlaneAction(repository).Run(duplicated),
	)
	fetchListings := action.NewFetchProviderListingsByCountryAction(repository)

	for _, code := range []string{"US", "DE", "ES", "GB", "BR"} {
		listings, err := fetchListings.Run(pmodel.NewCountryInfo(code))
		require.NoError(t, err)
		for _, listing := range listings {
			require.NotEqual(
				t, dupUUID, listing.Provider.UUID,
				"a provider with duplicated listings should be dropped",
			)
		}
	}
}

// The control plane's provider kill-switch: a provider whose listings entry
// is removed disappears from every endpoint, whatever houston serves for it.
func TestControlPlaneKillSwitchHidesProvider(t *testing.T) {
	const killedUUID = "4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14"

	// The permissive dev-stub minus the killed provider: a provider is
	// deactivated by removing its listings entry.
	narrowed := PermissiveControlPlane()
	listings := make([]pmodel.ControlPlaneListing, 0, len(narrowed.Listings))
	for _, listing := range narrowed.Listings {
		if listing.ProviderUUID != killedUUID {
			listings = append(listings, listing)
		}
	}
	narrowed.Listings = listings

	repository := marketplace_data.NewMarketplaceRepository(
		simulator.NewSimulatedHoustonService(nil, nil),
		teststorage.NewTestKeyValueStorage(t),
	)
	require.NoError(
		t, action.NewSaveControlPlaneAction(repository).Run(narrowed),
	)
	fetchCountries := action.NewFetchAvailableCountriesAction(repository)
	fetchListings := action.NewFetchProviderListingsByCountryAction(repository)
	fetchCountriesByProvider := action.NewFetchAvailableCountriesByProviderAction(
		repository,
	)

	countries, err := fetchCountries.Run()
	if err != nil {
		t.Fatalf("fetching countries: %v", err)
	}
	sawOtherProviders := false
	for _, country := range countries {
		listings, err := fetchListings.Run(country)
		if err != nil {
			t.Fatalf("fetching listings for %q: %v", country.Code, err)
		}
		for _, listing := range listings {
			if listing.Provider.UUID == killedUUID {
				t.Fatalf(
					"killed provider %q listed in %q", killedUUID, country.Code,
				)
			}
			sawOtherProviders = true
		}
	}
	if !sawOtherProviders {
		t.Fatal("expected the surviving providers to keep their listings")
	}

	if _, err := fetchCountriesByProvider.Run(killedUUID); err == nil {
		t.Fatal("expected an error for a kill-switched provider")
	}
}
