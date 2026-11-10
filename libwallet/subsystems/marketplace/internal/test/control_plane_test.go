package test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/data/houston/simulator"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/platform/test/teststorage"
	marketplace_data "github.com/muun/libwallet/subsystems/marketplace/internal/data"
	"github.com/muun/libwallet/subsystems/marketplace/internal/domain/action"
	internal_model "github.com/muun/libwallet/subsystems/marketplace/internal/domain/model"
	pmodel "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
)

// controlPlaneStorageKey pins the KV key the repository stores the control
// plane under: renaming it orphans every stored control plane in the wild.
const controlPlaneStorageKey = "securitycards:marketplace:controlplane"

func newControlPlaneTestRepository(
	t *testing.T,
) (marketplace_data.MarketplaceRepository, *storage.KeyValueStorage) {
	t.Helper()
	kv := teststorage.NewTestKeyValueStorage(t)
	return marketplace_data.NewMarketplaceRepository(
		simulator.NewSimulatedHoustonService(nil, nil), kv,
	), kv
}

// The control plane's levers are whitelists with fail-closed semantics, and
// the providers whitelist is derived: a provider is whitelisted iff it has a
// listings entry.
func TestControlPlaneWhitelistSemantics(t *testing.T) {
	controlPlane := internal_model.NewControlPlane(
		[]string{"US", "DE"},
		map[string][]string{"provider-1": {"US", "GB"}},
		[]string{"card-1", "card-2"},
	)

	assert.True(
		t, controlPlane.AllowsProvider("provider-1"),
		"a provider with a listings entry is whitelisted",
	)
	assert.False(
		t, controlPlane.AllowsProvider("provider-2"),
		"a provider with no listings entry is killed",
	)
	assert.True(t, controlPlane.AllowsCard("card-1"))
	assert.False(t, controlPlane.AllowsCard("card-3"))

	shippable := controlPlane.ShippableCountriesFor("provider-1")
	assert.True(
		t, shippable["US"],
		"US is both globally whitelisted and listed for the provider",
	)
	assert.False(
		t, shippable["GB"],
		"GB is listed for the provider but not globally whitelisted",
	)
	assert.False(
		t, shippable["DE"],
		"DE is globally whitelisted but not listed for the provider",
	)
	assert.Empty(t, controlPlane.ShippableCountriesFor("provider-2"))
}

// The zero value is the fully closed control plane.
func TestControlPlaneZeroValueIsClosed(t *testing.T) {
	var controlPlane internal_model.ControlPlane

	assert.False(t, controlPlane.AllowsProvider("any"))
	assert.False(t, controlPlane.AllowsCard("any"))
	assert.Empty(t, controlPlane.ShippableCountriesFor("any"))
}

// Malformed country codes would silently match no country, and duplicated
// ones would surface twice in the country picker: both are reported and
// skipped at store time, without rejecting the rest of the push.
func TestStoredControlPlaneSkipsMalformedAndDuplicatedCountryCodes(
	t *testing.T,
) {
	repository, _ := newControlPlaneTestRepository(t)

	require.NoError(t, action.NewSaveControlPlaneAction(repository).Run(
		pmodel.NewControlPlane(
			[]string{"US", "usa", "u", "", "DE", "US"},
			nil,
			nil,
		),
	))

	countries, err := repository.AvailableCountries()
	require.NoError(t, err)
	assert.Equal(
		t,
		[]pmodel.CountryInfo{
			pmodel.NewCountryInfo("US"),
			pmodel.NewCountryInfo("DE"),
		},
		countries,
	)
}

func TestMarketplaceFailsClosedUntilTheFirstControlPlaneStore(t *testing.T) {
	repository, _ := newControlPlaneTestRepository(t)

	countries, err := repository.AvailableCountries()
	require.NoError(t, err)
	assert.Empty(t, countries)
}

// A control plane stored by a build with a different storage shape (an
// upgrade or downgrade) must read as closed, not an error.
func TestMarketplaceFailsClosedOnUnreadableStoredControlPlane(t *testing.T) {
	cases := []struct {
		name   string
		stored string
	}{
		// Storage's JSONType guarantees a valid JSON document, so the
		// worst stored state is a document of the wrong shape.
		{"envelope of the wrong shape", "[]"},
		{
			"other schema version",
			`{"schemaVersion": 0, "storedAtUnixMillis": 1, "payload": {}}`,
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			repository, kv := newControlPlaneTestRepository(t)
			require.NoError(t, kv.Save(controlPlaneStorageKey, tc.stored))

			countries, err := repository.AvailableCountries()
			require.NoError(t, err)
			assert.Empty(t, countries)
		})
	}
}
