package data

import "github.com/phpdave11/gofpdf"

// TextStyling contains function pointers for styling text elements
type TextStyling struct {
	SetBodyFont  func(*gofpdf.Fpdf)
	SetBodyColor func(*gofpdf.Fpdf)
	SetLinkFont  func(*gofpdf.Fpdf)
	SetLinkColor func(*gofpdf.Fpdf)
}
