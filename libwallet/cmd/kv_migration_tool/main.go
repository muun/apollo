package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"

	"github.com/muun/libwallet/internal/kvmigrationlock"
	"github.com/muun/libwallet/storage"
)

// Default paths assume the tool is run from the libwallet directory.
const defaultMigrationsFile = "storage/kv_migrations.go"
const defaultLockfile = "storage/testdata/kv_migrations.lock"

func main() {
	lockCmd := flag.NewFlagSet("lock", flag.ExitOnError)
	lockMigrationsFile := lockCmd.String("migrations-file", defaultMigrationsFile, "path to the migrations Go source file")
	lockLockfile := lockCmd.String("lockfile", defaultLockfile, "path to the lockfile to write")

	verifyCmd := flag.NewFlagSet("verify", flag.ExitOnError)
	verifyMigrationsFile := verifyCmd.String("migrations-file", defaultMigrationsFile, "path to the migrations Go source file")
	verifyLockfile := verifyCmd.String("lockfile", defaultLockfile, "path to the lockfile to read")

	if len(os.Args) < 2 {
		log.Fatalf("Expected 'lock' or 'verify' subcommand.")
	}

	switch os.Args[1] {
	case "lock":
		lockCmd.Parse(os.Args[2:])
		err := runLock(storage.BuildKVMigrationPlan(), *lockMigrationsFile, *lockLockfile)
		if err != nil {
			log.Fatal(err)
		}
	case "verify":
		verifyCmd.Parse(os.Args[2:])
		err := runVerify(storage.BuildKVMigrationPlan(), *verifyMigrationsFile, *verifyLockfile)
		if err != nil {
			log.Fatal(err)
		}
	default:
		log.Fatalf("Expected 'lock' or 'verify' subcommand.")
	}
}

func runLock(plan []storage.Migration, migrationsFile, lockfilePath string) error {
	generatedLockFile, err := kvmigrationlock.Generate(plan, migrationsFile)
	if err != nil {
		return fmt.Errorf("failed to generate lockfile: %w", err)
	}

	// Check if a committed lockfile exists.
	existingData, err := os.ReadFile(lockfilePath)
	if os.IsNotExist(err) {
		// No lockfile yet, nothing to verify.
	} else if err != nil {
		return fmt.Errorf("failed to read existing lockfile: %w", err)
	} else {
		// If a lockfile already exists, verify that no existing migration was modified or deleted.
		// Only appending new migrations is allowed. Rewriting history is not allowed.
		var committedLockfile kvmigrationlock.Lockfile
		err = json.Unmarshal(existingData, &committedLockfile)
		if err != nil {
			return fmt.Errorf("failed to parse existing lockfile: %w", err)
		}
		err = failIfHistoryIsModified(committedLockfile, generatedLockFile)
		if err != nil {
			return err
		}
	}

	data, err := json.MarshalIndent(generatedLockFile, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal lockfile: %w", err)
	}

	err = os.WriteFile(lockfilePath, data, 0644)
	if err != nil {
		return fmt.Errorf("failed to write lockfile: %w", err)
	}
	log.Printf("Successfully wrote %s", lockfilePath)
	return nil
}

func runVerify(plan []storage.Migration, migrationsFile, lockfilePath string) error {
	generatedLockFile, err := kvmigrationlock.Generate(plan, migrationsFile)
	if err != nil {
		return fmt.Errorf("failed to generate lockfile for verification: %w", err)
	}

	existingData, err := os.ReadFile(lockfilePath)
	if err != nil {
		return fmt.Errorf("failed to read lockfile. Run 'lock' first: %w", err)
	}

	var committedLockFile kvmigrationlock.Lockfile
	err = json.Unmarshal(existingData, &committedLockFile)
	if err != nil {
		return fmt.Errorf("failed to parse lockfile: %w", err)
	}

	err = failIfHistoryIsModified(committedLockFile, generatedLockFile)
	if err != nil {
		return err
	}
	if len(committedLockFile.Migrations) < len(generatedLockFile.Migrations) {
		return fmt.Errorf("lockfile has %d migrations but plan has %d, run 'lock' to update",
			len(committedLockFile.Migrations), len(generatedLockFile.Migrations))
	}
	log.Println("OK: all existing migrations are unmodified.")
	return nil
}

// failIfHistoryIsModified checks that no committed migration was modified or deleted.
func failIfHistoryIsModified(committedLockfile kvmigrationlock.Lockfile, generatedLockfile *kvmigrationlock.Lockfile) error {
	if len(committedLockfile.Migrations) > len(generatedLockfile.Migrations) {
		return fmt.Errorf("plan has %d migrations but lockfile has %d: deleting migrations is not allowed",
			len(generatedLockfile.Migrations), len(committedLockfile.Migrations))
	}
	for i, existing := range committedLockfile.Migrations {
		if existing.Hash != generatedLockfile.Migrations[i].Hash {
			return fmt.Errorf("migration %d (%q) was modified", i+1, existing.Description)
		}
	}
	return nil
}
