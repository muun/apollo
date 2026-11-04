package walletdb

import (
	"os"

	"github.com/go-errors/errors"
)

func ResetDB(
	dbPath string,
	pool *Pool,
	runMigrations func(db *DB) error,
) error {
	wipeDB := func() (string, error) {
		for _, suffix := range []string{"", "-wal", "-shm"} {
			p := dbPath + suffix
			if err := os.Remove(p); err != nil && !os.IsNotExist(err) {
				return "", errors.Errorf("reset: remove %s: %w", p, err)
			}
		}
		return dbPath, nil
	}
	return pool.ReplaceDB(wipeDB, runMigrations)
}
