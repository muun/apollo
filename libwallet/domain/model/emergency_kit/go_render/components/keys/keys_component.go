package keys

import (
	"strings"
	"time"

	"github.com/muun/libwallet/data/emergency_kit"
	"github.com/muun/libwallet/data/emergency_kit/resources"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/assets"
)

var (
	keyBoxSpacing = resources.Mm(4)
	dateBoxSize   = resources.Mm(56)
)

type KeysComponent struct {
	pdf           *emergency_kit.PdfExtensions
	FirstKeyText  string
	SecondKeyText string
	Translations  *assets.Translations
}

func NewKeysComponent(
	pdf *emergency_kit.PdfExtensions,
	firstKey string,
	secondKey string,
	translations *assets.Translations,
) *KeysComponent {
	return &KeysComponent{
		pdf:           pdf,
		FirstKeyText:  firstKey,
		SecondKeyText: secondKey,
		Translations:  translations,
	}
}

func (r *KeysComponent) Height() float64 {
	header := NewKeysHeaderComponent(r.pdf, r.Translations)
	firstBox := newSingleKeyBox(r.pdf, r.Translations.Keys.FirstKeyLabel, r.FirstKeyText)
	secondBox := newSingleKeyBox(r.pdf, r.Translations.Keys.SecondKeyLabel, r.SecondKeyText)

	return header.Height() + firstBox.Height() + keyBoxSpacing + secondBox.Height() + dateBoxSize
}

func (r *KeysComponent) Render() {
	startY := r.pdf.GetY()

	assets.SetKeysBackgroundColor(r.pdf.Fpdf)
	r.pdf.Rect(
		assets.StandardHorizontalMargin,
		startY,
		r.pdf.GetDrawablePageWidth(),
		r.Height(),
		"F",
	)
	r.pdf.SetY(startY)

	header := NewKeysHeaderComponent(r.pdf, r.Translations)
	header.Render()

	firstBox := newSingleKeyBox(r.pdf, r.Translations.Keys.FirstKeyLabel, r.FirstKeyText)
	firstBox.Render()

	r.pdf.SetY(r.pdf.GetY() + keyBoxSpacing)

	secondBox := newSingleKeyBox(r.pdf, r.Translations.Keys.SecondKeyLabel, r.SecondKeyText)
	secondBox.Render()

	r.renderDate()
}

func (r *KeysComponent) renderDate() {
	startY := r.pdf.GetY()

	assets.SetKeysBackgroundColor(r.pdf.Fpdf)
	r.pdf.Rect(
		assets.StandardHorizontalMargin,
		startY,
		r.pdf.GetDrawablePageWidth(),
		dateBoxSize,
		"F",
	)

	prefix := r.Translations.Keys.CreatedOnPrefix + " "
	date := strings.ToUpper(r.Translations.LocalizedDate(time.Now()))

	// Calculate widths for each styled part
	assets.SetDateLabelFont(r.pdf.Fpdf)
	prefixWidth := r.pdf.GetStringWidthWithLetterSpacing(prefix, letterSpacing)

	// GetStringWidthWithLetterSpacing removes trailing spacing, but we need it between prefix and
	// date
	fontSizePt, _ := r.pdf.GetFontSize()
	fontSizeMm := resources.PtToMm(fontSizePt)
	letterSpacingAmount := fontSizeMm * letterSpacing

	assets.SetDateValueFont(r.pdf.Fpdf)
	dateWidth := r.pdf.GetStringWidthWithLetterSpacing(date, letterSpacing)

	// With center alignment, compensate for first char centering
	firstDateCharWidth := r.pdf.GetStringWidth(string(date[0]))

	// Total width includes the letter spacing between prefix and date, minus centering compensation
	totalWidth := prefixWidth + letterSpacingAmount + dateWidth - firstDateCharWidth/2
	pageWidth, _ := r.pdf.GetPageSize()
	centeredX := (pageWidth - totalWidth) / 2

	assets.SetDateLabelFont(r.pdf.Fpdf)
	assets.SetSecondaryTextColor(r.pdf.Fpdf)
	prefixEndX := r.pdf.RenderTextWithLetterSpacing(
		centeredX,
		startY,
		prefix,
		letterSpacing,
		"C",
		"M",
		dateBoxSize,
	)

	assets.SetDateValueFont(r.pdf.Fpdf)
	assets.SetTitleColor(r.pdf.Fpdf)
	dateStartX := prefixEndX + letterSpacingAmount - firstDateCharWidth/2
	r.pdf.RenderTextWithLetterSpacing(
		dateStartX,
		startY,
		date,
		letterSpacing,
		"C",
		"M",
		dateBoxSize,
	)

	r.pdf.SetY(startY + dateBoxSize)
}
