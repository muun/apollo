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

type GORMKVSchemaStateRepository struct {
	db *gorm.DB
}

// GetCurrentSchemaVersion returns the latest version from the schema state table.
func (r *GORMKVSchemaStateRepository) GetCurrentSchemaVersion() (int, error) {
	var v sql.NullInt64
	row := r.db.CommonDB().QueryRow(`SELECT MAX(schema_version) FROM kv_schema_state`)
	err := row.Scan(&v)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			// No version recorded yet
			return 0, nil
		}
		return 0, err
	}
	if !v.Valid {
		return 0, nil
	}
	return int(v.Int64), nil
}

// BumpSchemaVersion inserts a new version into the schema state table.
func (r *GORMKVSchemaStateRepository) BumpSchemaVersion(version int) error {
	now := time.Now().UTC()
	query := `
        INSERT INTO kv_schema_state (schema_version, applied_at)
        VALUES (?, ?)
    `
	return r.db.Exec(query, version, now).Error
}
