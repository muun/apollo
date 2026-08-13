package storage

import (
	"fmt"
	"reflect"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/walletdb"
)

// Change is the interface that represents a single, atomic modification to the
// database schema or its data.
type Change interface {
	apply(schema map[string]Classification, dbOps *[]func(repo walletdb.KeyValueRepository) error)
}

// KeyDefinition is a Change that introduces a new key to the schema.
type KeyDefinition struct {
	Key              string
	BackupType       BackupType
	BackupSecurity   BackupSecurity
	SecurityCritical bool
	ValueType        ValueType
}

func (c KeyDefinition) apply(
	schema map[string]Classification,
	dbOps *[]func(repo walletdb.KeyValueRepository) error,
) {
	_, exists := schema[c.Key]
	if exists {
		panic(fmt.Sprintf("kv migration: key '%s' is already defined", c.Key))
	}
	schema[c.Key] = Classification{
		BackupType:       c.BackupType,
		BackupSecurity:   c.BackupSecurity,
		SecurityCritical: c.SecurityCritical,
		ValueType:        c.ValueType,
	}
	*dbOps = append(*dbOps, func(repo walletdb.KeyValueRepository) error {
		return repo.Create(c.Key)
	})
}

// Define returns a new KeyDefinition.
func Define(
	key string,
	backupType BackupType,
	backupSecurity BackupSecurity,
	securityCritical bool,
	valueType ValueType,
) Change {
	if reflect.TypeOf(valueType).Kind() != reflect.Ptr {
		panic(fmt.Sprintf(
			"kv migration: ValueType for key '%s' expects a pointer &%s{} but got value %s{}",
			key,
			reflect.TypeOf(valueType).Name(),
			reflect.TypeOf(valueType).Name(),
		))
	}
	if backupType != NoAutoBackup && backupSecurity == NotApplicable {
		panic(fmt.Sprintf(
			"kv migration: key '%s' has auto-backup but BackupSecurity is NotApplicable",
			key,
		))
	}
	if backupType == NoAutoBackup && backupSecurity != NotApplicable {
		panic(fmt.Sprintf(
			"kv migration: key '%s' has NoAutoBackup but BackupSecurity is not NotApplicable",
			key,
		))
	}

	// TODO: remove these validations once AutoBackup and SecurityCritical are implemented
	if backupType != NoAutoBackup {
		panic(fmt.Sprintf(
			"kv migration: key '%s' uses BackupType %v which is not yet implemented",
			key, backupType,
		))
	}
	if securityCritical {
		panic(fmt.Sprintf(
			"kv migration: key '%s' has SecurityCritical=true which is not yet implemented",
			key,
		))
	}
	return KeyDefinition{
		Key:              key,
		BackupType:       backupType,
		BackupSecurity:   backupSecurity,
		SecurityCritical: securityCritical,
		ValueType:        valueType,
	}
}

// TypeMigration is a Change that updates a key's type for trivial conversions.
type TypeMigration struct {
	Key     string
	NewType ValueType
}

func (c TypeMigration) apply(
	schema map[string]Classification,
	dbOps *[]func(repo walletdb.KeyValueRepository) error, //nolint:revive // TODO: use or remove dbOps
) {
	classification, ok := schema[c.Key]
	if !ok {
		panic(fmt.Sprintf(
			"kv migration: attempted to migrate type for "+
				"key '%s' which has not been defined yet",
			c.Key,
		))
	}
	oldType := classification.ValueType
	if !isTrivialConversion(oldType, c.NewType) {
		panic(fmt.Sprintf(
			"kv migration: MigrateValueType cannot be used "+
				"for a non-trivial conversion from %T to %T "+
				"for key '%s'. Use MigrateValueTypeWithMap instead.",
			oldType, c.NewType, c.Key,
		))
	}
	classification.ValueType = c.NewType
	schema[c.Key] = classification
}

// MigrateValueType returns a new TypeMigration.
func MigrateValueType(key string, newType ValueType) Change {
	return TypeMigration{Key: key, NewType: newType}
}

// MappedTypeMigration is a Change that updates a key's type using a map.
type MappedTypeMigration struct {
	Key         string
	NewType     ValueType
	OldToNewMap map[string]string
}

func (c MappedTypeMigration) apply(
	schema map[string]Classification,
	dbOps *[]func(repo walletdb.KeyValueRepository) error,
) {
	classification, ok := schema[c.Key]
	if !ok {
		panic(fmt.Sprintf(
			"kv migration: attempted to migrate type for "+
				"key '%s' which has not been defined yet",
			c.Key,
		))
	}
	classification.ValueType = c.NewType
	schema[c.Key] = classification

	// Extract old values to check for unmapped ones.
	allowedOldValues := make([]string, 0, len(c.OldToNewMap))
	for k := range c.OldToNewMap {
		allowedOldValues = append(allowedOldValues, k)
	}

	// Verify that no unmapped values exist.
	// NULL is allowed, since it means the key was never set (so there is no value to migrate).
	*dbOps = append(*dbOps, func(repo walletdb.KeyValueRepository) error {
		val, err := repo.Get(c.Key)
		if err != nil {
			return err
		}
		if val == nil {
			return nil
		}
		ok, err := repo.IsValueIn(c.Key, allowedOldValues)
		if err != nil {
			return err
		}
		if !ok {
			return errors.Errorf("kv migration: unmapped value found for key '%s'", c.Key)
		}
		return nil
	})

	*dbOps = append(*dbOps, func(repo walletdb.KeyValueRepository) error {
		_, err := repo.UpdateAccordingToMap(c.Key, c.OldToNewMap)
		return err
	})
}

