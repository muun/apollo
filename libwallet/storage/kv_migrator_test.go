package storage

import (
	"errors"
	"path/filepath"
	"testing"

	"github.com/muun/libwallet/walletdb"
)

func TestMigrateValueTypeWithMap_FromIntToString(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wallet.db")

	// V1 of the app: nightMode is an Int
	v1Plan := []Migration{
		Migration{"Initial schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &IntType{}),
		}},
	}
	schemaV1, err := RunKeyValueMigrations(dbPath, v1Plan)
	if err != nil {
		t.Fatalf("V1 migration failed: %v", err)
	}
	storageV1 := NewKeyValueStorage(dbPath, schemaV1)
	if err := storageV1.Save("nightMode", int32(0)); err != nil {
		t.Fatalf("V1 save failed: %v", err)
	}

	// V2 of the app: nightMode becomes a String
	v2Plan := []Migration{
		Migration{"Initial Schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &IntType{}),
		}},
		Migration{"Migrate nightMode to string", []Change{
			MigrateValueTypeWithMap("nightMode", &StringType{}, map[string]string{
				"0": "light",
				"1": "dark",
			}),
		}},
	}
	schemaV2, err := RunKeyValueMigrations(dbPath, v2Plan)
	if err != nil {
		t.Fatalf("V2 migration failed: %v", err)
	}
	storageV2 := NewKeyValueStorage(dbPath, schemaV2)

	// Verify migrated value
	value, err := storageV2.Get("nightMode")
	if err != nil {
		t.Fatalf("V2 get failed: %v", err)
	}

	strValue, ok := value.(string)
	if !ok {
		t.Fatalf("Expected type string, got %T", value)
	}
	if strValue != "light" {
		t.Fatalf("Expected 'light', got %v", value)
	}
}

func TestMigrateValueTypeWithMap_FailsWhenValueIsNotMapped(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wallet.db")

	// V1 of the app: nightMode is a String, and an unexpected value "system" was saved
	v1Plan := []Migration{
		Migration{"Initial schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &StringType{}),
		}},
	}
	schemaV1, err := RunKeyValueMigrations(dbPath, v1Plan)
	if err != nil {
		t.Fatalf("V1 migration failed: %v", err)
	}
	storageV1 := NewKeyValueStorage(dbPath, schemaV1)
	if err := storageV1.Save("nightMode", "system"); err != nil {
		t.Fatalf("V1 save failed: %v", err)
	}

	// V2: nightMode becomes an Int, but the map only covers "light" and "dark", not "system"
	v2Plan := []Migration{
		Migration{"Initial schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &StringType{}),
		}},
		Migration{"Migrate nightMode to int", []Change{
			MigrateValueTypeWithMap("nightMode", &IntType{}, map[string]string{
				"light": "0",
				"dark":  "1",
			}),
		}},
	}
	_, err = RunKeyValueMigrations(dbPath, v2Plan)
	if err == nil {
		t.Fatal("Expected migration to fail due to unmapped value 'system', but it succeeded")
	}
}

func TestMigrateValueTypeWithMap_SucceedsWhenValueIsNull(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wallet.db")

	// V1: Define a key but never set its value, leaving it as NULL.
	v1Plan := []Migration{
		Migration{"Initial schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &IntType{}),
		}},
	}
	if _, err := RunKeyValueMigrations(dbPath, v1Plan); err != nil {
		t.Fatalf("V1 migration failed: %v", err)
	}

	// V2: NULL means absence of value, so the migration should succeed and leave the value as NULL
	v2Plan := []Migration{
		Migration{"Initial schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &IntType{}),
		}},
		Migration{"Migrate nightMode to string", []Change{
			MigrateValueTypeWithMap("nightMode", &StringType{}, map[string]string{
				"0": "light",
				"1": "dark",
			}),
		}},
	}
	schema, err := RunKeyValueMigrations(dbPath, v2Plan)
	if err != nil {
		t.Fatalf("Expected migration to succeed for NULL value, but got: %v", err)
	}

	value, err := NewKeyValueStorage(dbPath, schema).Get("nightMode")
	if err != nil {
		t.Fatalf("Get failed: %v", err)
	}
	if value != nil {
		t.Fatalf("Expected nil value, got %v", value)
	}
}

