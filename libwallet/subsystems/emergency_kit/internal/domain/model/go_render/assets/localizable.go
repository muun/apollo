package assets

import (
	_ "embed"
)

// EnglishJSON holds the embedded English translations.
//
//go:embed localizable/en.json
var EnglishJSON []byte

// SpanishJSON holds the embedded Spanish translations.
//
//go:embed localizable/es.json
var SpanishJSON []byte

type Language string

const (
	English Language = "en"
	Spanish Language = "es"
)

func LoadTranslations(lang Language) (*Translations, error) {
	var data []byte
	if lang == Spanish {
		data = SpanishJSON
	} else {
		data = EnglishJSON
	}
	return NewTranslations(lang, data)
}
