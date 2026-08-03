package components

import (
	"github.com/muun/libwallet/data/emergency_kit"
	"github.com/muun/libwallet/data/emergency_kit/resources"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/assets"
)

var (
	itemMarginTop        = resources.Mm(38)
	numberCircleSize     = resources.Mm(22)
	numberVerticalAdjust = resources.Mm(0.75)
	titleVerticalAdjust  = resources.Mm(1.15)
)

type InstructionsComponent struct {
	pdf   *emergency_kit.PdfExtensions
	Title string
	Intro string
	Items []InstructionItem
}

type InstructionItem struct {
	Number      string
	Title       string
	Description string
}

func NewInstructionItem(number, title, description string) InstructionItem {
	return InstructionItem{
		Number:      number,
		Title:       title,
		Description: description,
	}
}

func NewInstructionsComponent(
	pdf *emergency_kit.PdfExtensions,
	translations *assets.Translations,
) *InstructionsComponent {
	return &InstructionsComponent{
		pdf:   pdf,
		Title: translations.Instructions.Title,
		Intro: translations.Instructions.Intro,
		Items: []InstructionItem{
			NewInstructionItem(
				"1", translations.Instructions.Step1Title, translations.Instructions.Step1Desc,
			),
			NewInstructionItem(
				"2", translations.Instructions.Step2Title, translations.Instructions.Step2Desc,
			),
			NewInstructionItem(
				"3", translations.Instructions.Step3Title, translations.Instructions.Step3Desc,
			),
		},
	}
}

func (r *InstructionsComponent) Height() float64 {
	contentWidth := r.pdf.GetDrawablePageWidth()

	titleHeight := assets.SectionTitleLineHeight
	introHeight := r.calculateIntroHeight(contentWidth)
	allItemsHeight := r.calculateAllItemsHeight(contentWidth)

	return titleHeight + introHeight + allItemsHeight
}

func (r *InstructionsComponent) calculateIntroHeight(contentWidth float64) float64 {
	assets.SetBodyParagraphFont(r.pdf.Fpdf)
	introLines := r.pdf.LineCountWithLetterSpacing(contentWidth, r.Intro, assets.BodyLetterSpacing)
	return assets.IntraComponentSpacing + introLines*assets.BodyParagraphLineHeight
}

func (r *InstructionsComponent) calculateAllItemsHeight(contentWidth float64) float64 {
	textWidth := contentWidth - numberCircleSize - assets.StandardHorizontalMargin
	totalHeight := itemMarginTop

	for i, item := range r.Items {
		if i > 0 {
			totalHeight += itemMarginTop
		}
		totalHeight += r.calculateItemHeight(textWidth, item)
	}

	return totalHeight
}

func (r *InstructionsComponent) calculateItemHeight(
	textWidth float64,
	item InstructionItem,
) float64 {
	assets.SetBodyParagraphFont(r.pdf.Fpdf)
	descriptionLines := r.pdf.LineCountWithLetterSpacing(
		textWidth,
		item.Description,
		assets.BodyLetterSpacing,
	)

	return assets.SubtitleLineHeight +
		assets.IntraComponentSpacing +
		descriptionLines*assets.BodyParagraphLineHeight
}

func (r *InstructionsComponent) Render() {
	r.renderTitle()
	r.renderIntro()
	r.renderItems()
}

func (r *InstructionsComponent) renderTitle() {
	assets.SetSectionTitleFont(r.pdf.Fpdf)
	assets.SetTitleColor(r.pdf.Fpdf)
	r.pdf.SetXY(assets.StandardHorizontalMargin, r.pdf.GetY())
	r.pdf.Cell(0, assets.SectionTitleLineHeight, r.Title)
	r.pdf.SetY(r.pdf.GetY() + assets.SectionTitleLineHeight)
}

func (r *InstructionsComponent) renderIntro() {
	r.pdf.SetY(r.pdf.GetY() + assets.IntraComponentSpacing)
	assets.SetBodyParagraphFont(r.pdf.Fpdf)
	assets.SetSecondaryTextColor(r.pdf.Fpdf)
	r.pdf.SetXY(assets.StandardHorizontalMargin, r.pdf.GetY())
	r.pdf.MultiCellWithLetterSpacing(
		r.pdf.GetDrawablePageWidth(),
		assets.BodyParagraphLineHeight,
		r.Intro,
		assets.BodyLetterSpacing,
	)
}

func (r *InstructionsComponent) renderItems() {
	r.pdf.SetY(r.pdf.GetY() + itemMarginTop)
	for i, item := range r.Items {
		if i > 0 {
			r.pdf.SetY(r.pdf.GetY() + itemMarginTop)
		}
		r.renderSingleItem(item)
	}
}

func (r *InstructionsComponent) renderSingleItem(item InstructionItem) {
	itemY := r.pdf.GetY()

	r.renderNumberCircle(itemY, item.Number)

	textX := 2*assets.StandardHorizontalMargin + numberCircleSize
	textWidth := r.pdf.GetDrawablePageWidth() - numberCircleSize - assets.StandardHorizontalMargin

	r.renderItemTitle(textX, itemY, textWidth, item.Title)
	r.renderItemDescription(textX, itemY, textWidth, item.Description)

	r.pdf.SetY(itemY + r.calculateItemHeight(textWidth, item))
}

func (r *InstructionsComponent) renderNumberCircle(itemY float64, number string) {
	circleRadius := numberCircleSize / 2
	circleX := assets.StandardHorizontalMargin + circleRadius
	titleMidY := itemY + assets.SubtitleLineHeight/2
	circleTopY := titleMidY - circleRadius

	assets.SetInstructionNumberBackgroundColor(r.pdf.Fpdf)
	r.pdf.Circle(circleX, titleMidY, circleRadius, "F")

	assets.SetInstructionsBadgeFont(r.pdf.Fpdf)
	assets.SetWhiteTextColor(r.pdf.Fpdf)

	r.pdf.SetXY(assets.StandardHorizontalMargin, circleTopY+numberVerticalAdjust)
	r.pdf.CellFormat(numberCircleSize, numberCircleSize, number, "", 0, "C", false, 0, "")
}

func (r *InstructionsComponent) renderItemTitle(
	textX float64,
	itemY float64,
	textWidth float64,
	title string,
) {
	assets.SetSubtitleFont(r.pdf.Fpdf)
	assets.SetTitleColor(r.pdf.Fpdf)
	r.pdf.SetXY(textX, itemY+titleVerticalAdjust)
	r.pdf.CellFormat(textWidth, assets.SubtitleLineHeight, title, "", 0, "L", false, 0, "")
}

func (r *InstructionsComponent) renderItemDescription(
	textX float64,
	itemY float64,
	textWidth float64,
	description string,
) {
	descriptionY := itemY + assets.SubtitleLineHeight + assets.IntraComponentSpacing

	parts := r.pdf.ParseTextWithLinks(description, []string{"github.com/muun/recovery"})
	r.pdf.RenderMultiStyledText(
		textX,
		descriptionY,
		textWidth,
		assets.BodyParagraphLineHeight,
		parts,
		assets.BodyLetterSpacing,
		0,
	)
}
