package assets

import (
	_ "embed"
	"github.com/muun/libwallet/data/emergency_kit/resources"

	"github.com/phpdave11/gofpdf"
)

const (
	keysSectionTitleFontSize  = 32
	sectionTitleFontSize      = 25
	subtitleFontSize          = 21
	verificationCodeFontSize  = 20
	KeyBoxTitleFontSize       = 18
	bodyParagraphFontSize     = 17
	keysHeaderFontSize        = 16
	instructionsBadgeFontSize = 14
	encryptedKeyFontSize      = 14
	dateFontSize              = 13
	descriptorFontSize        = 10

	BodyLetterSpacing = 0.01
)

func SetKeysSectionTitleFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMedium(pdf, keysSectionTitleFontSize)
}

func SetSectionTitleFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMedium(pdf, sectionTitleFontSize)
}

func SetSubtitleFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMedium(pdf, subtitleFontSize)
}

func SetVerificationCodeFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMonoRegular(pdf, verificationCodeFontSize)
}

func SetKeyBoxTitleFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMedium(pdf, KeyBoxTitleFontSize)
}

func SetBodyParagraphFont(pdf *gofpdf.Fpdf) {
	setBodyParagraphFont(pdf, false)
}

func SetBodyParagraphFontUnderlined(pdf *gofpdf.Fpdf) {
	setBodyParagraphFont(pdf, true)
}

func setBodyParagraphFont(pdf *gofpdf.Fpdf, underlined bool) {
	if underlined {
		resources.SetRobotoRegularUnderlined(pdf, bodyParagraphFontSize)
	} else {
		resources.SetRobotoRegular(pdf, bodyParagraphFontSize)
	}
}

func SetKeysHeaderSubtitlesFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoRegular(pdf, keysHeaderFontSize)
}

func SetKeysHeaderSubtitleBoldFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMedium(pdf, keysHeaderFontSize)
}

func SetInstructionsBadgeFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMedium(pdf, instructionsBadgeFontSize)
}

func SetEncryptedKeyFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMonoRegular(pdf, encryptedKeyFontSize)
}

func SetDateLabelFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoRegular(pdf, dateFontSize)
}

func SetDateValueFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMedium(pdf, dateFontSize)
}

func SetDescriptorFont(pdf *gofpdf.Fpdf) {
	resources.SetRobotoMonoRegular(pdf, descriptorFontSize)
}
