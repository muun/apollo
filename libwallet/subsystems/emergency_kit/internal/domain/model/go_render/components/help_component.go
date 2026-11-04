package components

import (
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/data"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/data/resources"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/model/go_render/assets"
)

var (
	helpSectionMarginTop = resources.Mm(40)
	helpPaddingVertical  = resources.Mm(32)
	helpIconMarginLeft   = resources.Mm(8)
	helpIconMarginRight  = resources.Mm(8)
)

type HelpComponent struct {
	pdf         *data.PdfExtensions
	Title       string
	Description string
}

func NewHelpComponent(
	pdf *data.PdfExtensions,
	translations *assets.Translations,
) *HelpComponent {
	return &HelpComponent{
		pdf:         pdf,
		Title:       translations.Help.Title,
		Description: translations.Help.Description,
	}
}

func (r *HelpComponent) Height() float64 {
	pageWidth, _ := r.pdf.GetPageSize()
	textWidth := pageWidth -
		helpIconMarginLeft -
		assets.HelpIconSize -
		helpIconMarginRight -
		assets.StandardHorizontalMargin

	assets.SetBodyParagraphFont(r.pdf.Fpdf)
	descLines := r.pdf.LineCountWithLetterSpacing(
		textWidth,
		r.Description,
		assets.BodyLetterSpacing,
	)

	textContentHeight := assets.SubtitleLineHeight +
		assets.IntraComponentSpacing +
		descLines*assets.BodyParagraphLineHeight

	return helpSectionMarginTop + helpPaddingVertical + textContentHeight
}

func (r *HelpComponent) Render() {
	startY := r.pdf.GetY()
	pageWidth, _ := r.pdf.GetPageSize()
	height := r.Height()

	assets.SetBlueLightAltBackgroundColor(r.pdf.Fpdf)
	r.pdf.Rect(0, startY, pageWidth, height, "F")

	iconX := helpIconMarginLeft
	iconY := startY + helpPaddingVertical
	r.pdf.DrawIcon(assets.HelpImageName, iconX, iconY, assets.HelpIconSize)

	textX := helpIconMarginLeft + assets.HelpIconSize + helpIconMarginRight
	textY := startY + helpPaddingVertical
	textWidth := pageWidth - textX - assets.StandardHorizontalMargin

	assets.SetSubtitleFont(r.pdf.Fpdf)
	assets.SetTitleColor(r.pdf.Fpdf)
	r.pdf.SetXY(textX, textY)
	r.pdf.Cell(textWidth, assets.SubtitleLineHeight, r.Title)

	descY := textY + assets.SubtitleLineHeight + assets.IntraComponentSpacing

	parts := r.pdf.ParseTextWithLinks(r.Description, []string{"support@muun.com"})
	r.pdf.RenderMultiStyledText(
		textX,
		descY,
		textWidth,
		assets.BodyParagraphLineHeight,
		parts,
		assets.BodyLetterSpacing,
		0,
	)

	r.pdf.SetY(startY + height)
}
