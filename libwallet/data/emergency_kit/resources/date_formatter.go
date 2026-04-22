package resources

import (
	"fmt"
	"time"
)

var spanishMonthNames = []string{
	"Enero",
	"Febrero",
	"Marzo",
	"Abril",
	"Mayo",
	"Junio",
	"Julio",
	"Agosto",
	"Septiembre",
	"Octubre",
	"Noviembre",
	"Diciembre",
}

// FormatDate returns a localized date string for the given time and language code.
// Supported languages: "es" (Spanish), defaults to English.
func FormatDate(t time.Time, lang string) string {
	if lang == "es" {
		year, month, day := t.Date()
		return fmt.Sprintf("%d de %s, %d", day, spanishMonthNames[month-1], year)
	}
	return t.Format("January 2, 2006")
}
