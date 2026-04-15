package resources

import (
	"github.com/phpdave11/gofpdf"
)

const (
	// MillimetersPerInch is the number of millimeters in one inch
	MillimetersPerInch = 25.4

	// PixelsPerInch is the DPI for CSS pixels (web standard)
	PixelsPerInch = 96

	// PointsPerInch is the DPI for PDF points (PostScript/PDF standard)
	PointsPerInch = 72

	// PixelsToPointsRatio converts CSS pixels (96 DPI) to PDF points (72 DPI)
	// Calculated as: PointsPerInch / PixelsPerInch
	PixelsToPointsRatio = float64(PointsPerInch) / float64(PixelsPerInch)
)

// Mm converts pixels to millimeters at 96 DPI
// Formula: pixels * (mm/inch) / (pixels/inch) = millimeters
func Mm(pixels float64) float64 {
	return pixels * MillimetersPerInch / PixelsPerInch
}

// Pt converts pixels to points (96 DPI: 1px = 0.75pt)
func Pt(pixels float64) float64 {
	return pixels * PixelsToPointsRatio
}

// PtToPixels converts points to pixels (96 DPI: 1pt ≈ 1.333px)
func PtToPixels(points float64) float64 {
	return points / PixelsToPointsRatio
}

// PtToMm converts points to millimeters (96 DPI: 1pt ≈ 0.353mm)
func PtToMm(points float64) float64 {
	return Mm(PtToPixels(points))
}

type XY struct {
	X float64
	Y float64
}

type Component interface {
	Height(pdf *gofpdf.Fpdf) float64
	Render(pdf *gofpdf.Fpdf)
}
