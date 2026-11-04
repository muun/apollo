package data

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/data/houston/simulator"
	"github.com/muun/libwallet/platform/test/teststorage"
)

// RefreshCards and RefreshPrices are near-identical, so a crossed wire
// (either method refreshing the other endpoint) would be invisible at
// runtime. This pins the routing: each method writes its own cache key and
// leaves the other untouched.
func TestRefreshMethodsHitTheirOwnEndpoint(t *testing.T) {
	kv := teststorage.NewTestKeyValueStorage(t)
	repository := NewMarketplaceRepository(
		simulator.NewSimulatedHoustonService(nil, nil),
		kv,
	)

	require.NoError(t, repository.RefreshCards())
	cardsValue, err := kv.Get(cardsCacheKey)
	require.NoError(t, err)
	assert.NotNil(t, cardsValue, "RefreshCards should write the cards key")
	pricesValue, err := kv.Get(pricesCacheKey)
	require.NoError(t, err)
	assert.Nil(t, pricesValue, "RefreshCards should not touch the prices key")

	require.NoError(t, repository.RefreshPrices())
	pricesValue, err = kv.Get(pricesCacheKey)
	require.NoError(t, err)
	assert.NotNil(t, pricesValue, "RefreshPrices should write the prices key")
}
