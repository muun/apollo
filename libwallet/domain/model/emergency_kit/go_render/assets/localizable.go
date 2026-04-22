package assets

import (
	_ "embed"
	"encoding/json"
	"github.com/muun/libwallet/data/emergency_kit/resources"
	"time"
)

//go:embed localizable/en.json
var englishJSON []byte

//go:embed localizable/es.json
var spanishJSON []byte

type Language string

const (
	English Language = "en"
	Spanish Language = "es"
)

// Translations contains all translatable strings for the Emergency Kit PDF.
//
// To add a new translatable keyword:
//  1. Add the new field to the appropriate nested struct below with a json tag
//     Example: NewField string `json:"new_field"`
//  2. Add the corresponding key-value pair to all JSON files in localizable/
//     (en.json, es.json, and any other language files)
//  3. Use the new field in your component:
//     translations.SectionName.NewField
//  4. Run tests to ensure consistency:
//     go test ./emergencykit/go_render/assets -v
//
// To add a new language:
//  1. Create a new JSON file in localizable/ (e.g., localizable/fr.json)
//  2. Add an embed directive at the top of this file:
//     //go:embed localizable/fr.json
//     var frenchJSON []byte
//  3. Add a new Language constant:
//     French Language = "fr"
//  4. Update LoadTranslations() function to handle the new language:
//     } else if lang == French {
//     data = frenchJSON
//  5. Translate all strings from en.json to the new language
//  6. Add the new language to the test arrays in localizable_test.go:
//     - In TestTranslationCompleteness: add {French, frenchJSON}
//     - In TestTranslationStructMatchesJSON: add {French, frenchJSON}
//  7. Run tests to ensure the new language is complete:
//     go test ./emergencykit/go_render/assets -v
type Translations struct {
	Lang Language
	Header struct {
		Title              string `json:"title"`
		VerificationPrefix string `json:"verification_prefix"`
	} `json:"header"`
	Keys struct {
		EncryptedBackupTitle string `json:"encrypted_backup_title"`
		EncryptedBackupDesc1 string `json:"encrypted_backup_desc1"`
		EncryptedBackupDesc2 string `json:"encrypted_backup_desc2"`
		FirstKeyLabel        string `json:"first_key_label"`
		SecondKeyLabel       string `json:"second_key_label"`
		CreatedOnPrefix      string `json:"created_on_prefix"`
	} `json:"keys"`
	Instructions struct {
		Title      string `json:"title"`
		Intro      string `json:"intro"`
		Step1Title string `json:"step1_title"`
		Step1Desc  string `json:"step1_desc"`
		Step2Title string `json:"step2_title"`
		Step2Desc  string `json:"step2_desc"`
		Step3Title string `json:"step3_title"`
		Step3Desc  string `json:"step3_desc"`
	} `json:"instructions"`
	Help struct {
		Title       string `json:"title"`
		Description string `json:"description"`
	} `json:"help"`
	Advanced struct {
		Title    string `json:"title"`
		Subtitle string `json:"subtitle"`
		Intro    string `json:"intro"`
		Closing1 string `json:"closing1"`
		Closing2 string `json:"closing2"`
	} `json:"advanced"`
}

func LoadTranslations(lang Language) (*Translations, error) {
	var data []byte
	if lang == Spanish {
		data = spanishJSON
	} else {
		data = englishJSON
	}

	var t Translations
	err := json.Unmarshal(data, &t)
	if err != nil {
		return nil, err
	}
	t.Lang = lang

	return &t, nil
}

func (t *Translations) LocalizedDate(date time.Time) string {
	return resources.FormatDate(date, string(t.Lang))
}
