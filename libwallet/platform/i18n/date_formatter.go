package i18n

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

func FormatDate(t time.Time, lang string) string {
	if lang == "es" {
		year, month, day := t.Date()
		return fmt.Sprintf("%d de %s, %d", day, spanishMonthNames[month-1], year)
	}
	return t.Format("January 2, 2006")
}
