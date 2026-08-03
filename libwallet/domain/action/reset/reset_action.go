package reset

import (
	"os"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/storage"
	"github.com/muun/libwallet/walletdb"
)

type ResetDataAction interface {
	Run() error
}

type resetDataAction struct {
	dbPath        string
	pool          *walletdb.Pool
	migrationPlan []storage.Migration
}

func NewResetDataAction(
	dbPath string,
	pool *walletdb.Pool,
	migrationPlan []storage.Migration,
) ResetDataAction {
	return &resetDataAction{dbPath, pool, migrationPlan}
}

func (a *resetDataAction) Run() error {
	wipeDB := func() (string, error) {
		for _, suffix := range []string{"", "-wal", "-shm"} {
			p := a.dbPath + suffix
			if err := os.Remove(p); err != nil && !os.IsNotExist(err) {
				return "", errors.Errorf("reset: remove %s: %w", p, err)
			}
		}
		return a.dbPath, nil
	}
	runMigrations := func(db *walletdb.DB) error {
		_, err := storage.RunKeyValueMigrations(db, a.migrationPlan)
		if err != nil {
			return errors.Errorf("reset: kv migrations: %w", err)
		}
		return nil
	}
	return a.pool.ReplaceDB(wipeDB, runMigrations)
}
