package walletdb

import (
	"database/sql"
	"fmt"
	"strings"
	"time"

	"github.com/go-errors/errors"
	"github.com/jinzhu/gorm"
)

type KeyValueRepository interface {
	Create(key string) error
	Save(key string, value *string) error
	Update(key string, newValue string) error
	Get(key string) (*string, error)
	Delete(key string) error
	SaveBatch(items map[string]*string) error
	GetBatch(keys []string) (map[string]*string, error)
	UpdateAccordingToMap(key string, oldToNewMap map[string]string) (int, error)
	IsValueIn(key string, allowedValues []string) (bool, error)
}

type KeyValue struct {
	gorm.Model
	Key   string `gorm:"uniqueIndex"`
	Value *string
}

type keyValueRepository struct {
	withDB gormProvider
}

func (r *keyValueRepository) gorm(fn gormOperation) error {
	return r.withDB(fn)
}

// Create inserts a key with a null value if the key doesn't exist.
func (r *keyValueRepository) Create(key string) error {
	return r.gorm(func(db *gorm.DB) error {
		now := time.Now().UTC()
		query := `
			INSERT INTO key_values (key, created_at, updated_at)
			VALUES (?, ?, ?)
			ON CONFLICT(key) DO NOTHING
		`
		_, err := db.CommonDB().Exec(query, key, now, now)
		return err
	})
}

// Save inserts or updates a key-value into database
func (r *keyValueRepository) Save(key string, value *string) error {
	return r.gorm(func(db *gorm.DB) error {
		now := time.Now().UTC()
		query := `
			INSERT INTO key_values (key, value, created_at, updated_at)
			VALUES (?, ?, ?, ?)
			ON CONFLICT(key) DO UPDATE SET
				value = excluded.value,
				updated_at = excluded.updated_at;
		`
		err := db.Exec(query, key, value, now, now).Error
		if err != nil {
			return errors.Errorf("failed to save or update key-value: %w", err)
		}
		return nil
	})
}

// Update updates the value of a key if it exists.
func (r *keyValueRepository) Update(key string, newValue string) error {
	return r.gorm(func(db *gorm.DB) error {
		now := time.Now().UTC()
		query := `UPDATE key_values SET value=?, updated_at=? WHERE key=?`
		_, err := db.CommonDB().Exec(query, newValue, now, key)
		return err
	})
}

// Get value by key from database
func (r *keyValueRepository) Get(key string) (*string, error) {
	var result *string
	err := r.gorm(func(db *gorm.DB) error {
		var ns sql.NullString
		err := db.Raw("SELECT value FROM key_values WHERE key = ?", key).Row().Scan(&ns)
		if err != nil {
			if errors.Is(err, sql.ErrNoRows) {
				// If key is not found, return nil
				return nil
			}
			return errors.Errorf(
				"failed to fetch from db: %w",
				err,
			)
		}
		if ns.Valid {
			result = &ns.String
		}
		return nil
	})
	return result, err
}

// Delete key-value pair by key
func (r *keyValueRepository) Delete(key string) error {
	return r.gorm(func(db *gorm.DB) error {
		err := db.Exec("DELETE FROM key_values WHERE key = ?", key).Error
		if err != nil {
			return errors.Errorf("failed to delete key-value: %w", err)
		}
		return nil
	})
}

// SaveBatch inserts or updates a map of key-value into database
func (r *keyValueRepository) SaveBatch(items map[string]*string) error {
	if len(items) == 0 {
		return errors.Errorf("no items provided for database insertion")
	}

	now := time.Now().UTC()
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

	return r.gorm(func(db *gorm.DB) error {
		err := db.Exec(query, args...).Error
		if err != nil {
			return errors.Errorf("failed to save batch: %w", err)
		}
		return nil
	})
}

// GetBatch returns a list of values from database given a key list
func (r *keyValueRepository) GetBatch(keys []string) (map[string]*string, error) {
	if len(keys) == 0 {
		return nil, errors.Errorf("no keys provided")
	}

	keyValues := make(map[string]*string)
	for _, key := range keys {
		keyValues[key] = nil
	}

	placeholders := make([]string, len(keys))
	args := make(
		[]interface{}, //nolint:modernize // TODO: use any instead of interface{}
		len(keys),
	)
	for i, key := range keys {
		placeholders[i] = "?"
		args[i] = key
	}

	baseQuery := `SELECT key, value
		FROM key_values WHERE key IN (%s)
	`
	query := fmt.Sprintf(baseQuery, strings.Join(placeholders, ","))

	err := r.gorm(func(db *gorm.DB) error {
		rows, err := db.Raw(query, args...).Rows()
		if err != nil {
			return err
		}
		defer rows.Close() //nolint:errcheck // TODO: check error

		for rows.Next() {
			var key string
			var value sql.NullString

			err := rows.Scan(&key, &value)
			if err != nil {
				return err
			}

			if value.Valid {
				keyValues[key] = &value.String
			}
		}
		return rows.Err()
	})
	if err != nil {
		return nil, err
	}
	return keyValues, nil
}

// UpdateAccordingToMap updates key-values based on a map of old-to-new values.
func (r *keyValueRepository) UpdateAccordingToMap(
	key string,
	oldToNewMap map[string]string,
) (int, error) {
	if len(oldToNewMap) == 0 {
		return 0, nil
	}
	now := time.Now().UTC()
	var queryBuilder strings.Builder
	queryBuilder.WriteString("UPDATE key_values SET value = CASE value ")
	var args []any
	for oldValue, newValue := range oldToNewMap {
		queryBuilder.WriteString("WHEN ? THEN ? ")
		args = append(args, oldValue, newValue)
	}
	queryBuilder.WriteString("ELSE value END, updated_at = ? WHERE key = ?")
	args = append(args, now, key)

	var n int
	err := r.gorm(func(db *gorm.DB) error {
		res, err := db.CommonDB().Exec(queryBuilder.String(), args...)
		if err != nil {
			return err
		}
		rowsAffected, _ := res.RowsAffected()
		n = int(rowsAffected)
		return nil
	})
	return n, err
}

// IsValueIn returns true if the current value of key is in allowedValues.
func (r *keyValueRepository) IsValueIn(key string, allowedValues []string) (bool, error) {
	if len(allowedValues) == 0 {
		return false, nil
	}

	placeholders := make([]string, len(allowedValues))
	args := make([]any, 0, len(allowedValues)+1)
	args = append(args, key)
	for i, v := range allowedValues {
		placeholders[i] = "?"
		args = append(args, v)
	}

	query := fmt.Sprintf(
		`SELECT COUNT(*) FROM key_values WHERE key = ? AND value IN (%s)`,
		strings.Join(placeholders, ","),
	)

	var result bool
	err := r.gorm(func(db *gorm.DB) error {
		var count int
		row := db.CommonDB().QueryRow(query, args...)
		if err := row.Scan(&count); err != nil {
			return err
		}
		result = count > 0
		return nil
	})
	return result, err
}
