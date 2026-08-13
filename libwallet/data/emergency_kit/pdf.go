package emergency_kit

import (
	"bytes"
	"math"
	"strings"
	"time"

	"github.com/phpdave11/gofpdf"

	"github.com/muun/libwallet/data/emergency_kit/resources"
)

type RenderingContext struct {
	NonDrawableHorizontalMargins float64
	TextStyling                  TextStyling
	Images                       []ImageAsset
}

// TextStyling contains function pointers for styling text elements
type TextStyling struct {
	SetBodyFont  func(*gofpdf.Fpdf)
	SetBodyColor func(*gofpdf.Fpdf)
	SetLinkFont  func(*gofpdf.Fpdf)
	SetLinkColor func(*gofpdf.Fpdf)
}

// ImageAsset contains embedded image data
type ImageAsset struct {
	Name   string
	Format string
	Data   []byte
}

// PdfExtensions wraps gofpdf.Fpdf to provide additional helper methods
type PdfExtensions struct {
	*gofpdf.Fpdf
	ctx RenderingContext

	// Per-step durations of the (static) setup, exposed for profiling. In milliseconds.
	RegisterFontsMs  int64
	RegisterImagesMs int64
}

// CreateAndSetupPdf creates a new PdfExtensions wrapper around a gofpdf.Fpdf instance
func CreateAndSetupPdf(ctx RenderingContext) *PdfExtensions {
	// Custom page size: 138.3 × 388.1 mm (13.83 × 38.81 cm)
	// Matches HTML/CSS PDF dimensions to ensure proper scaling in PDF viewers like Preview
	pageSize := resources.XY{X: 138.3, Y: 388.1}

	pdf := gofpdf.NewCustom(&gofpdf.InitType{
		OrientationStr: "P",  // Portrait
		UnitStr:        "mm", // Millimeters
		Size:           gofpdf.SizeType{Wd: pageSize.X, Ht: pageSize.Y},
		FontDirStr:     "",
	})
	pdfExt := &PdfExtensions{Fpdf: pdf, ctx: ctx}

	startFonts := time.Now()
	registerFonts(pdfExt)
	pdfExt.RegisterFontsMs = time.Since(startFonts).Milliseconds()

	startImages := time.Now()
	registerImages(pdf, ctx.Images)
	pdfExt.RegisterImagesMs = time.Since(startImages).Milliseconds()

	// Remove cell margins globally to prevent unwanted padding
	pdf.SetCellMargin(0)

	// This PDF manages its own page layout via explicit AddPage() calls, so auto page break
	// must be disabled to prevent gofpdf from inserting extra pages mid-render.
	pdf.SetAutoPageBreak(false, 0)

	return pdfExt
}

func registerFonts(pdf *PdfExtensions) {
	// Add custom fonts from embedded binary data:
	pdf.AddUTF8FontFromBytes("Roboto", "", resources.RobotoRegular)
	pdf.AddUTF8FontFromBytes("Roboto", "M", resources.RobotoMedium)
	pdf.AddUTF8FontFromBytes("RobotoMono", "", resources.RobotoMonoRegular)
	pdf.AddUTF8FontFromBytes("RobotoMono", "M", resources.RobotoMonoMedium)
}

func registerImages(pdf *gofpdf.Fpdf, images []ImageAsset) {
	for _, img := range images {
		pdf.RegisterImageReader(img.Name, img.Format, bytes.NewReader(img.Data))
	}
}

func (p *PdfExtensions) AddComponentSeparator() {
	p.SetY(p.GetY() + resources.Mm(58))
}

// TextPart represents a part of text with specific styling
type TextPart struct {
	Text     string
	SetFont  func(*gofpdf.Fpdf)
	SetColor func(*gofpdf.Fpdf)
}