func TestUpdateAccordingToMap_SucceedsWhenKeyHasNoValue(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wallet.db")

	// V1: Define a key but never set its value, leaving it as NULL.
	v1Plan := []Migration{
		Migration{"Initial schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &StringType{}),
		}},
	}
	if _, err := RunKeyValueMigrations(dbPath, v1Plan); err != nil {
		t.Fatalf("V1 migration failed: %v", err)
	}

	// V2: Update according to a map. Since no value is set, no rows should be affected
	v2Plan := []Migration{
		Migration{"Initial schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &StringType{}),
		}},
		Migration{"Update nightMode according to map", []Change{
			UpdateAccordingToMap("nightMode", map[string]string{
				"light": "0",
				"dark":  "1",
			}),
		}},
	}
	schema, err := RunKeyValueMigrations(dbPath, v2Plan)
	if err != nil {
		t.Fatalf("Expected migration to succeed when key has no value, but got: %v", err)
	}

	// Value should remain NULL.
	value, err := NewKeyValueStorage(dbPath, schema).Get("nightMode")
	if err != nil {
		t.Fatalf("Get failed: %v", err)
	}
	if value != nil {
		t.Fatalf("Expected nil value, got %v", value)
	}
}

func TestRunAllMigrations(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wallet.db")

	// V1 state: Define a schema that existed before all migrations ran
	v1Plan := []Migration{
		Migration{"Initial test schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &IntType{}),
			Define("protocolForReceiving", NoAutoBackup, NotApplicable, false, &StringType{}),
			Define("useTurboChannels", NoAutoBackup, NotApplicable, false, &BoolType{}),
			Define("useTaproot", NoAutoBackup, NotApplicable, false, &BoolType{}),
			Define("useDefaultConfig", NoAutoBackup, NotApplicable, false, &BoolType{}),
			Define("securityCardUsageCount", NoAutoBackup, NotApplicable, false, &IntType{}),
		}},
	}
	schemaV1, err := RunKeyValueMigrations(dbPath, v1Plan)
	if err != nil {
		t.Fatalf("V1 migration failed: %v", err)
	}

	// Save data for initial schema
	storageV1 := NewKeyValueStorage(dbPath, schemaV1)
	storageV1.Save("nightMode", int32(0))
	storageV1.Save("protocolForReceiving", "UNIFIED")
	storageV1.Save("useTurboChannels", true)
	storageV1.Save("useTaproot", false)

	// V2 state: Run the full migration plan
	finalPlan := buildTestMigrationPlan()
	finalSchema, err := RunKeyValueMigrations(dbPath, finalPlan)
	if err != nil {
		t.Fatalf("Final migration failed: %v", err)
	}
	storageFinal := NewKeyValueStorage(dbPath, finalSchema)

	// Assertions
	nightMode, _ := storageFinal.Get("nightMode")
	if nightMode.(string) != "light" {
		t.Fatalf("nightMode=%v, want light", nightMode)
	}
	protocolForReceiving, _ := storageFinal.Get("protocolForReceiving")
	if protocolForReceiving.(string) != "LIGHTNING" {
		t.Fatalf("protocolForReceiving=%v, want LIGHTNING", protocolForReceiving)
	}
	securityCardUsageCount, _ := storageFinal.Get("securityCardUsageCount")
	if securityCardUsageCount.(int32) != 0 {
		t.Fatalf("securityCardUsageCount=%v, want 0", securityCardUsageCount)
	}
	useDefaultConfig, _ := storageFinal.Get("useDefaultConfig")
	if useDefaultConfig.(bool) != true {
		t.Fatalf("useDefaultConfig=%v, want true", useDefaultConfig)
	}
}

