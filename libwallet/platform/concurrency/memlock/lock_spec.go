package memlock

import "time"

// LockSpec binds a lock name to the timeout its acquisitions wait, so every caller takes the lock
// with the same pair.
type LockSpec struct {
	name    string
	timeout time.Duration
}

// NewLockSpec returns the spec of the lock named name, whose acquisitions wait up to timeout.
func NewLockSpec(name string, timeout time.Duration) LockSpec {
	return LockSpec{name: name, timeout: timeout}
}
