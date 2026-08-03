package memlock

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"
)

const testTimeout = 1 * time.Minute

func TestLockUnlock(t *testing.T) {
	var locker NamedLocker

	lock, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
	if err != nil {
		t.Fatalf("Acquire returned error: %v", err)
	}
	if locker.len() != 1 {
		t.Fatalf("expected len()==1 while locked, got %d", locker.len())
	}

	lock.Release()

	if locker.len() != 0 {
		t.Fatalf("expected len()==0 after release, got %d", locker.len())
	}
}

func TestLockSameEntity(t *testing.T) {
	var locker NamedLocker

	// Goroutine 1 acquires the lock.
	lock1, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
	if err != nil {
		t.Fatalf("Acquire returned error: %v", err)
	}

	done := make(chan struct{})
	acquired := make(chan struct{})
	go func() {
		// Goroutine 2 blocks until goroutine 1 unlocks.
		lock2, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
		if err != nil {
			t.Errorf("second Acquire returned error: %v", err)
			return
		}
		close(acquired)
		lock2.Release()
		close(done)
	}()

	// Give goroutine 2 time to block.
	select {
	case <-acquired:
		t.Fatal("second goroutine acquired lock while first still held it")
	case <-time.After(50 * time.Millisecond):
	}

	if locker.len() != 1 {
		t.Fatalf("expected len()==1, got %d", locker.len())
	}

	select {
	case <-done:
		t.Fatal("second goroutine done lock while first still held it")
	default:
		// not done
	}

	lock1.Release()

	select {
	case <-acquired:
	case <-time.After(time.Second):
		t.Fatal("second goroutine did not acquire lock after first unlocked")
	}

	// Wait for goroutine 2 to finish releasing before checking len.
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("second goroutine did not finish")
	}

	if locker.len() != 0 {
		t.Fatalf("expected len()==0, got %d", locker.len())
	}
}

func TestLockDifferentEntities(t *testing.T) {
	var locker NamedLocker

	lock1, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
	if err != nil {
		t.Fatalf("Acquire entity-1 returned error: %v", err)
	}

	// Locking a different entity should not block.
	done := make(chan struct{})
	go func() {
		lock2, err := locker.Acquire(context.Background(), "entity-2", testTimeout)
		if err != nil {
			t.Errorf("Acquire entity-2 returned error: %v", err)
			return
		}
		lock2.Release()
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("locking a different entity blocked unexpectedly")
	}

	lock1.Release()
}

func TestLockContextCancellation(t *testing.T) {
	var locker NamedLocker

	lock1, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
	if err != nil {
		t.Fatalf("Acquire returned error: %v", err)
	}

	// Try to lock with an already-cancelled context.
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err = locker.Acquire(ctx, "entity-1", testTimeout)
	if err == nil {
		t.Fatal("expected error from cancelled context, got nil")
	}
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled error, got %v", err)
	}

	// Entry should still exist (first lock is held).
	if locker.len() != 1 {
		t.Fatalf("expected len()==1, got %d", locker.len())
	}

	lock1.Release()

	if locker.len() != 0 {
		t.Fatalf("expected len()==0 after release, got %d", locker.len())
	}
}

func TestLockTimeout(t *testing.T) {
	var locker NamedLocker

	lock1, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
	if err != nil {
		t.Fatalf("Acquire returned error: %v", err)
	}

	_, err = locker.Acquire(context.Background(), "entity-1", 50*time.Millisecond)
	if err == nil {
		t.Fatal("expected timeout error, got nil")
	}
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("expected DeadlineExceeded error, got %v", err)
	}

	lock1.Release()

	if locker.len() != 0 {
		t.Fatalf("expected len()==0 after release, got %d", locker.len())
	}
}

func TestDoubleRelease(t *testing.T) {
	var locker NamedLocker

	lock, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
	if err != nil {
		t.Fatalf("Acquire returned error: %v", err)
	}

	lock.Release()
	lock.Release() // must not panic
}