func TestEarlyExitMigration(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wallet.db")
	db, _ := walletdb.Open(dbPath)
	defer db.Close()
	schemaStateRepository := db.NewKVSchemaStateRepository()
	keyValueRepository := db.NewKeyValueRepository()

	plan := buildTestMigrationPlan()
	// Set version to max to force an early-exit in the migration
	err := schemaStateRepository.BumpSchemaVersion(len(plan))
	if err != nil {
		t.Fatalf("BumpSchemaVersion failed: %v", err)
	}

	// Set a raw value that would be changed if migrations ran
	err = keyValueRepository.Save("nightMode", strPtr("0"))
	if err != nil {
		t.Fatalf("Save failed: %v", err)
	}

	// When trying to run migrations, it should early-exit without running any migration
	_, err = RunKeyValueMigrations(dbPath, plan)
	if err != nil {
		t.Fatalf("Migration failed: %v", err)
	}

	// Verify the raw value was not changed
	val, _ := keyValueRepository.Get("nightMode")
	if *val != "0" {
		t.Fatalf("Value was changed despite early exit: got %s", *val)
	}
}

func TestMigrationRollback(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wallet.db")

	// V1: Define a key and save an initial value
	v1Plan := []Migration{
		Migration{"Define nightMode", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &StringType{})},
		},
	}
	schemaV1, _ := RunKeyValueMigrations(dbPath, v1Plan)
	storageV1 := NewKeyValueStorage(dbPath, schemaV1)
	err := storageV1.Save("nightMode", "dark")
	if err != nil {
		t.Fatalf("Save failed: %v", err)
	}

	// V2: A failing migration
	v2Plan := []Migration{
		Migration{"Define nightMode", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &StringType{})},
		},
		Migration{"This one will fail", []Change{
			AddCustomChange("failing-change", func(tx LimitedKeyValueRepository) error {
				err := tx.Save("nightMode", strPtr("this_value_should_be_rolled_back"))
				if err != nil {
					return err
				}
				return errors.New("an error running custom change")
			}),
		}},
	}
	finalSchema, err := RunKeyValueMigrations(dbPath, v2Plan)
	if err == nil {
		t.Fatal("Expected an error, but got none")
	}

	// Verify state
	finalStorage := NewKeyValueStorage(dbPath, finalSchema)
	value, _ := finalStorage.Get("nightMode")
	if value.(string) != "dark" {
		t.Fatalf("Expected 'dark', got %v", value)
	}

	db, _ := walletdb.Open(dbPath)
	defer db.Close()
	version, _ := db.NewKVSchemaStateRepository().GetCurrentSchemaVersion()
	if version != 1 {
		t.Fatalf("Expected schema version 1, got %d", version)
	}
}

func TestDefine_PanicsOnDuplicatedKeyDefinitionWithinSameMigration(t *testing.T) {
	defer func() {
		r := recover()
		if r == nil {
			t.Errorf("The code did not panic")
		}
	}()
	plan := []Migration{
		Migration{"Duplicated keys", []Change{
			Define("someKey", NoAutoBackup, NotApplicable, false, &StringType{}),
			Define("someKey", NoAutoBackup, NotApplicable, false, &StringType{}),
		}},
	}
	dbPath := filepath.Join(t.TempDir(), "wallet.db")
	_, _ = RunKeyValueMigrations(dbPath, plan)
}

func TestDefine_PanicsOnDuplicatedKeyDefinitionWhenUsingDifferentMigrations(t *testing.T) {
	defer func() {
		r := recover()
		if r == nil {
			t.Errorf("The code did not panic")
		}
	}()
	plan := []Migration{
		Migration{"Define a key", []Change{
			Define("someKey", NoAutoBackup, NotApplicable, false, &StringType{}),
		}},
		Migration{"Duplicated key", []Change{
			Define("someKey", NoAutoBackup, NotApplicable, false, &StringType{}),
		}},
	}
	dbPath := filepath.Join(t.TempDir(), "wallet.db")
	_, _ = RunKeyValueMigrations(dbPath, plan)
}

func TestRunKeyValueMigrations_FailsOnDuplicatedCustomChangeID_WithinSameMigration(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wallet.db")
	plan := []Migration{
		Migration{"A migration", []Change{
			AddCustomChange("duplicated-id", func(tx LimitedKeyValueRepository) error {
				return nil
			}),
			AddCustomChange("duplicated-id", func(tx LimitedKeyValueRepository) error {
				return nil
			}),
		}},
	}
	_, err := RunKeyValueMigrations(dbPath, plan)
	if err == nil {
		t.Fatal("Expected an error due to duplicate CustomChange ID, but got none")
	}
}

