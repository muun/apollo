package resources

import (
	_ "embed"

	"github.com/phpdave11/gofpdf"
)

// Embed font files as binary data in the compiled binary
// This eliminates the need for external font files at runtime
//
// These fonts are licensed under the Apache License 2.0 - see LICENSE file in this directory

//go:embed fonts/Roboto-Regular.ttf
var RobotoRegular []byte

//go:embed fonts/Roboto-Medium.ttf
var RobotoMedium []byte

//go:embed fonts/RobotoMono-Regular.ttf
var RobotoMonoRegular []byte

//go:embed fonts/RobotoMono-Medium.ttf
var RobotoMonoMedium []byte

func SetRobotoRegular(pdf *gofpdf.Fpdf, sizeInPixels float64) {
	pdf.SetFont("Roboto", "", Pt(sizeInPixels))
}

func SetRobotoRegularUnderlined(pdf *gofpdf.Fpdf, sizeInPixels float64) {
	pdf.SetFont("Roboto", "U", Pt(sizeInPixels))
}

func SetRobotoMedium(pdf *gofpdf.Fpdf, sizeInPixels float64) {
	pdf.SetFont("Roboto", "M", Pt(sizeInPixels))
}

func SetRobotoMonoRegular(pdf *gofpdf.Fpdf, sizeInPixels float64) {
	pdf.SetFont("RobotoMono", "", Pt(sizeInPixels))
}
