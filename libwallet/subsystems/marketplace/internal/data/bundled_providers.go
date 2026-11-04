package data

import (
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/provider"
)

// bundledProvidersByUUID is the static registry of marketplace providers
// libwallet ships built in, keyed by the provider UUID houston uses in its
// cards and prices data. Houston entries whose UUID is not in this registry
// are ignored: they belong to providers this app version doesn't know.
// Localized provider data (like the description) lives on the native side.
//
// TODO: the three entries below are placeholder data mirroring the mock
// houston fixture (including identical light/dark themes); replace them with
// the real providers before wiring the real houston service.
var bundledProvidersByUUID = buildBundledProvidersByUUID()

func buildBundledProvidersByUUID() map[string]provider.Provider {
	providers := []provider.Provider{
		provider.NewProvider(
			"4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14",
			"Constellations",
			"https://en.wikipedia.org/wiki/Constellation",
			provider.NewTheme(0xB19B6A, 0x0DB19B6A),
			provider.NewTheme(0xB19B6A, 0x0DB19B6A),
		),
		provider.NewProvider(
			"9e2c6b4a-1f8d-473e-b9a5-c3d2e1f4a7b8",
			"Numbers",
			"https://en.wikipedia.org/wiki/Number",
			provider.NewTheme(0xD9DBDD, 0x0DD9DBDD),
			provider.NewTheme(0xD9DBDD, 0x0DD9DBDD),
		),
		provider.NewProvider(
			"b3f5c1d8-4a72-4e9b-8c61-d3a9e2f4b7c0",
			"Planets",
			"https://en.wikipedia.org/wiki/Planet",
			provider.NewTheme(0x158E5A, 0x0D158E5A),
			provider.NewTheme(0x158E5A, 0x0D158E5A),
		),
	}

	byUUID := make(map[string]provider.Provider, len(providers))
	for _, p := range providers {
		if _, duplicated := byUUID[p.UUID]; duplicated {
			panic(errors.Errorf("duplicated bundled provider %q", p.UUID))
		}
		byUUID[p.UUID] = p
	}
	return byUUID
}