func TestRunKeyValueMigrations_FailsOnDuplicatedCustomChangeID_WhenUsingDifferentMigration(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wallet.db")
	plan := []Migration{
		Migration{"First migration", []Change{
			AddCustomChange("duplicated-id", func(tx LimitedKeyValueRepository) error {
				return nil
			}),
		}},
		Migration{"Second migration", []Change{
			AddCustomChange("duplicated-id", func(tx LimitedKeyValueRepository) error {
				return nil
			}),
		}},
	}
	_, err := RunKeyValueMigrations(dbPath, plan)
	if err == nil {
		t.Fatal("Expected an error due to duplicate CustomChange ID, but got none")
	}
}

func TestDefine_PanicsWhenValueTypeIsNotAPointer(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Errorf("The code did not panic")
		}
	}()
	plan := []Migration{
		Migration{"Value type is not a pointer", []Change{
			Define("someKey", NoAutoBackup, NotApplicable, false, StringType{}),
		}},
	}
	dbPath := filepath.Join(t.TempDir(), "wallet.db")
	_, _ = RunKeyValueMigrations(dbPath, plan)
}

func buildTestMigrationPlan() []Migration {
	return []Migration{
		Migration{"Initial test schema", []Change{
			Define("nightMode", NoAutoBackup, NotApplicable, false, &IntType{}),
			Define("protocolForReceiving", NoAutoBackup, NotApplicable, false, &StringType{}),
			Define("securityCardUsageCount", NoAutoBackup, NotApplicable, false, &IntType{}),
			Define("useTurboChannels", NoAutoBackup, NotApplicable, false, &BoolType{}),
			Define("useTaproot", NoAutoBackup, NotApplicable, false, &BoolType{}),
			Define("useDefaultConfig", NoAutoBackup, NotApplicable, false, &BoolType{}),
		}},
		Migration{"Migrate nightMode and protocol", []Change{
			MigrateValueTypeWithMap("nightMode", &StringType{}, map[string]string{
				// Switch-case via map: "0"->"light", "1"->"dark"
				"0": "light",
				"1": "dark",
			}),
			UpdateAccordingToMap("protocolForReceiving", map[string]string{
				"UNIFIED": "LIGHTNING",
			}),
		}},
		Migration{"Initialize a key and update a second key based on other keys", []Change{
			AddCustomChange("initialize-security-card-usage-count", func(tx LimitedKeyValueRepository) error {
				val, _ := tx.Get("securityCardUsageCount")
				if val == nil {
					return tx.Save("securityCardUsageCount", strPtr("0"))
				}
				return nil
			}),
			AddCustomChange("set-use-default-config", func(tx LimitedKeyValueRepository) error {
				tc, _ := tx.Get("useTurboChannels")
				tr, _ := tx.Get("useTaproot")
				if tc != nil && tr != nil && *tc == "true" && *tr == "false" {
					return tx.Save("useDefaultConfig", strPtr("true"))
				}
				return nil
			}),
		}},
	}
}

func TestMigrateValueType_TrivialConversions(t *testing.T) {
	cases := []struct {
		name         string
		fromType     ValueType
		toType       ValueType
		initialValue any
		wantValue    any
	}{
		{"Int to String", &IntType{}, &StringType{}, int32(91), "91"},
		{"Long to String", &LongType{}, &StringType{}, int64(91), "91"},
		{"Bool to String", &BoolType{}, &StringType{}, true, "true"},
		{"Double to String", &DoubleType{}, &StringType{}, float64(3.14), "3.14"},
		{"Int to Long", &IntType{}, &LongType{}, int32(91), int64(91)},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			dbPath := filepath.Join(t.TempDir(), "wallet.db")

			v1Plan := []Migration{
				Migration{"Initial schema", []Change{
					Define("testKey", NoAutoBackup, NotApplicable, false, tc.fromType),
				}},
			}
			schemaV1, err := RunKeyValueMigrations(dbPath, v1Plan)
			if err != nil {
				t.Fatalf("V1 migration failed: %v", err)
			}
			if err := NewKeyValueStorage(dbPath, schemaV1).Save("testKey", tc.initialValue); err != nil {
				t.Fatalf("V1 save failed: %v", err)
			}

			v2Plan := []Migration{
				Migration{"Initial schema", []Change{
					Define("testKey", NoAutoBackup, NotApplicable, false, tc.fromType),
				}},
				Migration{"Migrate testKey", []Change{
					MigrateValueType("testKey", tc.toType),
				}},
			}
			schemaV2, err := RunKeyValueMigrations(dbPath, v2Plan)
			if err != nil {
				t.Fatalf("V2 migration failed: %v", err)
			}

			value, err := NewKeyValueStorage(dbPath, schemaV2).Get("testKey")
			if err != nil {
				t.Fatalf("Get failed: %v", err)
			}
			if value != tc.wantValue {
				t.Fatalf("got %v (%T), want %v (%T)", value, value, tc.wantValue, tc.wantValue)
			}
		})
	}
}

