package storage

import (
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/walletdb"
)

// Migration is a collection of Changes that are executed together
// within a single database transaction.
type Migration struct {
	Description string
	Changes     []Change
}

// run executes all Changes in the migration within a single transaction.
func (m *Migration) run(tx walletdb.KeyValueRepository, schema map[string]Classification) error {

	var dbOperations []func(repository walletdb.KeyValueRepository) error

	// Generate all DB operations by applying the changes to the schema.
	for _, change := range m.Changes {
		change.apply(schema, &dbOperations)
	}

	// Execute all generated operations within the transaction.
	for _, dbOperation := range dbOperations {
		err := dbOperation(tx)
		if err != nil {
			return err
		}
	}

	return nil
}

// RunKeyValueMigrations executes the entire migration plan, returning the final schema.
func RunKeyValueMigrations(
	db *walletdb.DB,
	migrations []Migration,
) (map[string]Classification, error) {

	// Validate that all CustomChange IDs are unique across all migrations.
	seenIDs := make(map[string]bool)
	for _, migration := range migrations {
		for _, change := range migration.Changes {
			if cc, ok := change.(CustomChange); ok {
				if seenIDs[cc.ID] {
					return nil, errors.Errorf(
						"duplicate custom change id '%s' in migration '%s'",
						cc.ID,
						migration.Description,
					)
				}
				seenIDs[cc.ID] = true
			}
		}
	}

	// Build the final schema in memory by running all changes without DB operations.
	// This is done upfront to ensure we can return a valid schema even if DB operations fail.
	finalSchema := make(map[string]Classification)
	var discardedDbOperations []func(walletdb.KeyValueRepository) error //nolint:staticcheck // TODO: var discardedDbOperations should be discardedDBOperations
	for i := range migrations {
		for _, change := range migrations[i].Changes {
			change.apply(finalSchema, &discardedDbOperations)
		}
	}

	// Get current schema version.
	schemaStateRepository := db.NewKVSchemaStateRepository()
	currentVersion, err := schemaStateRepository.GetCurrentSchemaVersion()
	if err != nil {
		return finalSchema, errors.Errorf("failed to get current schema version: %w", err)
	}

	// Early exit if no new migrations are needed.
	if currentVersion >= len(migrations) {
		return finalSchema, nil
	}

	// Re-build the schema state up to the current version before starting migrations.
	currentSchema := make(map[string]Classification)
	for i := 0; i < currentVersion; i++ { //nolint:modernize // TODO: use range over int
		for _, change := range migrations[i].Changes {
			change.apply(currentSchema, &discardedDbOperations)
		}
	}

	// Execute only the new migrations, each in its own transaction.
	for i := currentVersion; i < len(migrations); i++ {
		migration := &migrations[i]
		targetVersion := i + 1

		err = db.WithTx(func(txDB *walletdb.DB) error {
			if err := migration.run(txDB.NewKeyValueRepository(), currentSchema); err != nil {
				return errors.Errorf(
					"migration %d (%s) failed: %w",
					targetVersion,
					migration.Description,
					err,
				)
			}
			schemaRepo := txDB.NewKVSchemaStateRepository()
			if err := schemaRepo.BumpSchemaVersion(targetVersion); err != nil {
				return errors.Errorf(
					"failed to bump schema version to %d: %w",
					targetVersion,
					err,
				)
			}
			return nil
		})
		if err != nil {
			return finalSchema, err
		}
	}

	return finalSchema, nil
}
