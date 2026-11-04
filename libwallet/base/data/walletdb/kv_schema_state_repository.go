package walletdb

import (
	"database/sql"
	"errors"
	"time"

	"github.com/jinzhu/gorm"
)

type KVSchemaStateRepository interface {
	GetCurrentSchemaVersion() (int, error)
	BumpSchemaVersion(v int) error
}

type kvSchemaStateRepository struct {
	withDB gormProvider
}

func (r *kvSchemaStateRepository) gorm(fn gormOperation) error {
	return r.withDB(fn)
}

// GetCurrentSchemaVersion returns the latest version from the schema state table.
func (r *kvSchemaStateRepository) GetCurrentSchemaVersion() (int, error) {
	var result int
	err := r.gorm(func(db *gorm.DB) error {
		var v sql.NullInt64
		row := db.CommonDB().QueryRow(`SELECT MAX(schema_version) FROM kv_schema_state`)
		if err := row.Scan(&v); err != nil {
			if errors.Is(err, sql.ErrNoRows) {
				return nil
			}
			return err
		}
		if v.Valid {
			result = int(v.Int64)
		}
		return nil
	})
	return result, err
}

// BumpSchemaVersion inserts a new version into the schema state table.
func (r *kvSchemaStateRepository) BumpSchemaVersion(version int) error {
	return r.gorm(func(db *gorm.DB) error {
		now := time.Now().UTC()
		query := `
            INSERT INTO kv_schema_state (schema_version, applied_at)
            VALUES (?, ?)
        `
		return db.Exec(query, version, now).Error
	})
}
