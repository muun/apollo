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
	if !strings.Contains(out.HTML, `<ul class="descriptors">`) {
		t.Fatal("expected output html to contain output descriptors")
	}
	if !strings.Contains(out.HTML, `<span class="f">wsh</span>`) {
		t.Fatal("expected output html to contain output descriptor scripts")
	}
}

func TestGenerateHTMLWithFingerprints(t *testing.T) {
	data := &Input{
		FirstEncryptedKey:  "MyFirstEncryptedKey",
		FirstFingerprint:   "abababab",
		SecondEncryptedKey: "MySecondEncryptedKey",
		SecondFingerprint:  "cdcdcdcd",
	}

	out, err := GenerateHTML(data, "en")
	if err != nil {
		t.Fatal(err)
	}

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
	if !strings.Contains(out.HTML, `<ul class="descriptors">`) {
		t.Fatal("expected output html to contain output descriptors")
	}
	if !strings.Contains(out.HTML, `<span class="f">wsh</span>`) {
		t.Fatal("expected output html to contain output descriptor scripts")
	}
	if !strings.Contains(out.HTML, data.FirstFingerprint) {
		t.Fatal("expected output html to contain FirstFingerprint")
	}
	if !strings.Contains(out.HTML, data.SecondFingerprint) {
		t.Fatal("expected output html to contain SecondFingerprint")
	}
}

func TestGenerateDeterministicCode(t *testing.T) {
	mockFirstKey := "foo"
	mockSecondKey := "bar"
	expectedCode := "223695"

	mockInputs := &Input{
		FirstEncryptedKey:  mockFirstKey,
		SecondEncryptedKey: mockSecondKey,
	}

	code := generateDeterministicCode(mockInputs)
	if code != expectedCode {
		t.Fatalf("expected code from (%s, %s) to be %s, not %s", mockFirstKey, mockSecondKey, expectedCode, code)
	}
}
