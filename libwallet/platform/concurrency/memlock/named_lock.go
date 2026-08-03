package memlock

import "sync"

// NamedLock represents a held entity lock from NamedLocker. It is safe to call Release on a nil
// reference or multiple times; only the first non-nil call has any effect.
type NamedLock struct {
	locker *NamedLocker
	name   string
	once   sync.Once
}

// Release releases the entity lock. Only the first non-nil call has any effect.
func (u *NamedLock) Release() {
	if u == nil {
		// Lock was never taken
		return
	}

	u.once.Do(func() {
		u.locker.release(u.name)
	})
}