// RenderMultiStyledText renders text with multiple styles and automatic word-level wrapping
// It handles mixed font styles (regular/bold) and colors within a single line,
// wrapping words to the next line as needed to fit within the available width.
//
// Parameters:
//   - startX: X coordinate where text rendering starts
//   - startY: Y coordinate where text rendering starts
//   - availableWidth: Maximum width available for text
//   - lineHeight: Height of each line
//   - parts: Array of text parts, each with its own styling
//   - minLines: Minimum number of lines to use (0 for no minimum)
//
// Returns:
//   - endY: Y coordinate after rendering all text
//     (useful for calculating next element position)
func (p *PdfExtensions) RenderMultiStyledText(
	startX float64,
	startY float64,
	availableWidth float64,
	lineHeight float64,
	parts []TextPart,
	letterSpacing float64,
	minLines int,
) float64 {
	styling := p.ctx.TextStyling
	// Collect all words with their styling and widths
	type styledWord struct {
		text       string
		width      float64
		partIdx    int
		isLast     bool
		hasSpace   bool
		spaceWidth float64
	}

	allWords := []styledWord{}

	for partIndex, part := range parts {
		// Split part text into words
		words := strings.Fields(part.Text)

		// Apply styling for this part to calculate widths
		part.SetFont(p.Fpdf)
		part.SetColor(p.Fpdf)

		// Collect words with their metadata
		for wordIndex, word := range words {
			isLastWord := partIndex == len(parts)-1 && wordIndex == len(words)-1

			// Check if next part starts with punctuation (don't add space before punctuation)
			nextPartStartsWithPunctuation := false
			if partIndex < len(parts)-1 && wordIndex == len(words)-1 &&
				len(parts[partIndex+1].Text) > 0 {
				nextPartStartsWithPunctuation = isPunctuation(parts[partIndex+1].Text[0])
			}

			needsSpace := !isLastWord && !nextPartStartsWithPunctuation

			wordWidth := p.GetStringWidthWithLetterSpacing(word, letterSpacing)
			spaceWidth := float64(0)
			if needsSpace {
				spaceWidth = p.GetStringWidth(" ")
			}

			allWords = append(allWords, styledWord{
				text:       word,
				width:      wordWidth,
				partIdx:    partIndex,
				isLast:     isLastWord,
				hasSpace:   needsSpace,
				spaceWidth: spaceWidth,
			})
		}
	}

	// Calculate natural line breaks
	currentX := startX
	currentLine := 0
	forceWrapIndex := -1

	for _, word := range allWords {
		totalWidth := word.width + word.spaceWidth
		if currentX+totalWidth > startX+availableWidth {
			currentLine++
			currentX = startX
		}
		currentX += totalWidth
	}
	totalNaturalLines := currentLine + 1

	// If we need to force minimum lines and text fits on fewer lines,
	// force the last word to wrap to a new line
	if minLines > 0 && totalNaturalLines < minLines && len(allWords) > 0 {
		forceWrapIndex = len(allWords) - 1
	}

	// Render all words
	currentX = startX
	currentY := startY

	for i, word := range allWords {
		totalWidth := word.width + word.spaceWidth

		// Force wrap if this is the designated word
		if i == forceWrapIndex {
			currentY += lineHeight
			currentX = startX
		} else if currentX+totalWidth > startX+availableWidth {
			// Natural wrap
			currentY += lineHeight
			currentX = startX
		}

		// Render word with its styling
		parts[word.partIdx].SetFont(p.Fpdf)
		parts[word.partIdx].SetColor(p.Fpdf)
		if letterSpacing > 0 {
			p.RenderTextWithLetterSpacing(
				currentX,
				currentY,
				word.text,
				letterSpacing,
				"L",
				"T",
				lineHeight,
			)
		} else {
			p.SetXY(currentX, currentY)
			p.Cell(word.width, lineHeight, word.text)
		}
		currentX += word.width

		// Render space in regular font (not underlined)
		if word.hasSpace {
			styling.SetBodyFont(p.Fpdf)
			styling.SetBodyColor(p.Fpdf)
			p.SetXY(currentX, currentY)
			p.Cell(word.spaceWidth, lineHeight, " ")
			currentX += word.spaceWidth
		}
	}

	return currentY + lineHeight
}

