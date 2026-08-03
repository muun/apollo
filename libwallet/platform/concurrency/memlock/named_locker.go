package memlock

import (
	"context"
	"sync"
	"time"

	"golang.org/x/sync/semaphore"
)

// NamedLocker provides per-entity mutual exclusion that ensures at most one consumer holds the lock
// at a time for a given string name identifying the entity, while allowing different entities to
// proceed concurrently.
// The zero value is ready to use.
//
// # Architecture
//
// A guard mutex (sync.Mutex) protects a map of per-entity entries, each identified by a lockName.
// Each namedLockEntry holds a semaphore.Weighted(1) for context-aware blocking and a referenceCount
// for cleanup. Entries are created on first Acquire for a specific lockName and
// deleted when no consumer holds or is waiting for that lockName (referenceCount reaches
// zero), so the map does not grow unboundedly.
//
// The guard mutex is only held for map lookups and counter bumps, never while
// blocking on a per-entity semaphore, so contention on it is bounded by the
// cost of those fast operations, not by how long an entity lock is held.
type NamedLocker struct {
	// mutex guards all access to the locks map and to namedLockEntry.referenceCount.
	mutex sync.Mutex
	locks map[string]*namedLockEntry
}

// namedLockEntry is the per-entity state, created on first NamedLocker.Acquire for a lockName
// and deleted when referenceCount drops to zero.
type namedLockEntry struct {
	// semaphore is a weighted semaphore of capacity 1, functioning as a
	// context-aware mutex. Acquire(ctx,1) blocks until the entity is
	// available or the context is cancelled.
	semaphore *semaphore.Weighted

	// referenceCount counts goroutines that hold the semaphore or are blocked in Acquire.
	// When it reaches zero, no goroutine references the namedLockEntry,
	// and it is safe to delete from the map.
	referenceCount int
}

// Acquire acquires the lock for the given lockName, blocking until the lock
// is available, the timeout elapses, or ctx is done. On success, it returns a
// NamedLock that must be released by the caller:
//
//	lock, err := locker.Acquire(ctx, lockName, 5*time.Second)
//	defer lock.Release()
//	if err != nil { return err }
//
// The timeout only bounds the wait to acquire. Once acquired, the returned
// NamedLock is held until Release is called regardless of the timeout.
//
// If ctx is cancelled or the timeout elapses while waiting, Acquire returns
// the context error and cleans up internal state. It is safe to call
// concurrently from any number of goroutines.
func (l *NamedLocker) Acquire(
	ctx context.Context,
	lockName string,
	timeout time.Duration,
) (*NamedLock, error) {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	return l.acquire(ctx, lockName)
}

// Run acquires the lock for the given lockName with a timeout, calls f, and releases the lock when
// f returns. If the lock cannot be acquired, f is not called and the context error is returned.
func (l *NamedLocker) Run(
	ctx context.Context,
	lockName string,
	timeout time.Duration,
	f func() error,
) error {
	lock, err := l.Acquire(ctx, lockName, timeout)
	defer lock.Release()
	if err != nil {
		return err
	}

	return f()
}

// acquire is the internal implementation that blocks on ctx directly without applying its own
// timeout.
func (l *NamedLocker) acquire(ctx context.Context, name string) (*NamedLock, error) {
	entry := l.acquireEntry(name)

	// l.mutex is not held here, so other goroutines can acquire/release different names
	// concurrently.
	if err := entry.semaphore.Acquire(ctx, 1); err != nil {
		l.mutex.Lock()
		defer l.mutex.Unlock()

		l.releaseEntry(name, entry)

		return nil, err
	}

	return &NamedLock{locker: l, name: name}, nil
}

// release releases the per-entity semaphore and decrements the waiter count,
// deleting the namedLockEntry when it reaches zero.
//
// Both namedLockEntry.semaphore.Release and the waiter decrement happen inside the guard mutex
// critical section.
// Release never blocks (it decrements a counter and wakes up a waiter), so the additional hold time
// on mutex is negligible.
// Keeping the entire "release → decrement → conditionally delete" sequence atomic with respect to
// new Acquire calls prevents a window where a new Acquire would create a fresh namedLockEntry for
// the same name while the old semaphore hasn't been released yet.
func (l *NamedLocker) release(name string) {
	l.mutex.Lock()
	defer l.mutex.Unlock()

	entry, ok := l.locks[name]
	if !ok {
		// Matches sync.Mutex convention: unlocking an unheld lock panics.
		panic("memlock.NamedLocker: release of unknown lockName: " + name)
	}

	entry.semaphore.Release(1)

	l.releaseEntry(name, entry)
}

// acquireEntry acquires the namedLockEntry for a specific name.
// If the namedLockEntry doesn't exist yet, it creates it.
// The namedLockEntry.referenceCount of the entry is increased.
func (l *NamedLocker) acquireEntry(name string) *namedLockEntry {
	l.mutex.Lock()
	defer l.mutex.Unlock()

	if l.locks == nil {
		l.locks = make(map[string]*namedLockEntry)
	}

	entry, ok := l.locks[name]
	if !ok {
		entry = &namedLockEntry{semaphore: semaphore.NewWeighted(1)}
		l.locks[name] = entry
	}

	entry.referenceCount++

	return entry
}

// releaseEntry releases the namedLockEntry associated with a specific name.
// The namedLockEntry.referenceCount of the entry is decreased.
// Removes the namedLockEntry if namedLockEntry.referenceCount reaches zero.
// Should always be run under NamedLocker.mutex.
func (l *NamedLocker) releaseEntry(name string, entry *namedLockEntry) {
	entry.referenceCount--
	if entry.referenceCount == 0 {
		delete(l.locks, name)
	}
}

// len returns the number of names currently tracked.
// Intended for testing.
func (l *NamedLocker) len() int {
	l.mutex.Lock()
	defer l.mutex.Unlock()

	return len(l.locks)
}
