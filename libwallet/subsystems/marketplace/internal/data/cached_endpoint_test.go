package data

import (
	"fmt"
	"testing"
	"time"

	"github.com/go-errors/errors"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/platform/test/teststorage"
)

type testCachePayload struct {
	Value string `json:"value"`
}

// cachedEndpointFixture is a cachedEndpoint over the real storage schema
// with a controllable clock and a counting, failable fetch. Each successful
// fetch returns a distinct payload ("fetched-1", "fetched-2", ...) so tests
// can tell a cached value from a refetched one.
type cachedEndpointFixture struct {
	endpoint   *cachedEndpoint[testCachePayload]
	now        time.Time
	fetchCount int
	fetchErr   error
}

func newCachedEndpointFixture(t *testing.T) *cachedEndpointFixture {
	t.Helper()
	return newCachedEndpointFixtureOver(t, teststorage.NewTestKeyValueStorage(t))
}

func newCachedEndpointFixtureOver(
	t *testing.T,
	kv *storage.KeyValueStorage,
) *cachedEndpointFixture {
	t.Helper()

	f := &cachedEndpointFixture{now: time.Unix(1_000_000, 0)}
	f.endpoint = newCachedEndpoint(
		kv,
		cardsCacheKey,
		time.Hour,
		func() time.Time { return f.now },
		func() (testCachePayload, error) {
			f.fetchCount++
			if f.fetchErr != nil {
				return testCachePayload{}, f.fetchErr
			}
			return testCachePayload{
				Value: fmt.Sprintf("fetched-%d", f.fetchCount),
			}, nil
		},
	)
	return f
}

// An unclassified key would silently disable caching (every read and write
// fails, degrading to a permanent refetch): construction must fail loudly.
func TestNewCachedEndpointPanicsOnUnclassifiedKey(t *testing.T) {
	kv := teststorage.NewTestKeyValueStorage(t)

	require.Panics(t, func() {
		newCachedEndpoint(
			kv,
			"not-a-registered-key",
			time.Hour,
			time.Now,
			func() (testCachePayload, error) {
				return testCachePayload{}, nil
			},
		)
	})
}

// The cache is persistent: a payload stored by one endpoint instance is
// served by a later instance over the same storage — the process-restart
// case the KV cache exists for, and the only reader of the stored envelope
// shape that isn't the object that wrote it.
func TestCachedEndpointPersistsAcrossInstances(t *testing.T) {
	kv := teststorage.NewTestKeyValueStorage(t)

	first := newCachedEndpointFixtureOver(t, kv)
	_, err := first.endpoint.get()
	require.NoError(t, err)

	second := newCachedEndpointFixtureOver(t, kv)
	payload, err := second.endpoint.get()
	require.NoError(t, err)
	assert.Equal(
		t, 0, second.fetchCount,
		"a fresh persisted payload should serve without fetching",
	)
	assert.Equal(t, "fetched-1", payload.Value)
}

func TestCachedEndpointServesFreshCacheWithoutFetching(t *testing.T) {
	f := newCachedEndpointFixture(t)

	_, err := f.endpoint.get()
	require.NoError(t, err)
	require.Equal(t, 1, f.fetchCount, "a cold cache should fetch once")

	f.now = f.now.Add(30 * time.Minute)
	payload, err := f.endpoint.get()
	require.NoError(t, err)
	assert.Equal(t, 1, f.fetchCount, "a fresh cache should serve without fetching")
	assert.Equal(t, "fetched-1", payload.Value)
}

func TestCachedEndpointRefetchesOnceStale(t *testing.T) {
	f := newCachedEndpointFixture(t)

	_, err := f.endpoint.get()
	require.NoError(t, err)

	f.now = f.now.Add(2 * time.Hour)
	payload, err := f.endpoint.get()
	require.NoError(t, err)
	assert.Equal(t, 2, f.fetchCount, "a stale cache should refetch")
	assert.Equal(t, "fetched-2", payload.Value)
}

func TestCachedEndpointTreatsFutureFetchTimeAsStale(t *testing.T) {
	f := newCachedEndpointFixture(t)

	_, err := f.endpoint.get()
	require.NoError(t, err)

	// The device clock moves backwards after the write (NTP sync, manual
	// change): the stored fetchedAt is now in the future.
	f.now = f.now.Add(-time.Minute)
	_, err = f.endpoint.get()
	require.NoError(t, err)
	assert.Equal(t, 2, f.fetchCount, "a future fetchedAt should count as stale")
}

