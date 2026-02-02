package nfc

import (
	"encoding/hex"
	"fmt"
	"github.com/muun/libwallet/app_provided_data"
	"log/slog"
)

const nullByte = 0x00

// Cla stands for Instruction Class. It is a Javacard convention to categorize instructions. You
// usually use CLA 0x00 for common ISO7816-4 commands like SELECT (for applet selection) and 0x80 -
// 0xFF for applet-specific or Javacard proprietary commands.
const cla = 0x00

// InsSelect is the Instruction code sent to the card selecting the applet to operate with.
// Most of the instructions are described in the ISO7816 interface document.
// https://docs.oracle.com/en/java/javacard/3.2/jcapi/api_classic/javacard/framework/ISO7816.html
const insSelect = 0xA4

// Standard JavaCard response status words
const responseOk = 0x9000
const swWrongData = 0x6A80
const swInsNotSupported = 0x6D00

const (
	iso7816OffsetIns   = 1
	iso7816OffsetP1    = 2
	iso7816OffsetP2    = 3
	iso7816OffsetLc    = 4
	iso7816OffsetCData = 5

	hmacSha1SizeInBytes = 20
)

type JavaCard struct {
	nfcBridge app_provided_data.NfcBridge
}

type CardResponse struct {
	Response   []byte
	StatusCode uint16
}

func newJavaCard(nfcBridge app_provided_data.NfcBridge) *JavaCard {
	return &JavaCard{nfcBridge: nfcBridge}
}

// selectApplet sends the ISO select apdu command with the specified AppletId to this JavaCard
func (c *JavaCard) selectApplet(appletId string) error {

	selectAPDU, err := newSelectAPDU(appletId)
	if err != nil {
		return fmt.Errorf("couldn't build select apdu command: %w", err)
	}

	initialResponse, err := c.transmit(selectAPDU.serialize())
	if err != nil {
		return fmt.Errorf("couldn't transmit select apdu command: %w", err)
	}

	if initialResponse.StatusCode != responseOk {
		slog.Warn(
			"unknown response to select apdu command",
			slog.Any("response", hex.EncodeToString(initialResponse.Response)),
		)
		return fmt.Errorf("ISO app select failed, status code %x", initialResponse.StatusCode)
	}

	return nil
}

func (c *JavaCard) transmit(message []byte) (*CardResponse, error) {
	resp, err := c.nfcBridge.Transmit(message)
	if err != nil {
		return nil, err
	}

	// TODO move status and response parsing from native code here
	return &CardResponse{Response: resp.Response, StatusCode: uint16(resp.StatusCode)}, err
	//status := uint16(resp[len(resp)-2])<<8 | uint16(resp[len(resp)-1])
	//response := resp[:len(resp)-2] // Remove the last two bytes that correspond to the status word
	//return &CardResponse{Response: response, StatusCode: status}, err
}
