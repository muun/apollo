package nfc

import (
	"crypto/rand"
	"encoding/hex"
	"strings"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
)

type JavaCardApplet interface {
	getAppletID() string
	processCommand(apdu []byte) (*app_provided_data.NfcBridgeResponse, error)
}

type MockJavaCard struct {
	muuncardApplet JavaCardApplet
}

// Enforce we implement the interface
var _ app_provided_data.NfcBridge = (*MockJavaCard)(nil)

func NewMockJavaCard(muuncardApplet JavaCardApplet) *MockJavaCard {
	return &MockJavaCard{
		muuncardApplet: muuncardApplet,
	}
}

func (m *MockJavaCard) Transmit(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {
	return m.processCommand(apdu)
}

func (m *MockJavaCard) processCommand(apdu []byte) (*app_provided_data.NfcBridgeResponse, error) {
	ins := apdu[iso7816OffsetIns]

	switch ins {
	case insSelect:
		return m.handleSelectApplet(apdu)
	default:
		return m.muuncardApplet.processCommand(apdu)
	}
}

func (m *MockJavaCard) handleSelectApplet(apdu []byte) (
	*app_provided_data.NfcBridgeResponse,
	error,
) {
	dataSize := int(apdu[iso7816OffsetLc])
	data := apdu[iso7816OffsetCData:]

	// A SELECT may carry a one-byte Le after the AID; the applet decides what
	// to make of it.
	if len(data) != dataSize && len(data) != dataSize+1 {
		return nil, errors.Errorf(
			"invalid apdu: expected %v data bytes, got %v", dataSize, len(data),
		)
	}

	// Handle deselect applet
	if dataSize == 0 {
		// return FCI template, contains several internal OS stuff like A000000151000000 (the ID of
		// globalplatform).
		return newSuccessResponse([]byte("6F108408A000000151000000A5049F6501FF")), nil
	}

	appletID := hex.EncodeToString(data[:dataSize])
	if strings.ToUpper(appletID) != m.muuncardApplet.getAppletID() {
		return nil, errors.Errorf("incorrect applet id: %s", appletID)
	}

	// The card OS hands the SELECT to the applet it picked, and the applet
	// answers it, as the firmware does in process() when selectingApplet().
	return m.muuncardApplet.processCommand(apdu)
}

func newSuccessResponse(responseBytes []byte) *app_provided_data.NfcBridgeResponse {
	return &app_provided_data.NfcBridgeResponse{
		Response:   responseBytes,
		StatusCode: ResponseOK,
	}
}

func newErrorResponse(statusCode int32) *app_provided_data.NfcBridgeResponse {
	return &app_provided_data.NfcBridgeResponse{
		Response:   nil,
		StatusCode: statusCode,
	}
}

// RandomBytes returns count cryptographically random bytes, panicking if they cannot be read.
func RandomBytes(count int) []byte {
	buf := make([]byte, count)
	_, err := rand.Read(buf)
	if err != nil {
		panic("couldn't read random bytes")
	}

	return buf
}
