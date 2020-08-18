package libwallet

import (
	"testing"
)

// Test_renderToLog is not a real test, just a helper for human eyes.
func Test_renderEmergencyKitToLog(t *testing.T) {
	data := EKTemplateData{
		FirstEncryptedKey:  "FirstEncryptedKey",
		SecondEncryptedKey: "SecondEncryptedKey",
		VerificationCode:   "VerificationCode",
		CurrentDate:        "CurrentDate",
	}

	html, err := getEmergencyKitHTML(&data, "es")
	if err != nil {
		t.Error(err)
	}

	t.Log(html)
}
