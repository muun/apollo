package data

import (
	"encoding/json"
	"log/slog"
	"sync"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/storage"
)

// refreshFloor is the age a stored payload must reach before refresh
// fetches again: refresh promises data no older than this, not a houston
// round trip. Hammered refreshes (a pull-to-refresh five times in a row)
// collapse into one fetch. Only a successfully stored payload arms the
// floor, so a retry right after a failed refresh still fetches.
const refreshFloor = 30 * time.Second

// cachedEndpoint is one houston marketplace endpoint with its persistent
// TTL cache: get serves the stored payload while fresh and refetches in
// place once it is stale, missing or unreadable. Cache read and write
// problems degrade to fetching; a fetch failure is the only error surfaced.
type cachedEndpoint[T any] struct {
	mu    sync.Mutex
	kv    *storage.KeyValueStorage
	key   string
	ttl   time.Duration
	now   func() time.Time
	fetch func() (T, error)
}

func newCachedEndpoint[T any](
	kv *storage.KeyValueStorage,
	key string,
	ttl time.Duration,
	now func() time.Time,
	fetch func() (T, error),
) *cachedEndpoint[T] {
	// An unclassified key (a missing Define in the KV migration plan) is a
	// programming error that would otherwise be invisible: every read and
	// write on it fails, and the endpoint silently degrades to fetching
	// forever. Fail loudly at construction instead.
	if !kv.IsClassified(key) {
		panic(errors.Errorf(
			"cache key %q is not in the KV migration plan", key,
		))
	}

	return &cachedEndpoint[T]{
		kv:    kv,
		key:   key,
		ttl:   ttl,
		now:   now,
		fetch: fetch,
	}
}

func (c *cachedEndpoint[T]) get() (T, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if payload, fresh := c.freshPayload(c.ttl); fresh {
		return payload, nil
	}

	// The lock is held across the fetch on purpose: concurrent callers wait
	// for one houston round trip instead of racing their own.
	// TODO: once the real houston client replaces the panic stubs, give the
	// fetch a context/timeout — a hanging request would otherwise block
	// every marketplace screen behind this lock.
	payload, err := c.fetch()
	if err != nil {
		var zero T
		return zero, err
	}
	// A storage failure doesn't discard a successful fetch: the fresh
	// payload is served anyway.
	if err := c.store(payload); err != nil {
		slog.Warn(
			"Failed to write marketplace cache",
			slog.String("key", c.key),
			slog.Any("error", err),
		)
	}
	return payload, nil
}

// refresh bypasses the TTL check: it fetches and stores unless the stored
// payload is younger than refreshFloor. Any failure is returned to the
// caller and the previously stored payload is kept, so a later get within
// the TTL still serves it. Unlike get, a store failure is an error too:
// updating the cache is refresh's whole job, and a successful ack over a
// kept stale payload would lie to the caller.
func (c *cachedEndpoint[T]) refresh() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if _, fresh := c.freshPayload(refreshFloor); fresh {
		return nil
	}

	payload, err := c.fetch()
	if err != nil {
		return err
	}
	if err := c.store(payload); err != nil {
		return errors.Errorf("store refreshed payload: %w", err)
	}
	return nil
}

// freshPayload loads the cached payload, reporting whether it exists and is
// younger than maxAge. Any unreadable stored state is a cache miss, never
// an error: the caller refetches and overwrites it. The caller must hold
// c.mu.
func (c *cachedEndpoint[T]) freshPayload(maxAge time.Duration) (T, bool) {
	var payload T

	value, err := c.kv.Get(c.key)
	if err != nil {
		slog.Warn(
			"Failed to read marketplace cache",
			slog.String("key", c.key),
			slog.Any("error", err),
		)
		return payload, false
	}
	if value == nil {
		return payload, false
	}
	envelopeJSON, isString := value.(string)
	if !isString {
		slog.Warn(
			"Marketplace cache holds a non-string value",
			slog.String("key", c.key),
		)
		return payload, false
	}

	var envelope cacheEnvelope
	if err := json.Unmarshal([]byte(envelopeJSON), &envelope); err != nil {
		slog.Warn(
			"Marketplace cache envelope is unreadable",
			slog.String("key", c.key),
			slog.Any("error", err),
		)
		return payload, false
	}
	// A payload written by a build with a different DTO shape would decode
	// with silently zeroed fields: expected once after an upgrade, so a
	// plain miss rather than a warn.
	if envelope.SchemaVersion != cacheSchemaVersion {
		return payload, false
	}
	fetchedAt := time.UnixMilli(envelope.FetchedAtUnixMillis)
	// A negative elapsed means fetchedAt is in the future: the device clock
	// moved backwards since the write, so the entry's age is unknowable and
	// it must count as stale rather than fresh until the clock catches up.
	if elapsed := c.now().Sub(fetchedAt); elapsed < 0 || elapsed > maxAge {
		return payload, false
	}
	if err := json.Unmarshal(envelope.Payload, &payload); err != nil {
		slog.Warn(
			"Marketplace cache payload is unreadable",
			slog.String("key", c.key),
			slog.Any("error", err),
		)
		var zero T
		return zero, false
	}
	return payload, true
}

// store writes the payload into the KV storage wrapped in a fresh envelope.
// The caller must hold c.mu.
func (c *cachedEndpoint[T]) store(payload T) error {
	payloadJSON, err := json.Marshal(payload)
	if err != nil {
		return errors.Errorf("marshal cache payload: %w", err)
	}
	envelopeJSON, err := json.Marshal(cacheEnvelope{
		SchemaVersion:       cacheSchemaVersion,
		FetchedAtUnixMillis: c.now().UnixMilli(),
		Payload:             payloadJSON,
	})
	if err != nil {
		return errors.Errorf("marshal cache envelope: %w", err)
	}
	return c.kv.Save(c.key, string(envelopeJSON))
}
