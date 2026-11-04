package test

import (
	"encoding/json"
	"reflect"
	"testing"

	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/model/go_render/assets"
)

// TestTranslationStructMatchesJSON ensures the Translations struct matches
// the JSON structure exactly - no missing or extra fields.
func TestTranslationStructMatchesJSON(t *testing.T) {
	languages := []struct {
		name assets.Language
		data []byte
	}{
		{assets.English, assets.EnglishJSON},
		{assets.Spanish, assets.SpanishJSON},
	}

	for _, lang := range languages {
		t.Run(string(lang.name), func(t *testing.T) {
			// Parse JSON into a generic map
			var jsonMap map[string]any
			err := json.Unmarshal(lang.data, &jsonMap)
			if err != nil {
				t.Fatalf("Failed to unmarshal %s JSON: %v", lang.name, err)
			}

			// Validate structure matches
			validateJSONStructMatch(
				t,
				string(lang.name),
				"",
				jsonMap,
				reflect.TypeFor[assets.Translations](),
			)
		})
	}
}

// validateJSONStructMatch checks that JSON keys match struct fields
func validateJSONStructMatch(
	t *testing.T,
	lang string,
	path string,
	jsonMap map[string]any,
	structType reflect.Type,
) {
	if structType.Kind() != reflect.Struct {
		return
	}

	// Build a map of json tags to field names
	jsonTagToField := make(map[string]string)
	for i := 0; i < structType.NumField(); i++ {
		field := structType.Field(i)
		jsonTag := field.Tag.Get("json")
		if jsonTag != "" {
			jsonTagToField[jsonTag] = field.Name
		}
	}

	// Check all JSON keys have corresponding struct fields
	for jsonKey, jsonValue := range jsonMap {
		fullPath := path + "." + jsonKey
		if path == "" {
			fullPath = jsonKey
		}

		fieldName, exists := jsonTagToField[jsonKey]
		if !exists {
			t.Errorf("[%s] JSON key '%s' has no corresponding struct field", lang, fullPath)
			continue
		}

		// Get the struct field
		field, found := structType.FieldByName(fieldName)
		if !found {
			continue
		}

		// If it's a nested object, recurse
		if nestedMap, ok := jsonValue.(map[string]any); ok {
			validateJSONStructMatch(t, lang, fullPath, nestedMap, field.Type)
		}
	}

	// Check all struct fields have corresponding JSON keys
	for jsonTag, fieldName := range jsonTagToField {
		_, exists := jsonMap[jsonTag]
		if !exists {
			fullPath := path + "." + jsonTag
			if path == "" {
				fullPath = jsonTag
			}
			t.Errorf(
				"[%s] Struct field '%s' (json:\"%s\") has no corresponding JSON key at %s",
				lang,
				fieldName,
				jsonTag,
				fullPath,
			)
		}
	}
}
