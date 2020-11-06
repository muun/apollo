package libwallet

import (
	"testing"
)

func TestGenerateEmergencyKitHTML(t *testing.T) {
	_, err := GenerateEmergencyKitHTML(&EKInput{
		FirstEncryptedKey:  "MyFirstEncryptedKey",
		SecondEncryptedKey: "MySecondEncryptedKey",
	}, "es")
	if err != nil {
		t.Fatal(err)
	}
}