func TestCachedEndpointMissAndFetchFailureIsAnError(t *testing.T) {
	f := newCachedEndpointFixture(t)
	f.fetchErr = errors.Errorf("houston is down")

	_, err := f.endpoint.get()
	require.Error(t, err, "a cache miss with a failing fetch should error")
}

func TestCachedEndpointStaleAndFetchFailureIsAnError(t *testing.T) {
	f := newCachedEndpointFixture(t)

	_, err := f.endpoint.get()
	require.NoError(t, err)

	f.now = f.now.Add(2 * time.Hour)
	f.fetchErr = errors.Errorf("houston is down")
	_, err = f.endpoint.get()
	require.Error(t, err, "a stale refetch never serves the old payload")
}

func TestCachedEndpointRefreshBypassesFreshCacheAndStores(t *testing.T) {
	f := newCachedEndpointFixture(t)

	_, err := f.endpoint.get()
	require.NoError(t, err)

	f.now = f.now.Add(refreshFloor + time.Second)
	require.NoError(t, f.endpoint.refresh())
	require.Equal(
		t, 2, f.fetchCount, "refresh should fetch despite a fresh cache",
	)

	payload, err := f.endpoint.get()
	require.NoError(t, err)
	assert.Equal(t, 2, f.fetchCount, "get should serve what refresh stored")
	assert.Equal(t, "fetched-2", payload.Value)
}

// Hammered refreshes collapse into one fetch: refresh promises data no older
// than the floor, not a houston round trip per call.
func TestCachedEndpointRefreshFloorSkipsRecentPayload(t *testing.T) {
	f := newCachedEndpointFixture(t)

	require.NoError(t, f.endpoint.refresh())
	require.Equal(t, 1, f.fetchCount)

	require.NoError(t, f.endpoint.refresh())
	assert.Equal(
		t, 1, f.fetchCount,
		"a refresh within the floor should serve the stored payload",
	)

	f.now = f.now.Add(refreshFloor + time.Second)
	require.NoError(t, f.endpoint.refresh())
	assert.Equal(
		t, 2, f.fetchCount, "a refresh past the floor should fetch",
	)
}

// Only a successfully stored payload arms the floor: a retry right after a
// failed refresh must fetch again, not bounce off the floor.
func TestCachedEndpointRefreshAfterFailureRetriesImmediately(t *testing.T) {
	f := newCachedEndpointFixture(t)

	f.fetchErr = errors.Errorf("houston is down")
	require.Error(t, f.endpoint.refresh())
	require.Equal(t, 1, f.fetchCount)

	f.fetchErr = nil
	require.NoError(t, f.endpoint.refresh())
	assert.Equal(
		t, 2, f.fetchCount,
		"a retry after a failed refresh should fetch immediately",
	)
}

func TestCachedEndpointRefreshFailureIsAnErrorAndKeepsTheCache(t *testing.T) {
	f := newCachedEndpointFixture(t)

	_, err := f.endpoint.get()
	require.NoError(t, err)

	f.now = f.now.Add(refreshFloor + time.Second)
	f.fetchErr = errors.Errorf("houston is down")
	require.Error(t, f.endpoint.refresh())

	f.fetchErr = nil
	payload, err := f.endpoint.get()
	require.NoError(t, err)
	assert.Equal(
		t, 2, f.fetchCount,
		"a failed refresh should leave the stored payload in place",
	)
	assert.Equal(t, "fetched-1", payload.Value)
}

func TestCachedEndpointTreatsUnreadableCacheAsMiss(t *testing.T) {
	f := newCachedEndpointFixture(t)

	// Valid JSON that is not a cacheEnvelope object.
	require.NoError(t, f.endpoint.kv.Save(cardsCacheKey, "[]"))

	payload, err := f.endpoint.get()
	require.NoError(t, err)
	assert.Equal(t, 1, f.fetchCount, "an unreadable cache should refetch")
	assert.Equal(t, "fetched-1", payload.Value)
}

// A payload written under a different schema version (an app upgrade that
// changed a DTO's shape) must count as a miss, never be served with silently
// zeroed fields.
func TestCachedEndpointTreatsOtherSchemaVersionsAsMiss(t *testing.T) {
	f := newCachedEndpointFixture(t)

	staleSchema := `{"schemaVersion":0,` +
		`"fetchedAtUnixMillis":1000000000,"payload":{"value":"old"}}`
	require.NoError(t, f.endpoint.kv.Save(cardsCacheKey, staleSchema))

	payload, err := f.endpoint.get()
	require.NoError(t, err)
	assert.Equal(t, 1, f.fetchCount, "an old schema version should refetch")
	assert.Equal(t, "fetched-1", payload.Value)
}
