package nfc

import (
	"bytes"

	"github.com/muun/libwallet/platform/preconditions"
)

// MockFirmwareGenerationV3 names a firmware generation MockMuunCardV3 runs
// as. A generation is a wire behaviour named after the firmware that
// introduced it: what SELECT answers, how the metadata applet stores its keys
// and lays the metadata out, what certificate it holds, and which secret keys
// the challenge response MAC. 3.0 and 3.1 pair the same way.
//
// A card moves to a later firmware within its generation with
// UpgradeFirmware, and to another generation only with ReinstallFirmware.
//
// The Houston simulator keeps one reader per generation
// (base/data/houston/simulator/card_generation_v3.go). A generation is added or
// retired there and here together.
type MockFirmwareGenerationV3 string

const (
	// MockFirmwareGenerationV3_0 is firmware 3.0, what the cards in the field
	// run: the version alone at SELECT, one key agreement key, a certificate
	// over that key and the commitment key, and a challenge response MAC keyed
	// with the secret the request MAC matched.
	MockFirmwareGenerationV3_0 MockFirmwareGenerationV3 = "3.0"

	// MockFirmwareGenerationV3_1 is firmware 3.1 as the protocol spec lays it
	// out: the short device id at SELECT, 64 key agreement keys under one
	// certified tree, the key agreement key in use in the clear in the
	// metadata, and a challenge response MAC keyed with the secret the ratchet
	// derives.
	MockFirmwareGenerationV3_1 MockFirmwareGenerationV3 = "3.1"
)

// firmware returns what a card running this generation does. The constants
// above are the only values, so anything else is a programming error and
// panics.
func (g MockFirmwareGenerationV3) firmware() mockFirmwareV3 {
	firmware, ok := mockFirmwareByGenerationV3[g]
	if !ok {
		preconditions.MissingCase(g)
	}
	return firmware
}

// runs reports whether firmware version runs on this generation's metadata
// applet: from the version that introduced the generation up to, and not
// including, the one that introduced the next.
func (g MockFirmwareGenerationV3) runs(version [VersionSizeV3]byte) bool {
	introduced := g.firmware().version
	if bytes.Compare(version[:], introduced[:]) < 0 {
		return false
	}
	for _, other := range mockFirmwareByGenerationV3 {
		later := bytes.Compare(other.version[:], introduced[:]) > 0
		if later && bytes.Compare(version[:], other.version[:]) >= 0 {
			return false
		}
	}
	return true
}

// mockFirmwareV3 is what a card does differently per generation: the version
// it reports, how much it answers SELECT with, the metadata applet its
// ceremony leaves, and the secret that keys its challenge response MAC.
type mockFirmwareV3 struct {
	// version is major(u16be) || minor(u16be), what the card reports.
	version [VersionSizeV3]byte

	// selectResponseSize is how much of firmware_version || short_device_id
	// the applet answers SELECT with.
	selectResponseSize int

	// newMetadataApplet runs the generation's ceremony.
	newMetadataApplet func() (mockMetadataAppletV3, error)

	// responseMACUnderNextSecret keys the challenge response MAC with the
	// secret the ratchet derives, as the protocol spec does, so the MAC proves
	// the card holds the secret it ratcheted to. Otherwise the MAC is keyed
	// with the secret the request MAC matched, the one the ratchet starts
	// from.
	responseMACUnderNextSecret bool
}

var mockFirmwareByGenerationV3 = map[MockFirmwareGenerationV3]mockFirmwareV3{
	MockFirmwareGenerationV3_0: {
		version:                    [VersionSizeV3]byte{0x00, 0x03, 0x00, 0x00},
		selectResponseSize:         selectResponseVersionOnlySizeV3,
		newMetadataApplet:          newMockMetadataAppletV3_0,
		responseMACUnderNextSecret: false,
	},
	MockFirmwareGenerationV3_1: {
		version:                    [VersionSizeV3]byte{0x00, 0x03, 0x00, 0x01},
		selectResponseSize:         SelectResponseWithDeviceIDSizeV3,
		newMetadataApplet:          newMockMetadataAppletV3_1,
		responseMACUnderNextSecret: true,
	},
}
