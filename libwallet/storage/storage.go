package storage

import (
	"fmt"
	"github.com/muun/libwallet/walletdb"
)

type KeyValueStorage struct {
	dataFilePath         string
	keyClassificationMap map[string]Classification
}

func NewKeyValueStorage(dataFilePath string, keyClassificationMap map[string]Classification) *KeyValueStorage {
	return &KeyValueStorage{dataFilePath, keyClassificationMap}
}

// Save or update a value for a key
func (s *KeyValueStorage) Save(key string, value any) error {
	// Find the classification for the provided key
	classification, exists := s.keyClassificationMap[key]
	if !exists {
		return fmt.Errorf("classification not found for key: %s", key)
	}

	// Transform value to string based on the value type
	var ptrStrValue *string = nil
	if value != nil {
		str, err := classification.ValueType.ToString(value)
		if err != nil {
			return fmt.Errorf("failed to convert value to string for key %s: %v", key, err)
		}
		ptrStrValue = &str
	}

	// Insert or update a key-value on local database
	db, err := walletdb.Open(s.dataFilePath)
	if err != nil {
		return err
	}
	defer db.Close()
	err = db.NewKeyValueRepository().Save(key, ptrStrValue)
	if err != nil {
		return err
	}

	return nil
}

// Get value by key
func (s *KeyValueStorage) Get(key string) (any, error) {
	// Find the classification for the provided key
	classification, exists := s.keyClassificationMap[key]
	if !exists {
		return nil, fmt.Errorf("classification not found for key: %s", key)
	}

	// Fetch value by key from local database
	db, err := walletdb.Open(s.dataFilePath)
	if err != nil {
		return nil, err
	}
	defer db.Close()
	ptrStrValue, err := db.NewKeyValueRepository().Get(key)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch from db for key %s: %v", key, err)
	}

	// Parse string to right type based on predefined key classification
	if ptrStrValue == nil {
		return nil, nil
	}
	parsedValue, err := classification.ValueType.FromString(*ptrStrValue)
	if err != nil {
		return nil, fmt.Errorf("failed to convert string to predefined type for key %s: %v", key, err)
	}

	return parsedValue, nil
}

func (s *KeyValueStorage) Delete(key string) error {
	// Check provided key is classified in our schema
	_, exists := s.keyClassificationMap[key]
	if !exists {
		return fmt.Errorf("classification not found for key: %s", key)
	}

	// Remove key-value pair given the key
	db, err := walletdb.Open(s.dataFilePath)
	if err != nil {
		return err
	}
	defer db.Close()
	err = db.NewKeyValueRepository().Delete(key)
	if err != nil {
		return err
	}

	return nil
}

// SaveBatch saves or updates a map of key-values
func (s *KeyValueStorage) SaveBatch(items map[string]any) error {
	// Find the classification for the provided keys
	for key := range items {
		_, exists := s.keyClassificationMap[key]
		if !exists {
			return fmt.Errorf("classification not found for key: %s", key)
		}
	}

	// Transform a map[string]any into a map[string]*string.
	stringItems, err := transformToStringMap(items, s.keyClassificationMap)
	if err != nil {
		return err
	}

	// Insert or update key-values on local database
	db, err := walletdb.Open(s.dataFilePath)
	if err != nil {
		return err
	}
	defer db.Close()
	err = db.NewKeyValueRepository().SaveBatch(stringItems)
	if err != nil {
		return err
	}

	return nil
}

// GetBatch return a map of key-values given a key list
func (s *KeyValueStorage) GetBatch(keys []string) (map[string]any, error) {

	// Find the classification for the provided keys
	for _, key := range keys {
		_, exists := s.keyClassificationMap[key]
		if !exists {
			return nil, fmt.Errorf("classification not found for key: %s", key)
		}
	}

	// Fetch key-values by keys from local database
	db, err := walletdb.Open(s.dataFilePath)
	if err != nil {
		return nil, err
	}
	defer db.Close()
	items, err := db.NewKeyValueRepository().GetBatch(keys)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch from db: %v", err)
	}

	// Parse string value of each key to its corresponding type
	parsedItems, err := parseMap(items, s.keyClassificationMap)
	if err != nil {
		return nil, fmt.Errorf("failed to parse items to right type: %v", err)
	}

	return parsedItems, nil
}

// Transform an 'any' map into a string map using predefined key classifications to determine how to convert each value
func transformToStringMap(items map[string]any, keyClassificationMap map[string]Classification) (map[string]*string, error) {
	stringItems := make(map[string]*string)
	for key, value := range items {
		classification, ok := keyClassificationMap[key]
		if !ok {
			return nil, fmt.Errorf("classification not found for key: %s", key)
		}

		if value == nil {
			stringItems[key] = nil
			continue
		}

		stringValue, err := classification.ValueType.ToString(value)
		if err != nil {
			return nil, fmt.Errorf("failed to convert value to string for key %s: %v", key, err)
		}
		stringItems[key] = &stringValue
	}
	return stringItems, nil
}

// Parse a string map based on predefined key classifications
func parseMap(stringMap map[string]*string, keyClassificationMap map[string]Classification) (map[string]any, error) {
	parsedItems := make(map[string]any)
	for key, ptrStrValue := range stringMap {

		classification, exists := keyClassificationMap[key]
		if !exists {
			return nil, fmt.Errorf("classification not found for key: %s", key)
		}

		if ptrStrValue == nil {
			parsedItems[key] = nil
			continue
		}
		var parsed any

		parsed, err := classification.ValueType.FromString(*ptrStrValue)
		if err != nil {
			return nil, fmt.Errorf("failed to convert string to predefined type for key %s: %v", key, err)
		}

		parsedItems[key] = parsed
	}
	return parsedItems, nil
}