// MultiCellWithLetterSpacing renders multi-line text using the current font/color with custom
// letter spacing. It mimics the behaviour of gofpdf's MultiCell but applies inter-character
// spacing. The caller must set X/Y position and font/color before calling, just like MultiCell.
// After rendering, the Y cursor is advanced to the bottom of the last line.
func (p *PdfExtensions) MultiCellWithLetterSpacing(
	availableWidth, lineHeight float64,
	text string,
	letterSpacing float64,
) {
	startX := p.GetX()
	currentX := startX
	currentY := p.GetY()

	words := strings.Fields(text)
	spaceWidth := p.GetStringWidth(" ")

	for i, word := range words {
		wordWidth := p.GetStringWidthWithLetterSpacing(word, letterSpacing)
		maxX := startX + availableWidth

		// 1. Move to a new line if the word doesn't fit on the current one.
		//    Guard: skip if already at line start to avoid creating an empty line.
		if currentX > startX && currentX+wordWidth > maxX {
			currentY += lineHeight
			currentX = startX
		}

		if wordWidth <= availableWidth {
			// 2. Word fits on a single line — render it whole.
			currentX = p.RenderTextWithLetterSpacing(
				currentX,
				currentY,
				word,
				letterSpacing,
				"L",
				"T",
				lineHeight,
			)
		} else {
			// 3. Word is longer than the available width
			// (e.g. an encrypted key) — split character by character.
			lineStr := ""
			for _, char := range word {
				charStr := string(char)
				testWidth := p.GetStringWidthWithLetterSpacing(lineStr+charStr, letterSpacing)
				if lineStr != "" && currentX+testWidth > maxX {
					p.RenderTextWithLetterSpacing(
						currentX, currentY, lineStr,
						letterSpacing, "L", "T", lineHeight,
					)
					currentY += lineHeight
					currentX = startX
					lineStr = charStr
				} else {
					lineStr += charStr
				}
			}
			currentX = p.RenderTextWithLetterSpacing(
				currentX, currentY, lineStr,
				letterSpacing, "L", "T", lineHeight,
			)
		}

		// Add one space after each word
		if i < len(words)-1 {
			currentX += spaceWidth
		}
	}

	p.SetY(currentY + lineHeight)
}

// LineCountWithLetterSpacing estimates the number of lines text will occupy when rendered
// with the given letter spacing, based on total text width divided by available width.
func (p *PdfExtensions) LineCountWithLetterSpacing(
	maxWidth float64,
	text string,
	letterSpacing float64,
) float64 {
	if maxWidth <= 0 {
		return 1
	}
	textWidth := p.GetStringWidthWithLetterSpacing(text, letterSpacing)
	if textWidth <= maxWidth {
		return 1
	}
	return math.Ceil(textWidth / maxWidth)
}

// RenderTextWithLetterSpacing renders text with custom
// letter spacing.
// letterSpacing is specified as a fraction of the font size
// in pixels (0.05 = 5% of font size). The spacing is converted
// from points to millimeters to match PDF coordinate system.
// Custom spacing was already being used in the html/css version
// so we had to replicate it.
// horizontalAlign controls horizontal positioning:
// "L" (left), "C" (center), "R" (right)
// verticalAlign controls vertical positioning:
// "T" (top), "M" (middle), "B" (bottom), "A" (baseline)
// lineHeight is optional - if not provided or <= 0,
// defaults to fontHeight * 1.2
func (p *PdfExtensions) RenderTextWithLetterSpacing(
	x float64,
	y float64,
	text string,
	letterSpacing float64,
	horizontalAlign string,
	verticalAlign string,
	lineHeight ...float64,
) float64 {
	fontSizePt, _ := p.GetFontSize()
	fontSizeMm := resources.PtToMm(fontSizePt)

	cellHeight := fontSizeMm * 1.2
	if len(lineHeight) > 0 && lineHeight[0] > 0 {
		cellHeight = lineHeight[0]
	}

	currentX := x
	spacingAmount := fontSizeMm * letterSpacing

	for _, char := range text {
		charStr := string(char)
		charWidth := p.GetStringWidth(charStr)
		p.SetXY(currentX, y)
		p.CellFormat(
			charWidth,
			cellHeight,
			charStr,
			"",
			0,
			horizontalAlign+verticalAlign,
			false,
			0,
			"",
		)
		currentX += charWidth + spacingAmount
	}

	// Return the final X position (useful for continuing text on the same line)
	return currentX - spacingAmount // Remove the last spacing
}

