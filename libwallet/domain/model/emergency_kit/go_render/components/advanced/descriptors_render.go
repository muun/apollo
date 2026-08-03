package advanced

import (
	"encoding/hex"

	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/assets"
)

var (
	functions = []string{"musig", "multi", "wsh", "wpkh", "pkh", "sh", "tr"}
)

const fingerprintLength = 8

func parseDescriptor(descriptor string) DescriptorLine {
	line := NewDescriptorLine()
	currentText := ""

	for i := 0; i < len(descriptor); i++ {
		if descriptor[i] == '#' {
			line.addSegment(currentText, SegmentDefault)
			currentText = ""
			line.addSegment(descriptor[i:], SegmentChecksum)
			break
		}

		if matched, text := matchFunctionAt(descriptor, i); matched {
			line.addSegment(currentText, SegmentDefault)
			line.addSegment(text, SegmentFunction)
			currentText = ""
			i += len(text) - 1
			continue
		}

		if matched, text := matchFingerprintAt(descriptor, i); matched {
			line.addSegment(currentText, SegmentDefault)
			line.addSegment(text, SegmentFingerprint)
			currentText = ""
			i += len(text) - 1
			continue
		}

		currentText += string(descriptor[i])
	}

	line.addSegment(currentText, SegmentDefault)
	return line
}

func matchFunctionAt(text string, pos int) (bool, string) {
	for _, fn := range functions {
		if pos+len(fn) <= len(text) && text[pos:pos+len(fn)] == fn {
			return true, fn
		}
	}
	return false, ""
}

func matchFingerprintAt(text string, pos int) (bool, string) {
	if pos+fingerprintLength > len(text) {
		return false, ""
	}

	fingerprint := text[pos : pos+fingerprintLength]
	_, err := hex.DecodeString(fingerprint)
	if err == nil {
		return true, fingerprint
	}

	return false, ""
}

func (line *DescriptorLine) addSegment(text string, segmentType SegmentType) {
	if text == "" {
		return
	}
	line.Segments = append(line.Segments, NewDescriptorSegment(text, segmentType))
}

func (r *AdvancedComponent) renderDescriptors(startX float64, width float64) {
	r.pdf.SetY(r.pdf.GetY() + assets.StandardHorizontalMargin)
	boxStartY := r.pdf.GetY()

	padding := assets.StandardHorizontalMargin
	lineHeight := assets.OutputDescriptorsLineHeight
	boxHeight := padding + float64(len(r.Descriptors))*lineHeight + padding

	r.drawDescriptorBox(startX, boxStartY, width, boxHeight)
	r.renderDescriptorLines(startX+padding, boxStartY+padding, lineHeight)

	r.pdf.SetY(boxStartY + boxHeight)
}

func (r *AdvancedComponent) drawDescriptorBox(x float64, y float64, width float64, height float64) {
	assets.SetBlueLightAltBackgroundColor(r.pdf.Fpdf)
	r.pdf.Rect(x, y, width, height, "F")
}

func (r *AdvancedComponent) renderDescriptorLines(x float64, y float64, lineHeight float64) {
	assets.SetDescriptorFont(r.pdf.Fpdf)
	currentY := y

	for _, line := range r.Descriptors {
		r.pdf.SetXY(x, currentY)
		r.renderDescriptorLine(line, lineHeight)
		currentY += lineHeight
	}
}

func (r *AdvancedComponent) renderDescriptorLine(line DescriptorLine, lineHeight float64) {
	currentX := r.pdf.GetX()
	currentY := r.pdf.GetY()

	for _, segment := range line.Segments {
		switch segment.Type {
		case SegmentDefault:
			assets.SetDescriptorDefaultColor(r.pdf.Fpdf)
		case SegmentFunction:
			assets.SetDescriptorFunctionColor(r.pdf.Fpdf)
		case SegmentFingerprint:
			assets.SetDescriptorFingerprintColor(r.pdf.Fpdf)
		case SegmentChecksum:
			assets.SetDescriptorChecksumColor(r.pdf.Fpdf)
		}
		currentX = r.pdf.RenderTextWithLetterSpacing(
			currentX,
			currentY,
			segment.Text,
			assets.BodyLetterSpacing,
			"L",
			"T",
			lineHeight,
		)
	}
}
