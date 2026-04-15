package advanced

import (
	"github.com/muun/libwallet/data/emergency_kit"
	"github.com/muun/libwallet/data/emergency_kit/resources"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/assets"
)

type AdvancedComponent struct {
	pdf               *emergency_kit.PdfExtensions
	Title             string
	Subtitle          string
	IntroParagraph    string
	Descriptors       []DescriptorLine
	ClosingParagraph1 string
	ClosingParagraph2 string
}

type DescriptorLine struct {
	Segments []DescriptorSegment
}

type SegmentType int

const (
	SegmentDefault SegmentType = iota
	SegmentFunction
	SegmentFingerprint
	SegmentChecksum
)

type DescriptorSegment struct {
	Text string
	Type SegmentType
}

func NewAdvancedComponent(
	pdf *emergency_kit.PdfExtensions,
	descriptors []string,
	translations *assets.Translations,
) *AdvancedComponent {
	parsedDescriptors := make([]DescriptorLine, len(descriptors))
	for i, desc := range descriptors {
		parsedDescriptors[i] = parseDescriptor(desc)
	}

	return &AdvancedComponent{
		pdf:               pdf,
		Title:             translations.Advanced.Title,
		Subtitle:          translations.Advanced.Subtitle,
		IntroParagraph:    translations.Advanced.Intro,
		Descriptors:       parsedDescriptors,
		ClosingParagraph1: translations.Advanced.Closing1,
		ClosingParagraph2: translations.Advanced.Closing2,
	}
}

func (r *AdvancedComponent) Height() float64 {
	sectionMarginTop := resources.Mm(40)
	titleLineHeight := assets.SectionTitleLineHeight
	subtitleMarginTop := resources.Mm(24)
	subtitleLineHeight := assets.SubtitleLineHeight
	introParagraphMarginTop := assets.IntraComponentSpacing
	introParagraphLineHeight := assets.BodyParagraphLineHeight
	descriptorsMarginTop := assets.StandardHorizontalMargin
	descriptorsPadding := assets.StandardHorizontalMargin
	descriptorLineHeight := resources.Pt(9) * 2.4
	closingMarginTop := assets.StandardHorizontalMargin
	closingLineHeight := assets.BodyParagraphLineHeight

	innerWidth := r.pdf.GetDrawablePageWidth()

	assets.SetBodyParagraphFont(r.pdf.Fpdf)
	introLines := r.pdf.LineCountWithLetterSpacing(innerWidth, r.IntroParagraph, assets.BodyLetterSpacing)

	assets.SetDescriptorFont(r.pdf.Fpdf)
	closing1Lines := r.pdf.LineCountWithLetterSpacing(innerWidth, r.ClosingParagraph1, assets.BodyLetterSpacing)
	closing2Lines := r.pdf.LineCountWithLetterSpacing(innerWidth, r.ClosingParagraph2, assets.BodyLetterSpacing)

	descriptorsHeight := descriptorsPadding +
		float64(len(r.Descriptors))*descriptorLineHeight +
		descriptorsPadding

	totalHeight := sectionMarginTop +
		titleLineHeight +
		subtitleMarginTop + subtitleLineHeight +
		introParagraphMarginTop + introLines*introParagraphLineHeight +
		descriptorsMarginTop + descriptorsHeight +
		closingMarginTop + closing1Lines*closingLineHeight +
		closingMarginTop + closing2Lines*closingLineHeight

	return totalHeight
}

func (r *AdvancedComponent) Render() {
	innerStartX := assets.StandardHorizontalMargin
	innerWidth := r.pdf.GetDrawablePageWidth()

	// Render title "Advanced information"
	assets.SetSectionTitleFont(r.pdf.Fpdf)
	assets.SetTitleColor(r.pdf.Fpdf)
	r.pdf.SetXY(innerStartX, r.pdf.GetY()+innerStartX)
	r.pdf.Cell(0, assets.SectionTitleLineHeight, r.Title)
	r.pdf.SetY(r.pdf.GetY() + assets.SectionTitleLineHeight)

	// Render subtitle "Output descriptors"
	r.pdf.SetY(r.pdf.GetY() + resources.Mm(24))
	assets.SetSubtitleFont(r.pdf.Fpdf)
	assets.SetTitleColor(r.pdf.Fpdf)
	r.pdf.SetXY(innerStartX, r.pdf.GetY())
	r.pdf.Cell(0, assets.SubtitleLineHeight, r.Subtitle)
	r.pdf.SetY(r.pdf.GetY() + assets.SubtitleLineHeight)

	// Render intro paragraph
	r.pdf.SetY(r.pdf.GetY() + assets.IntraComponentSpacing)
	assets.SetBodyParagraphFont(r.pdf.Fpdf)
	assets.SetSecondaryTextColor(r.pdf.Fpdf)
	r.pdf.SetXY(innerStartX, r.pdf.GetY())
	r.pdf.MultiCellWithLetterSpacing(innerWidth, assets.BodyParagraphLineHeight, r.IntroParagraph, assets.BodyLetterSpacing)

	// Render descriptors box
	r.renderDescriptors(innerStartX, innerWidth)

	// Render closing paragraph 1
	r.pdf.SetY(r.pdf.GetY() + assets.StandardHorizontalMargin)
	assets.SetBodyParagraphFont(r.pdf.Fpdf)
	assets.SetSecondaryTextColor(r.pdf.Fpdf)
	r.pdf.SetXY(innerStartX, r.pdf.GetY())
	r.pdf.MultiCellWithLetterSpacing(innerWidth, assets.BodyParagraphLineHeight, r.ClosingParagraph1, assets.BodyLetterSpacing)

	// Render closing paragraph 2
	r.pdf.SetY(r.pdf.GetY() + assets.StandardHorizontalMargin)
	assets.SetBodyParagraphFont(r.pdf.Fpdf)
	assets.SetSecondaryTextColor(r.pdf.Fpdf)
	r.pdf.SetXY(innerStartX, r.pdf.GetY())
	r.pdf.MultiCellWithLetterSpacing(innerWidth, assets.BodyParagraphLineHeight, r.ClosingParagraph2, assets.BodyLetterSpacing)
}

// renderDescriptors is implemented in descriptors_render.go
