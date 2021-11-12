package bech32m

import (
	"testing"
)

// The following test vectors were taken from BIP-350.
// We only test for valid/invalid (and not decoded data), since only the checksum changed.

var validBech32m = []string{
	"A1LQFN3A",
	"a1lqfn3a",
	"an83characterlonghumanreadablepartthatcontainsthetheexcludedcharactersbioandnumber11sg7hg6",
	"abcdef1l7aum6echk45nj3s0wdvt2fg8x9yrzpqzd3ryx",
	"11llllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllludsr8",
	"split1checkupstagehandshakeupstreamerranterredcaperredlc445v",
	"?1v759aa",
}

var invalidBech32m = []string{
	"\x201xj0phk", // HRP character out of range
	"\x7f1g6xzxy", // HRP character out of range
	"\x801vctc34", // HRP character out of range

	"an84characterslonghumanreadablepartthatcontainsthetheexcludedcharactersbioandnumber11d6pts4", // Overall max length exceeded

	"qyrz8wqd2c9m",  // No separator
	"1qyrz8wqd2c9m", // Empty HRP
	"16plkw9",       // Empty HRP
	"1p2gdwpf",      // Empty HRP

	"y1b0jsk6g",  // Invalid data character
	"lt1igcx5c0", // Invalid data character

	"in1muywd",  // Too short checksum
	"mm1crxm3i", // Invalid character in checksum
	"au1s5cgom", // Invalid character in checksum
	"M1VUXWEZ",  // checksum calculated with uppercase form of HRP
}

func TestDecodeValid(t *testing.T) {
	for _, validBech := range validBech32m {
		_, _, err := Decode(validBech)
		if err != nil {
			t.Fatalf("failed to decode valid bech32m %s: %v", validBech, err)
		}
	}
}

func TestDecodeInvalid(t *testing.T) {
	for _, invalidBech := range invalidBech32m {
		_, _, err := Decode(invalidBech)
		if err == nil {
			t.Fatalf("success decoding invalid string %s", invalidBech)
		}
	}
}

func TestNotCompat(t *testing.T) {
	someBech32 := "bcrt1q77ayq0ldrwr3vg0rl0ss8u0ne0hajllz4h7yrqm8ldyy2v0860vs9xzmr4"

	_, _, err := Decode(someBech32)
	if err == nil {
		t.Fatalf("success decoding bech32 with bech32m %s (expected checksum failure)", someBech32)
	}
}
