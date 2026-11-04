package test

import (
	"testing"

	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

// mockFirmwareGenerationsV3 are the generations the mock card runs as: the
// one the cards in the field run, and the next one as the spec lays it out.
// A test that runs over them pins that the client and the Houston simulator
// handle both without being told which one they got.
var mockFirmwareGenerationsV3 = []nfc.MockFirmwareGenerationV3{
	nfc.MockFirmwareGenerationV3_0,
	nfc.MockFirmwareGenerationV3_1,
}

// forEachMockFirmwareGenerationV3 runs the test once per generation, as a
// subtest named after it.
func forEachMockFirmwareGenerationV3(
	t *testing.T,
	run func(t *testing.T, generation nfc.MockFirmwareGenerationV3),
) {
	t.Helper()

	for _, generation := range mockFirmwareGenerationsV3 {
		t.Run(string(generation), func(t *testing.T) {
			run(t, generation)
		})
	}
}
