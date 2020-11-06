package recoverycode

import (
	"encoding/hex"
	"testing"
)

func TestGenerate(t *testing.T) {
	code := Generate()
	if err := Validate(code); err != nil {
		t.Fatalf("expected generated recovery code to be valid, got error: %v", err)
	}
}

func TestValidate(t *testing.T) {
	testCases := []struct {
		desc      string
		input     string
		expectErr bool
	}{
		{
			desc:      "empty string",
			input:     "",
			expectErr: true,
		},
		{
			desc:      "invalid version",
			input:     "LB2Q-48Z3-25JR-S5JB-5SUS-HXHJ-RCMM-8YUA",
			expectErr: true,
		},
		{
			desc:      "invalid characters",
			input:     "LA2Q-48Z3-25JR-S51B-5SUS-HXHJ-RCMM-8YUA",
			expectErr: true,
		},
		{
			desc:      "invalid length",
			input:     "LA2Q-48Z3-25JR-SB5S-USHX-HJRC-MM8YUA",
			expectErr: true,
		},
		{
			desc:      "valid",
			input:     "LA2Q-48Z3-25JR-S5JB-5SUS-HXHJ-RCMM-8YUA",
			expectErr: false,
		},
	}
	for _, tt := range testCases {
		t.Run(tt.desc, func(t *testing.T) {
			err := Validate(tt.input)
			if (err != nil) != tt.expectErr {
				t.Errorf("unexpected result: %v", err)
			}
		})
	}
}

func TestConvertToKey(t *testing.T) {
	testCases := []struct {
		desc     string
		input    string
		expected string
	}{
		{
			desc:     "legacy recovery code",
			input:    "R52Q-48Z3-25JR-S5JB-5SUS-HXHJ-RCMM-8YUA",
			expected: "8c0699a16e9c4950caa7b166d268bdd341ebab39ac6c90f3e3d6fc596b776d29",
		},
		{
			desc:     "version 2 recovery code",
			input:    "LA2Q-48Z3-25JR-S5JB-5SUS-HXHJ-RCMM-8YUA",
			expected: "0e1446153d4cafb073110739608fdd76b8712221476ec198cf35e1d74d274e83",
		},
	}
	for _, tt := range testCases {
		t.Run(tt.desc, func(t *testing.T) {
			key, err := ConvertToKey(tt.input, "muun:rc")
			if err != nil {
				t.Errorf("unexpected error: %v", err)
			}
			got := hex.EncodeToString(key.Serialize())
			if got != tt.expected {
				t.Errorf("expected %v but got %v", tt.expected, got)
			}
		})
	}
}
