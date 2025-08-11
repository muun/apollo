package storage

import (
	"path"
	"reflect"
	"strings"
	"testing"
)

func TestGetAndSave(t *testing.T) {

	t.Run("returns error when key is not classified by saving data", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Test saving an invalid key.
		err := keyValueStorage.Save("invalid-key", nil)
		if err == nil {
			t.Fatal("expected error")
		}

		wantErr := "classification not found for key: invalid-key"
		if !strings.Contains(err.Error(), wantErr) {
			t.Fatalf("Save() error = %v, wantErr = %v", err, wantErr)
		}
	})

	t.Run("returns error when key is not classified by reading data", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Test getting an invalid key.
		_, err := keyValueStorage.Get("invalid-key")
		if err == nil {
			t.Fatal("expected error")
		}

		wantErr := "classification not found for key: invalid-key"
		if !strings.Contains(err.Error(), wantErr) {
			t.Fatalf("Get() error = %v, wantErr = %v", err, wantErr)
		}
	})

	t.Run("success when saving a key with nil value", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Try saving a valid key with nil as value.
		err := keyValueStorage.Save("gcmToken", nil)
		if err != nil {
			t.Fatalf("Error saving 'nil' value into db: %v", err)
		}

		// Read the stored value
		got, err := keyValueStorage.Get("gcmToken")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}

		// Verify value is nil
		if got != nil {
			t.Fatalf("Get() = %q, want nil", got)
		}
	})

	t.Run("returns error when key is not classified by deleting data", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Test deleting an invalid key.
		err := keyValueStorage.Delete("invalid-key")
		if err == nil {
			t.Fatal("expected error")
		}

		wantErr := "classification not found for key: invalid-key"
		if !strings.Contains(err.Error(), wantErr) {
			t.Fatalf("Get() error = %v, wantErr = %v", err, wantErr)
		}
	})

	t.Run("success when key is classified appropriately", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Try to save a value with key that exists in the classification for our keys.
		err := keyValueStorage.Save("gcmToken", "abc123")
		if err != nil {
			t.Fatalf("Error saving value into db: %v", err)
		}

		// Try to read a value with key that exists in the classification for our keys.
		_, err = keyValueStorage.Get("gcmToken")
		if err != nil {
			t.Fatalf("Error getting value from db: %v", err)
		}

	})

	t.Run("returns no error when there are no stored values for a key", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Try to get a key for which no one has saved a value before.
		var value any
		value, err := keyValueStorage.Get("gcmToken")
		if err != nil {
			t.Fatalf("Error getting value from db: %v", err)
		}

		// Expect nil
		if value != nil {
			t.Fatalf("Get() = %q, want nil", value)
		}
	})

	t.Run("returns error when value has invalid type", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Test saving a valid key with an invalid schema.
		var invalidTypeForValue = 3.14
		err := keyValueStorage.Save("gcmToken", invalidTypeForValue)

		if err == nil {
			t.Fatal("Parsing should fail")
		}
		wantErr := "StringType: invalid type, expected string"
		if !strings.Contains(err.Error(), wantErr) {
			t.Fatalf("Save() error = %v, wantErr = %v", err, wantErr)
		}

	})

	t.Run("success when value with type Bool can be parsed", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Test saving a valid key with a valid type.
		err := keyValueStorage.Save("isEmailVerified", true)

		if err != nil {
			t.Fatalf("Error saving value into db: %v", err)
		}

	})

	t.Run("success when value with type Int can be parsed", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Test saving a valid key with a valid type.
		err := keyValueStorage.Save("emergencyKitVersion", int32(1234))

		if err != nil {
			t.Fatalf("Error saving value into db: %v", err)
		}

	})

	t.Run("return no error when deleting a key-value pair", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Initially the value of a key is nil
		got, err := keyValueStorage.Get("gcmToken")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}
		if got != nil {
			t.Fatalf("Get() = %q, want nil", got)
		}

		// Save a new value.
		err = keyValueStorage.Save("gcmToken", "abc123")
		if err != nil {
			t.Fatalf("Error saving value into db: %v", err)
		}

		// Read the stored value
		got, err = keyValueStorage.Get("gcmToken")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}

		// Ensure the value is the same that we saved
		wantFirstTime := "abc123"
		if got != wantFirstTime {
			t.Errorf("Save() = %s, want %s", got, wantFirstTime)
		}

		// Delete the key-value pair
		err = keyValueStorage.Delete("gcmToken")
		if err != nil {
			t.Fatalf("error deleting key-value from db: %v", err)
		}

		// Try to read the delete key-value pair
		got, err = keyValueStorage.Get("gcmToken")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}

		// Verify value is nil again
		if got != nil {
			t.Fatalf("Get() = %q, want nil", got)
		}
	})

	t.Run("returns no error when updating a key with a new String value", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Initially the value of a key is nil
		got, err := keyValueStorage.Get("gcmToken")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}
		if got != nil {
			t.Fatalf("Get() = %q, want nil", got)
		}

		// Save a new value.
		err = keyValueStorage.Save("gcmToken", "abc123")
		if err != nil {
			t.Fatalf("Error saving value into db: %v", err)
		}

		// Read the stored value
		got, err = keyValueStorage.Get("gcmToken")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}

		// Ensure the value is the same that we saved
		wantFirstTime := "abc123"
		if got != wantFirstTime {
			t.Errorf("Save() = %s, want %s", got, wantFirstTime)
		}

		// Update the value
		err = keyValueStorage.Save("gcmToken", "xyz789")
		if err != nil {
			t.Fatalf("error saving value into db: %v", err)
		}

		// Read the stored value
		got, err = keyValueStorage.Get("gcmToken")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}

		// Verify key is updated with the new value
		wantSecondTime := "xyz789"
		if got != wantSecondTime {
			t.Errorf("Save() = %s, want %s", got, wantSecondTime)
		}

	})

	t.Run("returns no error when updating a key with a new Int value", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Initially the value of a key is nil
		got, err := keyValueStorage.Get("emergencyKitVersion")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}
		if got != nil {
			t.Fatalf("Get() = %q, want nil", got)
		}

		// Save a new value.
		err = keyValueStorage.Save("emergencyKitVersion", int32(123))
		if err != nil {
			t.Fatalf("Error saving value into db: %v", err)
		}

		// Read the stored value
		got, err = keyValueStorage.Get("emergencyKitVersion")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}

		// Ensure the value is the same that we saved
		wantFirstTime := int32(123)
		if got != wantFirstTime {
			t.Errorf("Save() = %v, want %v", got, wantFirstTime)
		}

		// Update the value
		err = keyValueStorage.Save("emergencyKitVersion", int32(789))
		if err != nil {
			t.Fatalf("error saving value into db: %v", err)
		}

		// Read the stored value
		got, err = keyValueStorage.Get("emergencyKitVersion")
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}

		// Verify key is updated with the new value
		wantSecondTime := int32(789)
		if got != wantSecondTime {
			t.Errorf("Save() = %v, want %v", got, wantSecondTime)
		}

	})

}

