package storage_test

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/muun/libwallet/internal/kvmigrationlock"
	"github.com/muun/libwallet/storage"
)

// TestMigrationsLockfileIsUpToDate verifies that no past migration has been modified
// and that all current migrations are reflected in the lockfile.
// If this test fails, run: go generate ./storage/... (from the libwallet directory).
func TestMigrationsLockfileIsUpToDate(t *testing.T) {
	current, err := kvmigrationlock.Generate(storage.BuildKVMigrationPlan(), "kv_migrations.go")
	if err != nil {
		t.Fatalf("failed to generate lockfile from current migrations: %v", err)
	}

	data, err := os.ReadFile("testdata/kv_migrations.lock")
	if err != nil {
		t.Fatalf("failed to read testdata/kv_migrations.lock: %v \nrun: go generate ./storage/... (from libwallet/)", err)
	}

	var committed kvmigrationlock.Lockfile
	if err := json.Unmarshal(data, &committed); err != nil {
		t.Fatalf("failed to parse testdata/kv_migrations.lock: %v", err)
	}

	if len(committed.Migrations) != len(current.Migrations) {
		t.Fatalf(
			"lockfile has %d migrations but plan has %d \nrun: go generate ./storage/... (from libwallet/)",
			len(committed.Migrations),
			len(current.Migrations),
		)
	}

	for i, existing := range committed.Migrations {
		if existing.Hash != current.Migrations[i].Hash {
			t.Fatalf(
				"migration %d ('%s') was modified \nrun: go generate ./storage/... (from libwallet/)",
				i+1,
				existing.Description,
			)
		}
	}
}