// MigrateValueTypeWithMap returns a new MappedTypeMigration.
// WARNING: Do not use this function in kv_migrations.go yet. Rollback support is not yet designed,
// and these map-based migrations are only reversible if the map is bijective.
// If you need this, please discuss with the Wallet team first.
func MigrateValueTypeWithMap(key string, newType ValueType, oldToNewMap map[string]string) Change {
	return MappedTypeMigration{Key: key, NewType: newType, OldToNewMap: oldToNewMap}
}

// MapUpdate is a Change that updates a key-value pair based on a map.
type MapUpdate struct {
	Key         string
	OldToNewMap map[string]string
}

func (c MapUpdate) apply(
	schema map[string]Classification,
	dbOps *[]func(repo walletdb.KeyValueRepository) error,
) {
	_, exists := schema[c.Key]
	if !exists {
		panic(
			fmt.Sprintf(
				"kv migration: attempted to update key '%s' which has not been defined yet",
				c.Key,
			),
		)
	}

	*dbOps = append(*dbOps, func(repo walletdb.KeyValueRepository) error {
		_, err := repo.UpdateAccordingToMap(c.Key, c.OldToNewMap)
		return err
	})
}

// UpdateAccordingToMap returns a new MapUpdate.
// WARNING: Do not use this function in kv_migrations.go yet. Rollback support is not yet designed,
// and these map-based migrations are only reversible if the map is bijective.
// If you need this, please discuss with the Wallet team first.
func UpdateAccordingToMap(key string, oldToNewMap map[string]string) Change {
	return MapUpdate{Key: key, OldToNewMap: oldToNewMap}
}

// CustomChange is a Change that wraps a custom database operation.
type CustomChange struct {
	ID   string
	Step func(tx LimitedKeyValueRepository) error
}

func (c CustomChange) apply(
	_ map[string]Classification,
	dbOps *[]func(repo walletdb.KeyValueRepository) error,
) {
	*dbOps = append(*dbOps, func(repo walletdb.KeyValueRepository) error {
		// Wrap the full repo in the limited repo before passing it to the custom step.
		limitedRepo := &limitedKeyValueRepository{keyValueRepository: repo}
		return c.Step(limitedRepo)
	})
}

// AddCustomChange returns a new CustomChange. The id must be unique across all migrations.
// WARNING: Do not use this function in kv_migrations.go yet. Rollback support is not yet designed,
// and custom changes are only reversible if the operation itself is. If you need this, please
// discuss with the Wallet team first.
func AddCustomChange(id string, customStep func(tx LimitedKeyValueRepository) error) Change {
	return CustomChange{ID: id, Step: customStep}
}

// isTrivialConversion checks if a type migration is safe to perform without a data mapping.
func isTrivialConversion(from, to ValueType) bool {
	fromType := reflect.TypeOf(from)
	toType := reflect.TypeOf(to)

	// Trivial conversion to String from safe types.
	if toType == reflect.TypeOf(&StringType{}) { //nolint:modernize // TODO: use reflect.TypeFor
		switch fromType {
		case reflect.TypeOf(&IntType{}), //nolint:modernize // TODO: use reflect.TypeFor
			reflect.TypeOf(&LongType{}),   //nolint:modernize // TODO: use reflect.TypeFor
			reflect.TypeOf(&DoubleType{}), //nolint:modernize // TODO: use reflect.TypeFor
			reflect.TypeOf(&BoolType{}):   //nolint:modernize // TODO: use reflect.TypeFor
			return true
		}
	}

	// Trivial conversion from Int to Long.
	if fromType == reflect.TypeOf(&IntType{}) && //nolint:modernize // TODO: use reflect.TypeFor
		toType == reflect.TypeOf(&LongType{}) { //nolint:modernize // TODO: use reflect.TypeFor
		return true
	}

	return false
}

// LimitedKeyValueRepository defines the limited set of database operations available
// within a custom migration change.
type LimitedKeyValueRepository interface {
	Save(key string, value *string) error
	Get(key string) (*string, error)
	Update(key string, newValue string) error
	Delete(key string) error
}

// limitedKeyValueRepository is the internal implementation of LimitedKeyValueRepository.
type limitedKeyValueRepository struct {
	keyValueRepository walletdb.KeyValueRepository
}

func (r *limitedKeyValueRepository) Save(key string, value *string) error {
	return r.keyValueRepository.Save(key, value)
}
func (r *limitedKeyValueRepository) Get(key string) (*string, error) {
	return r.keyValueRepository.Get(key)
}
func (r *limitedKeyValueRepository) Update(key string, newValue string) error {
	return r.keyValueRepository.Update(key, newValue)
}
func (r *limitedKeyValueRepository) Delete(key string) error {
	return r.keyValueRepository.Delete(key)
}
