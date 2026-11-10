package keys

import (
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/data"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/data/resources"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/model/go_render/assets"
)

var (
	iconMarginTop         = resources.Mm(10)
	titleY                = resources.Mm(30)
	iconMarginX           = resources.Mm(4)
	subtitleToBottomSpace = resources.Mm(31)
	titleToSubtitleSpace  = resources.Mm(4)
)

type KeysHeaderComponent struct {
	pdf          *data.PdfExtensions
	Translations *assets.Translations
}

func NewKeysHeaderComponent(
	pdf *data.PdfExtensions,
	translations *assets.Translations,
) *KeysHeaderComponent {
	return &KeysHeaderComponent{
		pdf:          pdf,
		Translations: translations,
	}
}

func (r *KeysHeaderComponent) Height() float64 {
	iconTrailing := iconMarginX + assets.PadlockIconSize
	textWidth := r.pdf.GetDrawablePageWidth() - iconTrailing

	assets.SetKeysHeaderSubtitlesFont(r.pdf.Fpdf)
	fullText := r.Translations.Keys.EncryptedBackupDesc1 +
		" " + r.Translations.Keys.EncryptedBackupDesc2
	subtitleLines := r.pdf.LineCountWithLetterSpacing(
		textWidth, fullText, assets.BodyLetterSpacing,
	)
	if subtitleLines < 2 {
		subtitleLines = 2
	}

	totalTextHeightWithSpaces := assets.KeysSectionTitleLineHeight +
		titleToSubtitleSpace +
		subtitleLines*assets.BodyParagraphLineHeight

	return assets.StandardHorizontalMargin + totalTextHeightWithSpaces + subtitleToBottomSpace
}

func (r *KeysHeaderComponent) Render() {
	startY := r.pdf.GetY()

	iconX := assets.StandardHorizontalMargin + iconMarginX
	iconY := startY + assets.StandardHorizontalMargin + iconMarginTop
	r.pdf.DrawIcon(assets.PadlockImageName, iconX, iconY, assets.PadlockIconSize)

	titleX := iconX + assets.PadlockIconSize
	relativeToComponentYTitleY := startY + titleY

	assets.SetKeysSectionTitleFont(r.pdf.Fpdf)
	assets.SetTitleColor(r.pdf.Fpdf)
	r.pdf.SetXY(titleX, relativeToComponentYTitleY)
	r.pdf.CellFormat(
		0,
		assets.KeysSectionTitleLineHeight,
		r.Translations.Keys.EncryptedBackupTitle,
		"",
		2,
		"L",
		false,
		0,
		"",
	)

	subtitleY := r.pdf.GetY() + titleToSubtitleSpace
	textWidth := r.pdf.GetDrawablePageWidth() - (titleX - assets.StandardHorizontalMargin)

	parts := []data.TextPart{
		{
			Text:     r.Translations.Keys.EncryptedBackupDesc1,
			SetFont:  assets.SetKeysHeaderSubtitlesFont,
			SetColor: assets.SetSecondaryTextColor,
		},
		{
			Text:     r.Translations.Keys.EncryptedBackupDesc2,
			SetFont:  assets.SetKeysHeaderSubtitleBoldFont,
			SetColor: assets.SetTitleColor,
		},
	}

	_ = r.pdf.RenderMultiStyledText(
		titleX,
		subtitleY,
		textWidth,
		assets.BodyParagraphLineHeight,
		parts,
		assets.BodyLetterSpacing,
		2,
	)

	r.pdf.SetY(startY + r.Height())
}
