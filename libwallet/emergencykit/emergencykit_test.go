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
	if !strings.Contains(out.HTML, `<span class="f">musig</span>`) {
		t.Fatal("expected output html to contain musig output descriptor scripts")
	}
	if !strings.Contains(out.HTML, data.FirstFingerprint) {
		t.Fatal("expected output html to contain FirstFingerprint")
	}
	if !strings.Contains(out.HTML, data.SecondFingerprint) {
		t.Fatal("expected output html to contain SecondFingerprint")
	}
}

func TestGenerateDeterministicCode(t *testing.T) {
	// Create a base Input, without version, which we'll set for each case below:
	input := &Input{
		FirstEncryptedKey:  "foo",
		SecondEncryptedKey: "bar",
	}

	// List our cases for each version:
	versionExpectedCodes := []struct {
		version      int
		expectedCode string
	}{
		{1, "190981"},
		{2, "257250"},
		{3, "494327"},
	}

	// Do the thing:
	for _, testCase := range versionExpectedCodes {
		input.Version = testCase.version
		code := generateDeterministicCode(input)

		if code != testCase.expectedCode {
			t.Fatalf("expected code from %+v to be %s, not %s", input, testCase.expectedCode, code)
		}
	}
}
