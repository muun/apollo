package keys

import (
	"github.com/muun/libwallet/data/emergency_kit"
	"github.com/muun/libwallet/data/emergency_kit/resources"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/assets"
)

var (
	keyBoxHorizontalMargin  = resources.Mm(20)
	keyBoxTitleToKeySpacing = resources.Mm(12)

	keyBoxInnerTopVerticalMargin = resources.Mm(38)
	KeyBoxInnerBottomMargin      = resources.Mm(25.5)
)

const (
	letterSpacing = 0.05
)

type singleKeyBox struct {
	pdf       *emergency_kit.PdfExtensions
	TitleText string
	KeyText   string
}

func newSingleKeyBox(pdf *emergency_kit.PdfExtensions, titleText string, keyText string) *singleKeyBox {
	return &singleKeyBox{
		pdf:       pdf,
		TitleText: titleText,
		KeyText:   keyText,
	}
}

func (r *singleKeyBox) Height() float64 {
	return keyBoxInnerTopVerticalMargin + r.calculateTitlePlusTextHeight() + KeyBoxInnerBottomMargin
}

func (r *singleKeyBox) keyTextHeight() float64 {
	assets.SetEncryptedKeyFont(r.pdf.Fpdf)
	keyLines := r.pdf.LineCountWithLetterSpacing(r.getBoxContentWidth(), r.KeyText, assets.BodyLetterSpacing)
	return keyLines * assets.EncryptedKeysTextFontSize
}

func (r *singleKeyBox) Render() {
	startY := r.pdf.GetY()
	contentWidth := r.getBoxContentWidth()
	boxHeight := r.Height()

	assets.SetWhiteBackgroundColor(r.pdf.Fpdf)
	r.pdf.Rect(keyBoxHorizontalMargin, startY, r.getBoxTotalWidth(), boxHeight, "F")

	innerY := startY + keyBoxInnerTopVerticalMargin
	innerX := keyBoxHorizontalMargin + assets.StandardHorizontalMargin

	assets.SetKeyBoxTitleFont(r.pdf.Fpdf)
	assets.SetTitleColor(r.pdf.Fpdf)
	r.pdf.RenderTextWithLetterSpacing(innerX, innerY, r.TitleText, letterSpacing, "L", "T")

	assets.SetEncryptedKeyFont(r.pdf.Fpdf)
	assets.SetSecondaryTextColor(r.pdf.Fpdf)
	keyTextY := r.pdf.GetY() + keyBoxTitleToKeySpacing + resources.Mm(assets.KeyBoxTitleFontSize)
	r.pdf.SetXY(innerX, keyTextY)

	// EncryptedKeysTextFontSize gives a 1.86 ratio for the KeyText 13px font. This high ratio
	// is intentional to improve readability of dense monospace text.
	r.pdf.MultiCellWithLetterSpacing(contentWidth, assets.EncryptedKeysTextFontSize, r.KeyText, assets.BodyLetterSpacing)

	r.pdf.SetY(startY + boxHeight)
}

func (r *singleKeyBox) getBoxContentWidth() float64 {
	return r.getBoxTotalWidth() - 2*assets.StandardHorizontalMargin
}

func (r *singleKeyBox) getBoxTotalWidth() float64 {
	pageWidth, _ := r.pdf.GetPageSize()
	return pageWidth - 2*keyBoxHorizontalMargin
}

func (r *singleKeyBox) calculateTitlePlusTextHeight() float64 {
	return resources.Mm(assets.KeyBoxTitleFontSize) + r.keyTextHeight() + keyBoxTitleToKeySpacing
}
