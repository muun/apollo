package nfc

import (
	"encoding/hex"
	"fmt"
	"github.com/muun/libwallet/app_provided_data"
	"strings"
)

type JavaCardApplet interface {
	getAppletId() string
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

	if len(data) != dataSize {
		return nil, fmt.Errorf("invalid apdu: expected %v data bytes, got %v", dataSize, len(data))
	}

	// Handle deselect applet
	if dataSize == 0 {
		// return FCI template, contains several internal OS stuff like A000000151000000 (the ID of
		// globalplatform).
		return newSuccessResponse([]byte("6F108408A000000151000000A5049F6501FF")), nil
	}

	appletId := hex.EncodeToString(data)
	if strings.ToUpper(appletId) != m.muuncardApplet.getAppletId() {
		return nil, fmt.Errorf("incorrect applet id: %s", appletId)
	}

	// Return some internal OS stuff + "muun.com" in hex (e.g. 6D75756E2E636F6D).
	return newSuccessResponse([]byte("D1010855046D75756E2E636F6D")), nil
}
