package nfc

import (
	"context"
	"encoding/hex"
	"fmt"
	"log/slog"

	"github.com/muun/libwallet/app_provided_data"
)

// MuuncardV3AppletID identifies the V3 applet inside the card, used by
// the JavaCard SELECT command to route subsequent APDUs to it.
const MuuncardV3AppletID = "A00000015100133B00"

// MuunCardV3 is the long-lived client wired with the NFC bridge. It does
// not talk to the card until Connect() is called. Instantiate once per
// app and call Connect() for each NFC session.
type MuunCardV3 struct {
	rawCard *JavaCard
}

// NewCardV3 builds a MuunCardV3 wired to the given NFC bridge. The
// returned client is long-lived and does not talk to the card until
// Connect is called.
func NewCardV3(nfcBridge app_provided_data.NfcBridge) *MuunCardV3 {
	return &MuunCardV3{rawCard: NewJavaCard(nfcBridge)}
}

// Connect opens an NFC session by selecting the V3 applet on the card.
// Returns a *MuunCardV3Session to send subsequent APDUs without
// re-selecting. Call once per NFC session (typically once per logical
// operation like Pair or SignChallenge).
//
// The selection asks for as much room as the short form offers, so whatever
// the applet answers with arrives whole no matter which firmware generation
// sent it, and the session carries it. An answer this client cannot read is a
// firmware it does not know, and it stops here with ErrUnknownFirmware rather
// than send that firmware commands meant for another one. No answer at all
// gets the same treatment: every firmware in the field names itself.
func (c *MuunCardV3) Connect(_ context.Context) (*MuunCardV3Session, error) {
	bytesResponse, err := c.rawCard.SelectApplet(MuuncardV3AppletID, MaxShortAPDUResponseSize)
	if err != nil {
		return nil, newCardError(
			ErrAppletIDNotFound,
			"error selecting muuncard v3 applet",
		)
	}

	selectResponse, err := ParseSelectResponseV3(bytesResponse)
	if err != nil {
		return nil, newCardError(
			ErrUnknownFirmware,
			fmt.Sprintf("unreadable selection answer %x: %s", bytesResponse, err),
		)
	}
	var shortDeviceID []byte
	if selectResponse.ShortDeviceID != nil {
		shortDeviceID = selectResponse.ShortDeviceID[:]
	}
	slog.Info(
		"Selected V3 applet",
		"firmwareVersion", hex.EncodeToString(selectResponse.FirmwareVersion[:]),
		"shortDeviceID", hex.EncodeToString(shortDeviceID),
	)

	return &MuunCardV3Session{
		rawCard:        c.rawCard,
		selectResponse: selectResponse,
	}, nil
}
