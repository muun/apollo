package emergencykit

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"strconv"
	"text/template"
	"time"
)

// Input struct to fill the PDF
type Input struct {
	FirstEncryptedKey  string
	FirstFingerprint   string
	SecondEncryptedKey string
	SecondFingerprint  string
	Version            int
}

// Output with the html as string and the verification code
type Output struct {
	HTML             string
	VerificationCode string
}

var spanishMonthNames = []string{
	"Enero",
	"Febrero",
	"Marzo",
	"Abril",
	"Mayo",
	"Junio",
	"Julio",
	"Agosto",
	"Septiembre",
	"Octubre",
	"Noviembre",
	"Diciembre",
}

// GenerateHTML returns the translated emergency kit html as a string along with the verification code.
func GenerateHTML(params *Input, lang string) (*Output, error) {
	verificationCode := generateDeterministicCode(params)

	// Render output descriptors:
	var descriptors string

	if params.hasFingerprints() {
		descriptors = GetDescriptorsHTML(&DescriptorsData{
			FirstFingerprint:  params.FirstFingerprint,
			SecondFingerprint: params.SecondFingerprint,
		})
	}

	// Render page body:
	content, err := render("EmergencyKitContent", lang, &contentData{
		// Externally provided:
		FirstEncryptedKey:  params.FirstEncryptedKey,
		SecondEncryptedKey: params.SecondEncryptedKey,

		// Computed by us:
		VerificationCode: verificationCode,
		CurrentDate:      formatDate(time.Now(), lang),
		Descriptors:      descriptors,

		// Template pieces separated for reuse:
		IconHelp:    iconHelp,
		IconPadlock: iconPadlock,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to render EmergencyKitContent template: %w", err)
	}

	// Render complete HTML page:
	page, err := render("EmergencyKitPage", lang, &pageData{
		Css:     css,
		Content: content,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to render EmergencyKitPage template: %w", err)
	}

	return &Output{
		HTML:             page,
		VerificationCode: verificationCode,
	}, nil
}

func formatDate(t time.Time, lang string) string {
	if lang == "en" {
		return t.Format("January 2, 2006")

	} else {
		// Golang has no i18n facilities, so we do our own formatting.
		year, month, day := t.Date()
		monthName := spanishMonthNames[month-1]

		return fmt.Sprintf("%d de %s, %d", day, monthName, year)
	}
}

func generateDeterministicCode(params *Input) string {
	// NOTE:
	// This function creates a stable verification code given the inputs to render the Emergency Kit. For now, the
	// implementation relies exclusively on the SecondEncryptedKey, which is the Muun key. This is obviously not ideal,
	// since we're both dropping part of the input and introducing the assumption that the Muun key will always be
	// rendered second -- but it compensates for a problem with one of our clients that causes the user key serialization
	// to be recreated each time the kit is rendered (making this deterministic approach useless).

	// Create a deterministic serialization of the input:
	inputMaterial := params.SecondEncryptedKey + strconv.Itoa(params.Version)

	// Compute a cryptographically secure hash of the material (critical, these are keys):
	inputHash := sha256.Sum256([]byte(inputMaterial))

	// Extract a verification code from the hash (doesn't matter if we discard bytes):
	var code string
	for _, b := range inputHash[:6] {
		code += strconv.Itoa(int(b) % 10)
	}

	return code
}

func render(name, language string, data interface{}) (string, error) {
	tmpl, err := template.New(name).Parse(getContent(name, language))
	if err != nil {
		return "", err
	}
	var buf bytes.Buffer
	err = tmpl.Execute(&buf, data)
	if err != nil {
		return "", err
	}
	return buf.String(), nil
}

func getContent(name string, language string) string {
	switch name {
	case "EmergencyKitPage":
		return page

	case "EmergencyKitContent":
		if language == "es" {
			return contentES
		}
		return contentEN

	default:
		panic("could not find template with name: " + name)
	}
}

func (i *Input) hasFingerprints() bool {
	return i.FirstFingerprint != "" && i.SecondFingerprint != ""
}
