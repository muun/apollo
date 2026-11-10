package walletdb

import (
	"log/slog"
	"sync"

	"github.com/go-errors/errors"
)

type Pool struct {
	poolMutex sync.RWMutex
	db        *DB
}

// NewPool opens a new database at path, calls setup(db) if non-nil, and returns a Pool.
func NewPool(path string, setup func(*DB) error) (*Pool, error) {
	db, err := open(path)
	if err != nil {
		return nil, err
	}
	if setup != nil {
		if err := setup(db); err != nil {
			db.Close()
			return nil, err
		}
	}
	return &Pool{db: db}, nil
}

// WithDB runs fn under a shared read lock.
// A nil pool returns an error instead of panicking on the receiver.
func (p *Pool) WithDB(fn func(*DB) error) error {
	if p == nil {
		return errors.New("pool: not initialized")
	}
	p.poolMutex.RLock()
	defer p.poolMutex.RUnlock()
	if p.db == nil {
		return errors.New("pool: database is closed")
	}
	return fn(p.db)
}

// ReplaceDB closes the current DB, runs fn under the exclusive write lock to get the path for the
// replacement DB, then opens it and runs setup if non-nil. p.db is set to nil on any error so
// subsequent reads get a clean error.
func (p *Pool) ReplaceDB(fn func() (string, error), setup func(*DB) error) error {
	p.poolMutex.Lock()
	defer p.poolMutex.Unlock()
	if p.db != nil {
		p.db.Close()
		p.db = nil
	}
	path, err := fn()
	if err != nil {
		slog.Error("pool: replace failed, db unavailable until restart",
			"step", "wipe", "error", err)
		return err
	}
	newDB, err := open(path)
	if err != nil {
		slog.Error("pool: replace failed, db unavailable until restart",
			"step", "open", "error", err)
		return err
	}
	if setup != nil {
		if err := setup(newDB); err != nil {
			newDB.Close()
			slog.Error("pool: replace failed, db unavailable until restart",
				"step", "setup", "error", err)
			return err
		}
	}
	p.db = newDB
	return nil
}

func (p *Pool) NewKeyValueRepository() KeyValueRepository {
	return &keyValueRepository{
		withDB: func(fn gormOperation) error {
			return p.WithDB(func(db *DB) error { return fn(db.Gorm()) })
		},
	}
}

// Close closes the DB and nils the pointer under the exclusive write lock. A nil pool is a no-op.
func (p *Pool) Close() {
	if p == nil {
		return
	}
	p.poolMutex.Lock()
	defer p.poolMutex.Unlock()
	if p.db != nil {
		p.db.Close()
		p.db = nil
	}
}
