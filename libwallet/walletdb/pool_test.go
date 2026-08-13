package walletdb

import "testing"

// TestPool_NilReceiver guards the regression that motivated the nil checks: after StopServer (or
// before Init) the global pool pointer is nil, and callers still invoke WithDB/Close on it. These
// must return an error / no-op rather than panic on the receiver.
func TestPool_NilReceiver(t *testing.T) {
	var p *Pool // nil, as the global is before Init and after StopServer

	called := false
	err := p.WithDB(func(_ *DB) error {
		called = true
		return nil
	})
	if err == nil {
		t.Fatal("WithDB on a nil pool: expected an error, got nil")
	}
	if called {
		t.Fatal("WithDB on a nil pool: fn must not run")
	}

	// Must not panic.
	p.Close()
}

// TestPool_ClosedReturnsError covers the adjacent case: a live pool whose db has been cleared
// (e.g. Close, or a failed ReplaceDB) reports a distinct error instead of running fn.
func TestPool_ClosedReturnsError(t *testing.T) {
	p := &Pool{} // db == nil

	called := false
	err := p.WithDB(func(_ *DB) error {
		called = true
		return nil
	})
	if err == nil {
		t.Fatal("WithDB on a closed pool: expected an error, got nil")
	}
	if called {
		t.Fatal("WithDB on a closed pool: fn must not run")
	}
}
