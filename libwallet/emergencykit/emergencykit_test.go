package emergencykit

import (
	"strings"
	"testing"
)

func TestGenerateHTML(t *testing.T) {
	out, err := GenerateHTML(&Input{
		FirstEncryptedKey:  "MyFirstEncryptedKey",
		SecondEncryptedKey: "MySecondEncryptedKey",
	}, "en")
	if err != nil {
		t.Fatal(err)
	}
	// t.Log(out.HTML)
	if len(out.VerificationCode) != 6 {
		t.Fatal("expected verification code to have length 6")
	}
	if !strings.Contains(out.HTML, out.VerificationCode) {
		t.Fatal("expected output html to contain verification code")
	}
	if !strings.Contains(out.HTML, "MyFirstEncryptedKey") {
		t.Fatal("expected output html to contain first encrypted key")
	}
	if !strings.Contains(out.HTML, "MySecondEncryptedKey") {
		t.Fatal("expected output html to contain second encrypted key")
	}
}
