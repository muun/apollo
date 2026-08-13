package storage

import (
	"strings"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/walletdb"
)

type KeyValueStorage struct {
	repo                 walletdb.KeyValueRepository
	keyClassificationMap map[string]Classification
}

func NewKeyValueStorage(
	repo walletdb.KeyValueRepository,
	keyClassificationMap map[string]Classification,
) *KeyValueStorage {
	return &KeyValueStorage{repo, keyClassificationMap}
}

// Save or update a value for a key
func (s *KeyValueStorage) Save(key string, value any) error {
	// Find the classification for the provided key
	classification, exists := s.keyClassificationMap[key]
	if !exists {
		return errors.Errorf("classification not found for key: %s", key)
	}

	// Transform value to string based on the value type
	var ptrStrValue *string = nil
	if value != nil {
		str, err := classification.ValueType.ToString(value)
		if err != nil {
			return errors.Errorf(
				"failed to convert value to string for key %s: %w",
				key,
				err,
			)
		}
		ptrStrValue = &str
	}

	// Insert or update a key-value on local database
	return s.repo.Save(key, ptrStrValue)
}

// Get value by key
func (s *KeyValueStorage) Get(key string) (any, error) {
	// Find the classification for the provided key
	classification, exists := s.keyClassificationMap[key]
	if !exists {
		return nil, errors.Errorf("classification not found for key: %s", key)
	}

	// Fetch value by key from local database
	ptrStrValue, err := s.repo.Get(key)
	if err != nil {
		return nil, errors.Errorf(
			"failed to fetch from db for key %s: %w",
			key,
			err,
		)
	}

	// Parse string to right type based on predefined key classification
	if ptrStrValue == nil {
		return nil, nil
	}
	parsedValue, err := classification.ValueType.FromString(*ptrStrValue)
	if err != nil {
		return nil, errors.Errorf(
			"failed to convert string to predefined type for key %s: %w",
			key,
			err,
		)
	}

	return parsedValue, nil
}

func (s *KeyValueStorage) Delete(key string) error {
	// Check provided key is classified in our schema
	_, exists := s.keyClassificationMap[key]
	if !exists {
		return errors.Errorf("classification not found for key: %s", key)
	}

	// Remove key-value pair given the key
	return s.repo.Delete(key)
}

// SaveBatch saves or updates a map of key-values
func (s *KeyValueStorage) SaveBatch(items map[string]any) error {
	// Find the classification for the provided keys
	for key := range items {
		_, exists := s.keyClassificationMap[key]
		if !exists {
			return errors.Errorf("classification not found for key: %s", key)
		}
	}

	// Transform a map[string]any into a map[string]*string.
	stringItems, err := transformToStringMap(items, s.keyClassificationMap)
	if err != nil {
		return err
	}

	// Insert or update key-values on local database
	return s.repo.SaveBatch(stringItems)
}

// GetBatch return a map of key-values given a key list
func (s *KeyValueStorage) GetBatch(keys []string) (map[string]any, error) {

	// Find the classification for the provided keys
	for _, key := range keys {
		_, exists := s.keyClassificationMap[key]
		if !exists {
			return nil, errors.Errorf("classification not found for key: %s", key)
		}
	}

	// Fetch key-values by keys from local database
	items, err := s.repo.GetBatch(keys)
	if err != nil {
		return nil, errors.Errorf(
			"failed to fetch from db: %w",
			err,
		)
	}

	// Parse string value of each key to its corresponding type
	parsedItems, err := parseMap(items, s.keyClassificationMap)
	if err != nil {
		return nil, errors.Errorf(
			"failed to parse items to right type: %w",
			err,
		)
	}

	return parsedItems, nil
}

// GetByPrefix returns a map of key-values where keys match the given prefix
func (s *KeyValueStorage) GetByPrefix(prefix string) (map[string]any, error) {
	var matchingKeys []string
	for key := range s.keyClassificationMap {
		if strings.HasPrefix(key, prefix) {
			matchingKeys = append(matchingKeys, key)
		}
	}

	if len(matchingKeys) == 0 {
		return make(map[string]any), nil
	}

	return s.GetBatch(matchingKeys)
}

// Transform an 'any' map into a string map using predefined key classifications to determine how to
// convert each value
func transformToStringMap(
	items map[string]any,
	keyClassificationMap map[string]Classification,
) (map[string]*string, error) {
	stringItems := make(map[string]*string)
	for key, value := range items {
		classification, ok := keyClassificationMap[key]
		if !ok {
			return nil, errors.Errorf("classification not found for key: %s", key)
		}

		if value == nil {
			stringItems[key] = nil
			continue
		}

		stringValue, err := classification.ValueType.ToString(value)
		if err != nil {
			return nil, errors.Errorf(
				"failed to convert value to string for key %s: %w",
				key,
				err,
			)
		}
		stringItems[key] = &stringValue
	}
	return stringItems, nil
}

// Parse a string map based on predefined key classifications
func parseMap(
	stringMap map[string]*string,
	keyClassificationMap map[string]Classification,
) (map[string]any, error) {
	parsedItems := make(map[string]any)
	for key, ptrStrValue := range stringMap {

		classification, exists := keyClassificationMap[key]
		if !exists {
			continue
		}

		if ptrStrValue == nil {
			parsedItems[key] = nil
			continue
		}
		var parsed any

		parsed, err := classification.ValueType.FromString(*ptrStrValue)
		if err != nil {
			return nil, errors.Errorf(
				"failed to convert string to predefined type for key %s: %w",
				key,
				err,
			)
		}

		parsedItems[key] = parsed
	}
	return parsedItems, nil
}