func TestMigrateValueType_PanicsOnNonTrivialConversions(t *testing.T) {
	cases := []struct {
		name     string
		fromType ValueType
		toType   ValueType
	}{
		{"String to Int", &StringType{}, &IntType{}},
		{"String to Long", &StringType{}, &LongType{}},
		{"String to Bool", &StringType{}, &BoolType{}},
		{"String to Double", &StringType{}, &DoubleType{}},
		{"Long to Int", &LongType{}, &IntType{}},
		{"Bool to Int", &BoolType{}, &IntType{}},
		{"Double to Int", &DoubleType{}, &IntType{}},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			defer func() {
				if r := recover(); r == nil {
					t.Errorf("Expected panic for conversion from %T to %T, but did not panic", tc.fromType, tc.toType)
				}
			}()
			dbPath := filepath.Join(t.TempDir(), "wallet.db")
			plan := []Migration{
				Migration{"Initial schema", []Change{
					Define("testKey", NoAutoBackup, NotApplicable, false, tc.fromType),
				}},
				Migration{"Migrate testKey", []Change{
					MigrateValueType("testKey", tc.toType),
				}},
			}
			_, _ = RunKeyValueMigrations(dbPath, plan)
		})
	}
}

func TestDefine_PanicsOnInvalidBackupSecurityCombination(t *testing.T) {
	cases := []struct {
		name           string
		backupType     BackupType
		backupSecurity BackupSecurity
	}{
		{"AutoBackup with NotApplicable (Sync)", SyncAutoBackup, NotApplicable},
		{"AutoBackup with NotApplicable (Async)", AsyncAutoBackup, NotApplicable},
		{"NoAutoBackup with Plain", NoAutoBackup, Plain},
		{"NoAutoBackup with Authenticated", NoAutoBackup, Authenticated},
		{"NoAutoBackup with Encrypted", NoAutoBackup, Encrypted},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			defer func() {
				if r := recover(); r == nil {
					t.Errorf("Expected panic for combination %v + %v, but did not panic", tc.backupType, tc.backupSecurity)
				}
			}()
			Define("someKey", tc.backupType, tc.backupSecurity, false, &StringType{})
		})
	}
}

// TODO: remove these tests once AutoBackup and SecurityCritical are implemented.
func TestDefine_PanicsOnUnimplementedClassifications(t *testing.T) {
	t.Run("SyncAutoBackup is not yet implemented", func(t *testing.T) {
		defer func() {
			if r := recover(); r == nil {
				t.Errorf("Expected panic for SyncAutoBackup, but did not panic")
			}
		}()
		Define("someKey", SyncAutoBackup, Plain, false, &StringType{})
	})

	t.Run("AsyncAutoBackup is not yet implemented", func(t *testing.T) {
		defer func() {
			if r := recover(); r == nil {
				t.Errorf("Expected panic for AsyncAutoBackup, but did not panic")
			}
		}()
		Define("someKey", AsyncAutoBackup, Plain, false, &StringType{})
	})

	t.Run("SecurityCritical is not yet implemented", func(t *testing.T) {
		defer func() {
			if r := recover(); r == nil {
				t.Errorf("Expected panic for SecurityCritical=true, but did not panic")
			}
		}()
		Define("someKey", NoAutoBackup, NotApplicable, true, &StringType{})
	})
}

func strPtr(s string) *string {
	return &s
}
