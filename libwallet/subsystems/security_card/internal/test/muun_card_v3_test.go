package test

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/base/domain/cryptography"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

// Connect is where the selection's answer turns into something the session
// can hand out, so what it makes of each shape the parser accepts is pinned
// here. That a shape the parser refuses reaches the user as an unsupported
// card is covered end to end by the sign flow's own test.

func TestMuunCardV3Connect_CarriesWhatTheAppletAnswered(t *testing.T) {
	muun, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)
	applet, err := nfc.NewMockMuunCardV3(muun.PublicKey, nfc.MockFirmwareGenerationV3_1)
	require.NoError(t, err)

	session, err := nfc.NewCardV3(nfc.NewMockJavaCard(applet)).Connect(t.Context())
	require.NoError(t, err)

	response := session.SelectResponse()
	require.NotNil(t, response)
	require.Equal(t, applet.FirmwareVersion(), response.FirmwareVersion)
	shortDeviceID := applet.ShortDeviceID()
	require.Equal(t, &shortDeviceID, response.ShortDeviceID)
}

func TestMuunCardV3Connect_CarriesAnAnswerWithoutAShortDeviceID(t *testing.T) {
	// Firmware 3.0 is from before the short device id, so the answer has
	// none to carry.
	muun, err := cryptography.GenerateSecp256r1PKeyPair()
	require.NoError(t, err)
	applet, err := nfc.NewMockMuunCardV3(muun.PublicKey, nfc.MockFirmwareGenerationV3_0)
	require.NoError(t, err)

	session, err := nfc.NewCardV3(nfc.NewMockJavaCard(applet)).Connect(t.Context())
	require.NoError(t, err)

	response := session.SelectResponse()
	require.NotNil(t, response)
	require.Equal(t, applet.FirmwareVersion(), response.FirmwareVersion)
	require.Nil(t, response.ShortDeviceID)
}

func TestMuunCardV3Connect_RefusesAnAppletThatSaidNothing(t *testing.T) {
	// The parser refuses silence; this pins that Connect reports it as a
	// firmware this client does not know, the same as any other answer it
	// cannot read.
	session, err := nfc.NewCardV3(silentSelectBridge{}).Connect(t.Context())
	require.Nil(t, session)

	var cardError *nfc.CardError
	require.ErrorAs(t, err, &cardError)
	require.Equal(t, nfc.ErrUnknownFirmware, cardError.Code)
}

// silentSelectBridge answers with an empty success, which is all Connect
// needs: it sends one selection and reads nothing else.
type silentSelectBridge struct{}

func (silentSelectBridge) Transmit(_ []byte) (*app_provided_data.NfcBridgeResponse, error) {
	return &app_provided_data.NfcBridgeResponse{
		Response:   nil,
		StatusCode: nfc.ResponseOK,
	}, nil
}
