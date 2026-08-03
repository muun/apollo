package components

import (
	"github.com/muun/libwallet/data/emergency_kit"
	"github.com/muun/libwallet/data/emergency_kit/resources"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/assets"
)

type HeaderComponent struct {
	pdf              *emergency_kit.PdfExtensions
	TitleText        string
	VerificationText string
}

func NewHeaderComponent(
	pdf *emergency_kit.PdfExtensions,
	verificationCode string,
	translations *assets.Translations,
) *HeaderComponent {
	return &HeaderComponent{
		pdf:              pdf,
		TitleText:        translations.Header.Title,
		VerificationText: translations.Header.VerificationPrefix + verificationCode,
	}
}

func (r *HeaderComponent) Height() float64 {
	// Height = line height + 20px top padding + 20px bottom padding
	return assets.SectionTitleLineHeight + 2*resources.Mm(20)
}

func (r *HeaderComponent) Render() {
	startY := r.pdf.GetY()
	innerStartX := assets.StandardHorizontalMargin

	componentWidth, _ := r.pdf.GetPageSize()
	componentHeight := r.Height()
	innerWidth := r.pdf.GetDrawablePageWidth()

	r.addBackgroundColor(componentWidth, componentHeight)

	// Render title on the left side
	assets.SetSectionTitleFont(r.pdf.Fpdf)
	assets.SetTitleColor(r.pdf.Fpdf)
	r.pdf.SetXY(innerStartX, startY)
	r.pdf.CellFormat(
		innerWidth,
		componentHeight,
		r.TitleText,
		"",
		2,
		"LM",
		false,
		0,
		"",
	) // LM = Left, Middle

	// Render verification code on the right side
	assets.SetVerificationCodeFont(r.pdf.Fpdf)
	assets.SetSecondaryTextColor(r.pdf.Fpdf)
	r.pdf.SetXY(innerStartX, startY)
	r.pdf.CellFormat(
		innerWidth,
		componentHeight,
		r.VerificationText,
		"",
		2,
		"RM",
		false,
		0,
		"",
	) // RM = Right, Middle

	// Move cursor to end of header
	r.pdf.SetXY(0, startY+componentHeight)
}

func (r *HeaderComponent) addBackgroundColor(componentWidth float64, componentHeight float64) {
	assets.SetHeaderBackgroundColor(r.pdf.Fpdf)
	r.pdf.Rect(0, 0, componentWidth, componentHeight, "F")
}
