package test

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

// Driven through MockJavaCard, which stands in for the card OS: it routes a
// selection to the applet the AID names, and everything after it to the applet
// selected.

func TestJavaCardSelectApplet_ReturnsWhatTheAppletAnswered(t *testing.T) {
	card, applet := newMockedJavaCard(t)

	response, err := card.SelectApplet(nfc.MuuncardV3AppletID, nfc.MaxShortAPDUResponseSize)
	require.NoError(t, err)

	// The V3 applet answers a selection that made room with its firmware
	// version and short device id. What the bytes mean is the client's
	// business; what this pins is that the selection hands them to its caller.
	shortDeviceID := applet.ShortDeviceID()
	require.Equal(t, append([]byte{0x00, 0x03, 0x00, 0x01}, shortDeviceID[:]...), response)
}

func TestJavaCardSelectApplet_ReturnsNothingWhenItAsksForNoRoom(t *testing.T) {
	card, _ := newMockedJavaCard(t)

	response, err := card.SelectApplet(nfc.MuuncardV3AppletID, 0)
	require.NoError(t, err)

	require.Empty(t, response)
}

func TestJavaCardSelectApplet_FailsOnAnUnknownAppletID(t *testing.T) {
	card, _ := newMockedJavaCard(t)

	_, err := card.SelectApplet("A0000001510000FF00", nfc.MaxShortAPDUResponseSize)

	require.Error(t, err)
}

func TestJavaCardSelectApplet_FailsOnAnAppletIDThatIsNotHex(t *testing.T) {
	card, _ := newMockedJavaCard(t)

	_, err := card.SelectApplet("not hex", nfc.MaxShortAPDUResponseSize)

	require.Error(t, err)
}

func newMockedJavaCard(t *testing.T) (*nfc.JavaCard, *nfc.MockMuunCardV3) {
	t.Helper()

	muunKeyPair, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)

	applet, err := nfc.NewMockMuunCardV3(muunKeyPair.PublicKey, nfc.MockFirmwareGenerationV3_1)
	require.NoError(t, err)

	return nfc.NewJavaCard(nfc.NewMockJavaCard(applet)), applet
}
