package nfc

import (
	"encoding/hex"
	"log/slog"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
)

// NullByte is the zero value used for unused APDU header bytes.
const NullByte = 0x00

// Cla stands for Instruction Class. It is a Javacard convention to categorize instructions. You
// usually use CLA 0x00 for common ISO7816-4 commands like SELECT (for applet selection) and 0x80 -
// 0xFF for applet-specific or Javacard proprietary commands.
const cla = 0x00

// CLAEdge is our custom Instruction Class for applet-specific commands (0x80-0xFF range).
const CLAEdge = 0x80

// InsSelect is the Instruction code sent to the card selecting the applet to operate with.
// Most of the instructions are described in the ISO7816 interface document.
// https://docs.oracle.com/en/java/javacard/3.2/jcapi/api_classic/javacard/framework/ISO7816.html
const insSelect = 0xA4

// Standard JavaCard response status words.
const (
	// ResponseOK reports that the command succeeded.
	ResponseOK = 0x9000
	// SWINSNotSupported reports that the applet does not support the instruction.
	SWINSNotSupported = 0x6D00
)

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

func NewJavaCard(nfcBridge app_provided_data.NfcBridge) *JavaCard {
	return &JavaCard{nfcBridge: nfcBridge}
}

// SelectApplet sends the ISO select apdu command with the specified AppletId to this JavaCard,
// and returns whatever the applet answered the selection with.
//
// maxResponseSize is how much room to make for that answer, and 0 makes none.
// Whether an applet answers at all is the applet's own business: the V3 one
// sends its payload only when the room holds it whole. The bytes come back
// exactly as they arrived, for the caller that knows the applet to read.
func (c *JavaCard) SelectApplet(
	appletID string,
	maxResponseSize int,
) ([]byte, error) {

	selectAPDU, err := NewSelectAPDU(appletID, maxResponseSize)
	if err != nil {
		return nil, errors.Errorf("couldn't build select apdu command: %w", err)
	}

	initialResponse, err := c.transmit(selectAPDU.SerializeShort())
	if err != nil {
		return nil, errors.Errorf("couldn't transmit select apdu command: %w", err)
	}

	if initialResponse.StatusCode != ResponseOK {
		slog.Warn(
			"unknown response to select apdu command",
			slog.Any("response", hex.EncodeToString(initialResponse.Response)),
		)
		return nil, errors.Errorf(
			"ISO app select failed, status code %x", initialResponse.StatusCode,
		)
	}

	return initialResponse.Response, nil
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