// GetStringWidthWithLetterSpacing calculates the width of text with custom letter spacing
// letterSpacing is specified as a fraction of the font size in pixels (0.05 = 5% of font size)
// The spacing is converted from points to millimeters to match PDF coordinate system
func (p *PdfExtensions) GetStringWidthWithLetterSpacing(
	text string,
	letterSpacing float64,
) float64 {
	if text == "" {
		return 0
	}
	fontSizePt, _ := p.GetFontSize()
	fontSizeMm := resources.PtToMm(fontSizePt)
	spacingAmount := fontSizeMm * letterSpacing

	totalWidth := float64(0)
	for _, char := range text {
		charWidth := p.GetStringWidth(string(char))
		totalWidth += charWidth + spacingAmount
	}

	// Remove the last spacing since there's no spacing after the last character
	return totalWidth - spacingAmount
}

// GetDrawablePageWidth returns the usable page width after subtracting the horizontal margins on
// both sides.
func (p *PdfExtensions) GetDrawablePageWidth() float64 {
	pageWidth, _ := p.GetPageSize()
	return pageWidth - 2*p.ctx.NonDrawableHorizontalMargins
}

// ParseTextWithLinks splits text into parts, highlighting specified links in link color.
// Links are matched by exact string matching.
func (p *PdfExtensions) ParseTextWithLinks(text string, links []string) []TextPart {
	styling := p.ctx.TextStyling

	if len(links) == 0 {
		return []TextPart{
			{Text: text, SetFont: styling.SetBodyFont, SetColor: styling.SetBodyColor},
		}
	}

	// Find first link occurrence
	var foundLink string
	linkStart := -1
	for _, link := range links {
		index := strings.Index(text, link)
		if index >= 0 {
			linkStart = index
			foundLink = link
			break
		}
	}

	// No link found
	if linkStart < 0 {
		return []TextPart{
			{Text: text, SetFont: styling.SetBodyFont, SetColor: styling.SetBodyColor},
		}
	}

	// Build parts: before link, link, after link
	parts := []TextPart{}

	if linkStart > 0 {
		parts = append(parts, TextPart{
			Text:     text[:linkStart],
			SetFont:  styling.SetBodyFont,
			SetColor: styling.SetBodyColor,
		})
	}

	parts = append(parts, TextPart{
		Text:     foundLink,
		SetFont:  styling.SetLinkFont,
		SetColor: styling.SetLinkColor,
	})

	linkEnd := linkStart + len(foundLink)
	if linkEnd < len(text) {
		remainingText := text[linkEnd:]
		// If remaining text starts with punctuation, create a separate non-spaced part for it
		if len(remainingText) > 0 && isPunctuation(remainingText[0]) {
			parts = append(parts, TextPart{
				Text:     string(remainingText[0]),
				SetFont:  styling.SetBodyFont,
				SetColor: styling.SetBodyColor,
			})
			remainingText = remainingText[1:]
		}
		// Recursively parse remaining text for more links
		if len(remainingText) > 0 {
			remainingParts := p.ParseTextWithLinks(remainingText, links)
			parts = append(parts, remainingParts...)
		}
	}

	return parts
}

func (p *PdfExtensions) LineCount(maxWidth float64, text string) float64 {
	if maxWidth <= 0 {
		return 1
	}

	textWidth := p.GetStringWidth(text)
	if textWidth <= maxWidth {
		return 1
	}

	return math.Ceil(textWidth / maxWidth)
}

func isPunctuation(char byte) bool {
	return strings.ContainsRune(".,!?;:", rune(char))
}
