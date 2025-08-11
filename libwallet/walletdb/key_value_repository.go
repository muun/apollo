package walletdb

import (
	"database/sql"
	"errors"
	"fmt"
	"github.com/jinzhu/gorm"
	"strings"
	"time"
)

type KeyValueRepository interface {
	Save(key string, value *string) error
	Get(key string) (*string, error)
	Delete(key string) error
	SaveBatch(items map[string]*string) error
	GetBatch(keys []string) (map[string]*string, error)
}

type KeyValue struct {
	gorm.Model
	Key   string `gorm:"uniqueIndex"`
	Value *string
}

type GORMKeyValueRepository struct {
	db *gorm.DB
}

// Save inserts or updates a key-value into database
func (r *GORMKeyValueRepository) Save(key string, value *string) error {

	now := time.Now()
	query := `
		INSERT INTO key_values (key, value, created_at, updated_at)
		VALUES (?, ?, ?, ?)
		ON CONFLICT(key) DO UPDATE SET 
			value = excluded.value,
			updated_at = excluded.updated_at;
	`
	err := r.db.Exec(query, key, value, now, now).Error
	if err != nil {
		return fmt.Errorf("failed to save or update key-value: %w", err)
	}
	return nil
}

// Get value by key from database
func (r *GORMKeyValueRepository) Get(key string) (*string, error) {
	var ns sql.NullString

	err := r.db.Raw("SELECT value FROM key_values WHERE key = ?", key).Row().Scan(&ns)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			// If key is not found, return nil
			return nil, nil
		}
		return nil, fmt.Errorf("failed to fetch from db: %v", err)
	}

	if ns.Valid {
		return &ns.String, nil
	}
	return nil, nil
}

// Delete key-value pair by key
func (r *GORMKeyValueRepository) Delete(key string) error {

	err := r.db.Exec("DELETE FROM key_values WHERE key = ?", key).Error
	if err != nil {
		return fmt.Errorf("failed to delete key-value: %w", err)
	}
	return nil
}

// SaveBatch inserts or updates a map of key-value into database
func (r *GORMKeyValueRepository) SaveBatch(items map[string]*string) error {
	if len(items) == 0 {
		return fmt.Errorf("no items provided for database insertion")
	}

	now := time.Now()
	placeholders := make([]string, 0, len(items))
	args := make([]any, 0, len(items)*2)
	for key, value := range items {
		placeholders = append(placeholders, "(?, ?, ?, ?)")
		// If value is nil, "SQL NULL" will be inserted
		args = append(args, key, value, now, now)
	}

	baseQuery := `INSERT INTO key_values (key, value, created_at, updated_at)
		VALUES %s
		ON CONFLICT(key) DO UPDATE SET
			value = excluded.value,
			updated_at = excluded.updated_at
	`
	query := fmt.Sprintf(baseQuery, strings.Join(placeholders, ", "))

	err := r.db.Exec(query, args...).Error
	if err != nil {
		return fmt.Errorf("failed to save batch: %w", err)
	}

	return nil
}

// GetBatch returns a list of values from database given a key list
func (r *GORMKeyValueRepository) GetBatch(keys []string) (map[string]*string, error) {
	if len(keys) == 0 {
		return nil, fmt.Errorf("no keys provided")
	}

	keyValues := make(map[string]*string)
	for _, key := range keys {
		keyValues[key] = nil
	}

	placeholders := make([]string, len(keys))
	args := make([]interface{}, len(keys))
	for i, key := range keys {
		placeholders[i] = "?"
		args[i] = key
	}

	baseQuery := `SELECT key, value
		FROM key_values WHERE key IN (%s)
	`
	query := fmt.Sprintf(baseQuery, strings.Join(placeholders, ","))

	rows, err := r.db.Raw(query, args...).Rows()
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var key string
		var value sql.NullString

		err := rows.Scan(&key, &value)
		if err != nil {
			return nil, err
		}

		if value.Valid {
			keyValues[key] = &value.String
		}
	}
	err = rows.Err()
	if err != nil {
		return nil, err
	}
	return keyValues, nil
}
