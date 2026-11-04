package data

import "github.com/phpdave11/gofpdf"

// TextPart represents a part of text with specific styling
type TextPart struct {
	Text     string
	SetFont  func(*gofpdf.Fpdf)
	SetColor func(*gofpdf.Fpdf)
}
