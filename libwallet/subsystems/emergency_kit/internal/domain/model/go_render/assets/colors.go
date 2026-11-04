package assets

import (
	"github.com/phpdave11/gofpdf"
)

func SetTitleColor(pdf *gofpdf.Fpdf) {
	pdf.SetTextColor(24, 36, 73)
}

func SetSecondaryTextColor(pdf *gofpdf.Fpdf) {
	pdf.SetTextColor(87, 101, 128)
}

func SetHeaderBackgroundColor(pdf *gofpdf.Fpdf) {
	pdf.SetFillColor(247, 251, 255)
}

func SetBlueLightAltBackgroundColor(pdf *gofpdf.Fpdf) {
	pdf.SetFillColor(246, 249, 255)
}

func SetWhiteBackgroundColor(pdf *gofpdf.Fpdf) {
	pdf.SetFillColor(255, 255, 255)
}

func SetKeysBackgroundColor(pdf *gofpdf.Fpdf) {
	pdf.SetFillColor(223, 236, 251)
}

func SetInstructionNumberBackgroundColor(pdf *gofpdf.Fpdf) {
	pdf.SetFillColor(36, 116, 205)
}

func SetLinkColor(pdf *gofpdf.Fpdf) {
	pdf.SetTextColor(51, 124, 208)
}

func SetWhiteTextColor(pdf *gofpdf.Fpdf) {
	pdf.SetTextColor(255, 255, 255)
}

func SetDescriptorDefaultColor(pdf *gofpdf.Fpdf) {
	pdf.SetTextColor(87, 101, 128)
}

func SetDescriptorFunctionColor(pdf *gofpdf.Fpdf) {
	pdf.SetTextColor(68, 123, 239)
}

func SetDescriptorFingerprintColor(pdf *gofpdf.Fpdf) {
	pdf.SetTextColor(215, 74, 65)
}

func SetDescriptorChecksumColor(pdf *gofpdf.Fpdf) {
	pdf.SetTextColor(164, 47, 162)
}
