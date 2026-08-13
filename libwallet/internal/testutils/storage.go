package testutils

import (
	"path"
	"testing"

	"github.com/muun/libwallet/storage"
	"github.com/muun/libwallet/walletdb"
)

// NewTestKeyValueStorage creates a KeyValueStorage backed by a temp file with the production
// schema.
func NewTestKeyValueStorage(t *testing.T) *storage.KeyValueStorage {
	t.Helper()

	dbPath := path.Join(t.TempDir(), "test.db")
	var schema map[string]storage.Classification
	pool, err := walletdb.NewPool(dbPath, func(db *walletdb.DB) error {
		var migErr error
		schema, migErr = storage.RunKeyValueMigrations(db, storage.BuildKVMigrationPlan())
		return migErr
	})
	if err != nil {
		t.Fatalf("failed to open walletdb: %v", err)
	}
	t.Cleanup(func() { pool.Close() })

	return storage.NewKeyValueStorage(pool.NewKeyValueRepository(), schema)
}
