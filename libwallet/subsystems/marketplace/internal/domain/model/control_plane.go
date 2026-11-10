package model

import (
	"slices"
)

// ControlPlane is the marketplace control plane: backend-driven
// levers, sourced from RealTimeData on the native side, that tune what the
// marketplace surfaces. Every lever is a whitelist with fail-closed
// semantics — an empty lever hides everything it governs. The zero value is
// the fully closed control plane. There is no separate providers whitelist:
// a provider is whitelisted iff it has a Listings entry.
type ControlPlane struct {
	Countries []string
	Listings  map[string][]string
	Cards     []string
}

func NewControlPlane(
	countries []string,
	listings map[string][]string,
	cards []string,
) ControlPlane {
	return ControlPlane{
		Countries: countries,
		Listings:  listings,
		Cards:     cards,
	}
}

// AllowsProvider reports whether the provider has any listings entry — the
// provider-level kill-switch: removing a provider's listings entry
// deactivates it everywhere.
func (c ControlPlane) AllowsProvider(providerUUID string) bool {
	_, listed := c.Listings[providerUUID]
	return listed
}

// AllowsCard reports whether the card is whitelisted.
func (c ControlPlane) AllowsCard(cardUUID string) bool {
	return slices.Contains(c.Cards, cardUUID)
}

// ShippableCountriesFor returns the countries where the provider's listings
// may surface: the intersection of the global country whitelist and the
// provider's listing whitelist. Empty when either side is empty (fail
// closed).
func (c ControlPlane) ShippableCountriesFor(
	providerUUID string,
) map[string]bool {
	listed := c.Listings[providerUUID]
	listedSet := make(map[string]bool, len(listed))
	for _, code := range listed {
		listedSet[code] = true
	}

	shippable := make(map[string]bool)
	for _, code := range c.Countries {
		if listedSet[code] {
			shippable[code] = true
		}
	}
	return shippable
}