func TestStress(t *testing.T) {
	const (
		numEntities   = 10
		numGoroutines = 100
		numIterations = 50
	)

	var locker NamedLocker
	counters := make([]int, numEntities)
	var wg sync.WaitGroup

	for i := range numGoroutines {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			entityIdx := id % numEntities
			entityID := string(rune('A' + entityIdx))

			for range numIterations {
				lock, err := locker.Acquire(context.Background(), entityID, testTimeout)
				if err != nil {
					t.Errorf("Acquire returned error: %v", err)
					return
				}
				counters[entityIdx]++
				lock.Release()
			}
		}(i)
	}

	wg.Wait()

	if locker.len() != 0 {
		t.Fatalf("expected len()==0 after all goroutines done, got %d", locker.len())
	}

	// Each entity gets (numGoroutines/numEntities) * numIterations increments.
	expectedPerEntity := (numGoroutines / numEntities) * numIterations
	for i, count := range counters {
		if count != expectedPerEntity {
			t.Errorf("counter[%d] = %d, expected %d", i, count, expectedPerEntity)
		}
	}
}

func TestReuseAfterRelease(t *testing.T) {
	var locker NamedLocker

	for i := range 3 {
		lock, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
		if err != nil {
			t.Fatalf("iteration %d: Acquire returned error: %v", i, err)
		}
		lock.Release()
	}

	if locker.len() != 0 {
		t.Fatalf("expected len()==0 after reuse cycle, got %d", locker.len())
	}
}

func TestMultipleWaiters(t *testing.T) {
	var locker NamedLocker
	const numWaiters = 5

	lock1, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
	if err != nil {
		t.Fatalf("Acquire returned error: %v", err)
	}

	// Start numWaiters goroutines all waiting on the same entity.
	counter := 0
	var wg sync.WaitGroup
	for range numWaiters {
		wg.Add(1)
		go func() {
			defer wg.Done()
			lock, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
			if err != nil {
				t.Errorf("waiter Acquire returned error: %v", err)
				return
			}
			counter++
			lock.Release()
		}()
	}

	// Let all waiters queue up.
	time.Sleep(50 * time.Millisecond)

	lock1.Release()
	wg.Wait()

	if counter != numWaiters {
		t.Fatalf("expected counter==%d, got %d", numWaiters, counter)
	}
	if locker.len() != 0 {
		t.Fatalf("expected len()==0, got %d", locker.len())
	}
}

func TestRunSuccess(t *testing.T) {
	var locker NamedLocker

	err := locker.Run(context.Background(), "entity-1", testTimeout, func() error {
		if locker.len() != 1 {
			t.Fatalf("expected len()==1 while locked, got %d", locker.len())
		}
		return nil
	})
	if err != nil {
		t.Fatalf("Run returned error: %v", err)
	}

	if locker.len() != 0 {
		t.Fatalf("expected len()==0 after release, got %d", locker.len())
	}
}

func TestRunErrorPropagation(t *testing.T) {
	var locker NamedLocker
	sentinel := context.DeadlineExceeded

	err := locker.Run(context.Background(), "entity-1", testTimeout, func() error {
		return sentinel
	})
	if !errors.Is(err, sentinel) {
		t.Fatalf("expected sentinel error, got %v", err)
	}

	if locker.len() != 0 {
		t.Fatalf("expected len()==0 after Run returns, got %d", locker.len())
	}
}

func TestRunTimeout(t *testing.T) {
	var locker NamedLocker

	lock1, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
	if err != nil {
		t.Fatalf("Acquire returned error: %v", err)
	}

	called := false
	err = locker.Run(context.Background(), "entity-1", 50*time.Millisecond, func() error {
		called = true
		return nil
	})
	if err == nil {
		t.Fatal("expected timeout error, got nil")
	}
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("expected DeadlineExceeded error, got %v", err)
	}
	if called {
		t.Fatal("callback should not have been called on timeout")
	}

	lock1.Release()
}

func TestRunCancel(t *testing.T) {
	var locker NamedLocker

	lock1, err := locker.Acquire(context.Background(), "entity-1", testTimeout)
	if err != nil {
		t.Fatalf("Acquire returned error: %v", err)
	}

	// Try to lock with an already-cancelled context.
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	hasRun := false

	err = locker.Run(ctx, "entity-1", testTimeout, func() error {
		hasRun = true
		return nil
	})
	if err == nil {
		t.Fatal("expected error from cancelled context, got nil")
	}
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled error, got %v", err)
	}
	if hasRun {
		t.Fatal("expected cancelled callback to not have been called")
	}

	// Entry should still exist (first lock is held).
	if locker.len() != 1 {
		t.Fatalf("expected len()==1, got %d", locker.len())
	}

	lock1.Release()

	if locker.len() != 0 {
		t.Fatalf("expected len()==0 after release, got %d", locker.len())
	}
}
