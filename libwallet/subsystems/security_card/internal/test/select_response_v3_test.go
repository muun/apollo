package test

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

// One client has to read every firmware generation's answer, so the parser
// is tested against each shape the wire has had, plus what it must refuse.

func TestParseSelectResponseV3_RefusesNothingSaid(t *testing.T) {
	// Silence is what firmware from before the payload answers, and what a
	// relay that drops the payload makes any firmware look like. No card in
	// the field answers that way, so it is refused rather than read as a card
	// with no version.
	response, err := nfc.ParseSelectResponseV3(nil)

	require.Error(t, err)
	require.Nil(t, response)
}

func TestParseSelectResponseV3_ReadsAVersionOnlyAnswer(t *testing.T) {
	response, err := nfc.ParseSelectResponseV3([]byte{0x00, 0x03, 0x00, 0x00})

	require.NoError(t, err)
	require.Equal(t, [4]byte{0x00, 0x03, 0x00, 0x00}, response.FirmwareVersion)
	require.Nil(t, response.ShortDeviceID)
}

func TestParseSelectResponseV3_ReadsAnAnswerWithADeviceID(t *testing.T) {
	deviceID := [nfc.ShortDeviceIDSizeV3]byte{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}
	data := append([]byte{0x00, 0x03, 0x00, 0x01}, deviceID[:]...)

	response, err := nfc.ParseSelectResponseV3(data)

	require.NoError(t, err)
	require.Equal(t, [4]byte{0x00, 0x03, 0x00, 0x01}, response.FirmwareVersion)
	require.Equal(t, &deviceID, response.ShortDeviceID)
}

func TestParseSelectResponseV3_CopiesTheDeviceIDOutOfTheBuffer(t *testing.T) {
	data := append([]byte{0x00, 0x03, 0x00, 0x01}, make([]byte, nfc.ShortDeviceIDSizeV3)...)

	response, err := nfc.ParseSelectResponseV3(data)
	require.NoError(t, err)

	data[nfc.VersionSizeV3] = 0xFF

	require.Equal(t, byte(0x00), response.ShortDeviceID[0])
}

func TestParseSelectResponseV3_RefusesALengthNoFirmwareSends(t *testing.T) {
	for _, length := range []int{1, 3, 5, 15, 17, 32} {
		_, err := nfc.ParseSelectResponseV3(make([]byte, length))

		require.Error(t, err, "length %d", length)
	}
}

func TestSelectResponseV3_NamesTheFirmwareVersionAsMajorDotMinor(t *testing.T) {
	// Each half is a big-endian u16, so a byte past the low one counts too.
	response := nfc.NewSelectResponseV3([nfc.VersionSizeV3]byte{0x01, 0x03, 0x00, 0x0A}, nil)

	require.Equal(t, "259.10", response.FirmwareVersionName())
}
