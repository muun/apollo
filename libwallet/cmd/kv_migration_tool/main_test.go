package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/muun/libwallet/internal/kvmigrationlock"
	"github.com/muun/libwallet/storage"
)

var planV1 = []storage.Migration{
	{Description: "Migration 1", Changes: []storage.Change{
		storage.Define("key1", storage.NoAutoBackup, storage.NotApplicable, false, &storage.StringType{}),
	}},
}

var planV2 = []storage.Migration{
	{Description: "Migration 1", Changes: []storage.Change{
		storage.Define("key1", storage.NoAutoBackup, storage.NotApplicable, false, &storage.StringType{}),
	}},
	{Description: "Migration 2", Changes: []storage.Change{
		storage.Define("key2", storage.NoAutoBackup, storage.NotApplicable, false, &storage.StringType{}),
	}},
}

// planV1Modified has the same description as planV1 but different content, so its hash differs.
var planV1Modified = []storage.Migration{
	{Description: "Migration 1", Changes: []storage.Change{
		storage.Define("key1_modified", storage.NoAutoBackup, storage.NotApplicable, false, &storage.StringType{}),
	}},
}

func TestLock(t *testing.T) {

	t.Run("creates lockfile when none exists", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runLock(planV1, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		data, err := os.ReadFile(lockfile)
		if err != nil {
			t.Fatalf("lockfile was not created: %v", err)
		}
		var lf kvmigrationlock.Lockfile
		err = json.Unmarshal(data, &lf)
		if err != nil {
			t.Fatalf("lockfile is not valid JSON: %v", err)
		}
		if len(lf.Migrations) != 1 {
			t.Fatalf("expected 1 migration in lockfile, got %d", len(lf.Migrations))
		}
	})

	t.Run("idempotent when plan is unchanged", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runLock(planV1, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("first lock failed: %v", err)
		}
		err = runLock(planV1, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("second lock failed: %v", err)
		}
	})

	t.Run("succeeds when plan has new migrations", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runLock(planV1, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("v1 lock failed: %v", err)
		}
		err = runLock(planV2, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("v2 lock failed: %v", err)
		}

		data, _ := os.ReadFile(lockfile)
		var lf kvmigrationlock.Lockfile
		json.Unmarshal(data, &lf)
		if len(lf.Migrations) != 2 {
			t.Fatalf("expected 2 migrations in lockfile, got %d", len(lf.Migrations))
		}
	})

	t.Run("fails when migration is modified", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runLock(planV1, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("initial lock failed: %v", err)
		}
		err = runLock(planV1Modified, migrationsFile, lockfile)
		if err == nil {
			t.Fatal("expected error for modified migration, but got none")
		}
	})

	t.Run("fails when migration is deleted", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runLock(planV2, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("initial lock failed: %v", err)
		}
		err = runLock(planV1, migrationsFile, lockfile)
		if err == nil {
			t.Fatal("expected error for deleted migration, but got none")
		}
	})
}

func TestVerify(t *testing.T) {
	t.Run("fails when no lockfile exists", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runVerify(planV1, migrationsFile, lockfile)
		if err == nil {
			t.Fatal("expected error when lockfile does not exist, but got none")
		}
	})

	t.Run("succeeds when lockfile matches plan", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runLock(planV1, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("lock failed: %v", err)
		}
		err = runVerify(planV1, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
	})

	t.Run("fails when plan has unlocked migrations", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runLock(planV1, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("lock failed: %v", err)
		}
		err = runVerify(planV2, migrationsFile, lockfile)
		if err == nil {
			t.Fatal("expected error for unlocked migrations, but got none")
		}
	})

	t.Run("fails when migration is modified", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runLock(planV1, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("lock failed: %v", err)
		}
		err = runVerify(planV1Modified, migrationsFile, lockfile)
		if err == nil {
			t.Fatal("expected error for modified migration, but got none")
		}
	})

	t.Run("fails when migration is deleted", func(t *testing.T) {
		migrationsFile := writeMigrationsFile(t)
		lockfile := newLockfilePath(t)

		err := runLock(planV2, migrationsFile, lockfile)
		if err != nil {
			t.Fatalf("initial lock failed: %v", err)
		}
		err = runVerify(planV1, migrationsFile, lockfile)
		if err == nil {
			t.Fatal("expected error for deleted migration, but got none")
		}
	})
}

func writeMigrationsFile(t *testing.T) string {
	t.Helper()

	// minimalGoFile is a valid Go source used as the migrations file.
	// We need it since kvmigrationlock.Generate parses it to locate AddCustomChange literals.
	// Our test plans only use Define, so any valid Go file suffices.
	var minimalGoFile = "package test\n"

	path := filepath.Join(t.TempDir(), "migrations.go")
	err := os.WriteFile(path, []byte(minimalGoFile), 0644)
	if err != nil {
		t.Fatalf("failed to write migrations file: %v", err)
	}
	return path
}

func newLockfilePath(t *testing.T) string {
	return filepath.Join(t.TempDir(), "kv_migrations.lock")
}
