package libwallet

import (
	"crypto/rand"
	"io"
	"time"

	"github.com/pkg/errors"
)

// EKInput input struct to fill the PDF
type EKInput struct {
	FirstEncryptedKey  string
	SecondEncryptedKey string
}

// EKOutput with the html as string and the verification code
type EKOutput struct {
	HTML             string
	VerificationCode string
}

// GenerateEmergencyKitHTML returns the html as a string along with the verification code
func GenerateEmergencyKitHTML(ekParams *EKInput) (*EKOutput, error) {
	return GenerateTranslatedEmergencyKitHTML(ekParams, "en")
}

// GenerateTranslatedEmergencyKitHTML returns the translated html as a string along with the verification code
func GenerateTranslatedEmergencyKitHTML(ekParams *EKInput, language string) (*EKOutput, error) {
	verificationCode := getRandomVerificationCode()
	currentDate := time.Now()

	data := EKTemplateData{
		FirstEncryptedKey:  ekParams.FirstEncryptedKey,
		SecondEncryptedKey: ekParams.SecondEncryptedKey,
		VerificationCode:   verificationCode,
		// Careful: do not change these format values. See the doc more info: https://golang.org/pkg/time/#pkg-constants
		CurrentDate: currentDate.Format("2006/01/02"), // Format date to YYYY/MM/DD
	}

	html, err := getEmergencyKitHTML(&data, language)
	if err != nil {
		return nil, errors.Wrapf(err, "Failed to obtain rendered HTML")
	}

	return &EKOutput{
		html,
		verificationCode,
	}, nil
}

func getRandomVerificationCode() string {
	const length = 6

	charset := [...]byte{'1', '2', '3', '4', '5', '6', '7', '8', '9', '0'}
	result := make([]byte, length)

	n, err := io.ReadAtLeast(rand.Reader, result, length)
	if n != length {
		panic(err)
	}

	for i := 0; i < len(result); i++ {
		result[i] = charset[int(result[i])%len(charset)]
	}

	return string(result)
}
