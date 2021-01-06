package libwallet

import (
	"testing"
)

func TestGenerateEmergencyKitHTML(t *testing.T) {
	_, err := GenerateEmergencyKitHTML(&EKInput{
		FirstEncryptedKey:  "5zZPk5V7oJcXtQyFgdxrP6D5A4Xck2XMC2FG7rrxeDu89K4YuuMoAdZ2MeAGqMU28aR4Lsa5HRxB5mDXmajmYgLaZi6CivXeBRSzazJb8T4VizArrDA8NDH8TipEsHnwCyCd6eiNQYbedyRPw4B",
		SecondEncryptedKey: "4RLVcRNPSdCcV5pdd6FsNuUzhGwp3h7piXhpDkHbF31PrHmNqsyMd9vRveXsBVsWPLXHvMkvhzk68yGw4Wwcxfz55yPeN5Jogqpmn7BQc7P1SNymwtgbatLiJfwqFLm1iqoLPobCmK6wH7MY9N7",
	}, "es")
	if err != nil {
		t.Fatal(err)
	}
}
