package emergencykit

import (
	"bytes"
	"crypto/rand"
	"fmt"
	"text/template"
	"time"
)

// Input struct to fill the PDF
type Input struct {
	FirstEncryptedKey  string
	SecondEncryptedKey string
}

// Output with the html as string and the verification code
type Output struct {
	HTML             string
	VerificationCode string
}

// GenerateHTML returns the translated emergency kit html as a string along with the verification code.
func GenerateHTML(params *Input, lang string) (*Output, error) {
	verificationCode := randomCode(6)

	content, err := render("EmergencyKitContent", lang, &contentData{
		FirstEncryptedKey:  params.FirstEncryptedKey,
		SecondEncryptedKey: params.SecondEncryptedKey,
		VerificationCode:   verificationCode,
		// Careful: do not change these format values. See this doc for more info: https://golang.org/pkg/time/#pkg-constants
		CurrentDate: time.Now().Format("2006/01/02"), // Format date to YYYY/MM/DD
	})
	if err != nil {
		return nil, fmt.Errorf("failed to render EmergencyKitContent template: %w", err)
	}

	page, err := render("EmergencyKitPage", lang, &pageData{
		Css:     css,
		Logo:    logo,
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

func randomCode(length int) string {
	result := make([]byte, length)
	_, err := rand.Read(result)
	if err != nil {
		panic(err)
	}
	charset := "0123456789"
	for i := 0; i < length; i++ {
		result[i] = charset[int(result[i])%len(charset)]
	}
	return string(result)
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
