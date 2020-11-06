package libwallet

import (
	"github.com/muun/libwallet/emergencykit"
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

// GenerateEmergencyKitHTML returns the translated html as a string along with the verification code
func GenerateEmergencyKitHTML(ekParams *EKInput, language string) (*EKOutput, error) {
	out, err := emergencykit.GenerateHTML(&emergencykit.Input{
		FirstEncryptedKey:  ekParams.FirstEncryptedKey,
		SecondEncryptedKey: ekParams.SecondEncryptedKey,
	}, language)
	if err != nil {
		return nil, err
	}
	return &EKOutput{
		HTML:             out.HTML,
		VerificationCode: out.VerificationCode,
	}, nil
}
