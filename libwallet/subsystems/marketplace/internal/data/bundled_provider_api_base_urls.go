package data

import (
	libwallet "github.com/muun/libwallet/legacy"
)

// bundledProviderAPIBaseURLs maps each network name to the base URL of every
// bundled provider's Provider API on that network, keyed by provider UUID.
// The apps can't buy from a provider missing from their network.
//
// TODO: placeholder data, like bundledProvidersByUUID. The regtest entries
// point to the card-providers-api the toolkit runs locally, which simulates a
// single provider and is only reachable from an iOS simulator on the same
// machine. Replace them with the real providers' URLs.
var bundledProviderAPIBaseURLs = map[string]map[string]string{
	libwallet.Regtest().Name(): {
		"4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14": "http://localhost:8084",
		"9e2c6b4a-1f8d-473e-b9a5-c3d2e1f4a7b8": "http://localhost:8084",
		"b3f5c1d8-4a72-4e9b-8c61-d3a9e2f4b7c0": "http://localhost:8084",
	},
}

// BundledProviderAPIBaseURLs returns the base URL of every bundled provider's
// Provider API on the given network, keyed by provider UUID.
func BundledProviderAPIBaseURLs(network *libwallet.Network) map[string]string {
	return bundledProviderAPIBaseURLs[network.Name()]
}