func TestGetBatchAndSaveBatch(t *testing.T) {

	t.Run("returns error when any key is not classified when saving batch", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Test saving items with an invalid key.
		var items = make(map[string]any)
		items["primaryCurrency"] = "USD"   // valid key
		items["invalid-key"] = "any value" // invalid key

		err := keyValueStorage.SaveBatch(items)
		if err == nil {
			t.Fatal("expected error")
		}

		wantErr := "classification not found for key: invalid-key"
		if !strings.Contains(err.Error(), wantErr) {
			t.Fatalf("SaveBatch() error = %v, wantErr = %v", err, wantErr)
		}
	})

	t.Run("returns error when any key is not classified when getting batch", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Test getting items where one of the keys is invalid.
		var keys = []string{"primaryCurrency", "invalid-key"}

		_, err := keyValueStorage.GetBatch(keys)
		if err == nil {
			t.Fatal("expected error")
		}

		wantErr := "classification not found for key: invalid-key"
		if !strings.Contains(err.Error(), wantErr) {
			t.Fatalf("GetBatch() error = %v, wantErr = %v", err, wantErr)
		}
	})

	t.Run("returns no error when there are no stored values by getting batch", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Try to get keys for which no one has saved a value before.
		var keys = []string{"primaryCurrency", "isEmailVerified"}

		got, err := keyValueStorage.GetBatch(keys)
		if err != nil {
			t.Fatalf("Error getting value from db: %v", err)
		}

		// Expect nil
		primaryCurrency := got["primaryCurrency"]
		if primaryCurrency != nil {
			t.Fatalf("got %v, want nil", primaryCurrency)
		}

		isEmailVerified := got["isEmailVerified"]
		if isEmailVerified != nil {
			t.Fatalf("got %v, want nil", isEmailVerified)
		}
	})

	t.Run("returns no error when reading, saving and updating batch", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Initially the value of the keys is nil
		var keys = []string{"primaryCurrency", "email", "isEmailVerified", "emergencyKitVersion"}
		items, err := keyValueStorage.GetBatch(keys)
		if err != nil {
			t.Fatalf("Error getting values from db: %v", err)
		}
		// Expect nil for all values
		for k, v := range items {
			if v != nil {
				t.Fatalf("for key %v, got = %v, want nil", k, v)
			}
		}

		// Save batch
		items = make(map[string]any)
		items["primaryCurrency"] = "USD"
		items["email"] = "pepe@test.com"
		items["isEmailVerified"] = true
		items["emergencyKitVersion"] = int32(123)

		err = keyValueStorage.SaveBatch(items)
		if err != nil {
			t.Fatalf("Error saving values into db: %v", err)
		}

		// Read the stored values
		got, err := keyValueStorage.GetBatch(keys)
		if err != nil {
			t.Fatalf("Error reading values from db: %v", err)
		}

		// Ensure the values are the same that we saved
		if !reflect.DeepEqual(got, items) {
			t.Fatalf("GetBatch() = %#v, want %#v", got, items)
		}

		// Update the values
		items["primaryCurrency"] = "ARS"
		items["email"] = nil
		items["isEmailVerified"] = false
		items["emergencyKitVersion"] = int32(789)
		err = keyValueStorage.SaveBatch(items)
		if err != nil {
			t.Fatalf("error saving values into db: %v", err)
		}

		// Read the stored values
		got, err = keyValueStorage.GetBatch(keys)
		if err != nil {
			t.Fatalf("Error reading values from db: %v", err)
		}

		// Verify keys are updated with the new values
		if !reflect.DeepEqual(got, items) {
			t.Fatalf("GetBatch() = %v, want %v", got, items)
		}

	})

	t.Run("returns no error when reading, saving and updating a single item", func(t *testing.T) {
		// Setup
		dataDir := path.Join(t.TempDir(), "test.db")
		keyValueStorage := NewKeyValueStorage(dataDir, buildStorageSchemaForTests())

		// Initially the value of the key is nil
		var keys = []string{"primaryCurrency"}
		items, err := keyValueStorage.GetBatch(keys)
		if err != nil {
			t.Fatalf("Error getting values from db: %v", err)
		}
		// Expect nil
		if items["primaryCurrency"] != nil {
			t.Fatalf("got = %v, want nil", items["primaryCurrency"])
		}

		// Save batch
		items = make(map[string]any)
		items["primaryCurrency"] = "USD"

		err = keyValueStorage.SaveBatch(items)
		if err != nil {
			t.Fatalf("Error saving value into db: %v", err)
		}

		// Read the stored value
		got, err := keyValueStorage.GetBatch(keys)
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}

		// Ensure the value is the same that we saved
		if !reflect.DeepEqual(got, items) {
			t.Fatalf("GetBatch() = %#v, want %#v", got, items)
		}

		// Update the value
		items["primaryCurrency"] = "ARS"
		err = keyValueStorage.SaveBatch(items)
		if err != nil {
			t.Fatalf("Error saving value into db: %v", err)
		}

		// Read the stored value
		got, err = keyValueStorage.GetBatch(keys)
		if err != nil {
			t.Fatalf("Error reading value from db: %v", err)
		}

		// Verify key is updated with the new value
		if !reflect.DeepEqual(got, items) {
			t.Fatalf("GetBatch() = %v, want %v", got, items)
		}

	})

}

func buildStorageSchemaForTests() map[string]Classification {
	return map[string]Classification{
		"email": {
			BackupType: NoAutoBackup, BackupSecurity: NotApplicable, SecurityCritical: false, ValueType: &StringType{},
		},
		"emergencyKitVersion": {
			BackupType: NoAutoBackup, BackupSecurity: NotApplicable, SecurityCritical: false, ValueType: &IntType{},
		},
		"gcmToken": {
			BackupType: NoAutoBackup, BackupSecurity: NotApplicable, SecurityCritical: false, ValueType: &StringType{},
		},
		"isEmailVerified": {
			BackupType: NoAutoBackup, BackupSecurity: NotApplicable, SecurityCritical: false, ValueType: &BoolType{},
		},
		"primaryCurrency": {
			BackupType: NoAutoBackup, BackupSecurity: NotApplicable, SecurityCritical: false, ValueType: &StringType{},
		},
	}
}
