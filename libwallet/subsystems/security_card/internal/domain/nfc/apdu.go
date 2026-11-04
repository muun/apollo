package nfc

import (
	"encoding/hex"

	"github.com/go-errors/errors"
)

// ISO 7816-4 length limits for the two APDU wire formats.
const (
	// MaxShortAPDUDataSize is the maximum data field the short form can
	// carry: Lc is a single byte.
	MaxShortAPDUDataSize = 255

	// MaxShortAPDUResponseSize is the largest response the short form can
	// ask for: Le is a single byte, and 0x00 stands for 256 rather than
	// for none.
	MaxShortAPDUResponseSize = 256

	// MaxExtendedAPDUSupportedLength caps the Lc/Le fields of an extended
	// APDU at 32767. This is our card's limit (JCOP stores them as signed
	// 16-bit shorts and rejects larger values with 0x6700), not an ISO
	// 7816-4 one.
	MaxExtendedAPDUSupportedLength = 0x7FFF
)

// APDU is the message structure as per documentation:
// https://www.cardlogix.com/glossary/apdu-application-protocol-data-unit-smart-card/
//
// Two wire formats are supported via SerializeShort and SerializeExtended;
// the caller picks based on the target applet's ISO 7816-4 capabilities.
type APDU struct {
	cls  byte   // Class of instruction
	ins  byte   // Instruction code
	p1   byte   // Instruction parameter 1
	p2   byte   // Instruction parameter 2
	data []byte // String of bytes sent in the data field of the command

	// maxResponseSize is how much room the command makes for the card's
	// answer, as the short form's Le byte; 0 leaves it off the wire.
	maxResponseSize int
}

// NewAPDU creates an APDU command from its header bytes and data field.
func NewAPDU(cls byte, ins byte, p1 byte, p2 byte, data []byte) *APDU {
	return NewAPDUWithLe(cls, ins, p1, p2, data, 0)
}

// NewAPDUWithLe builds a command that makes room for maxResponseSize bytes
// of the card's answer, sent as the short form's Le byte.
func NewAPDUWithLe(
	cls byte,
	ins byte,
	p1 byte,
	p2 byte,
	data []byte,
	maxResponseSize int,
) *APDU {
	return &APDU{
		cls:             cls,
		ins:             ins,
		p1:              p1,
		p2:              p2,
		data:            data,
		maxResponseSize: maxResponseSize,
	}
}

// NewSelectAPDU builds the ISO select apdu to pick the applet. [00 a4 04 00 (appletID)].
// This is required to get started.
//
// maxResponseSize is how many bytes to make room for in the applet's answer
// to the selection. The V3 applet gates that answer on the room it was given,
// so 0 gets 0x9000 and no data, and so does a size too small to hold the
// answer whole.
func NewSelectAPDU(
	appletID string,
	maxResponseSize int,
) (*APDU, error) {
	initByteCode, err := hex.DecodeString(appletID)
	if err != nil {
		return nil, err
	}

	return NewAPDUWithLe(cla, insSelect, 4, 0, initByteCode, maxResponseSize), nil
}

// SerializeShort produces an ISO 7816-4 short-form APDU on the wire. Two
// shapes, depending on whether the command asks for a response:
//
//	No response (case 3S):   CLA INS P1 P2 Lc Data
//	With response (case 4S): CLA INS P1 P2 Lc Data Le
//
// Lc is a single byte, so data must be at most 255 bytes. A response above
// the 256 a single Le byte can ask for, or data above the 255 Lc can
// declare, is a caller bug (short-only applets can't carry either); panic
// instead of silently truncating. So is a response asked for without data:
// that is case 2S, which has no Lc byte, and this always writes one.
func (a *APDU) SerializeShort() []byte {
	if len(a.data) > MaxShortAPDUDataSize {
		panic(errors.Errorf(
			"short APDU data %d exceeds %d bytes",
			len(a.data), MaxShortAPDUDataSize,
		))
	}
	if a.maxResponseSize > MaxShortAPDUResponseSize {
		panic(errors.Errorf(
			"short APDU response %d exceeds %d bytes",
			a.maxResponseSize, MaxShortAPDUResponseSize,
		))
	}
	if a.maxResponseSize > 0 && len(a.data) == 0 {
		panic(errors.New("short APDU asks for a response but carries no data"))
	}

	out := append([]byte{a.cls, a.ins, a.p1, a.p2, byte(len(a.data))}, a.data...)
	if a.maxResponseSize > 0 {
		// The conversion is the encoding: 256 truncates to 0x00, which is
		// exactly what the short form spells 256 as.
		out = append(out, byte(a.maxResponseSize))
	}

	return out
}

// SerializeExtended produces an ISO 7816-4 extended-length APDU on the wire.
// Two shapes depending on whether data is present:
//
//	No data (case 2E):  CLA INS P1 P2 | 0x00 LeHi LeLo
//	With data (case 4E): CLA INS P1 P2 | 0x00 LcHi LcLo | Data | LeHi LeLo
//
// Le is fixed to MaxExtendedAPDUSupportedLength (0x7FFF), the largest response the
// card accepts (Java Card caps Ne at a signed short, 0..32767). Requesting
// 0xFFFF would make the JCRE reject the APDU with 0x6700. The card still
// returns only what it has. Command data above the same 32767 cap panics.
func (a *APDU) SerializeExtended() []byte {
	if len(a.data) > MaxExtendedAPDUSupportedLength {
		panic(errors.Errorf(
			"extended APDU data %d exceeds %d bytes",
			len(a.data), MaxExtendedAPDUSupportedLength,
		))
	}
	header := []byte{a.cls, a.ins, a.p1, a.p2}
	leHi, leLo := byte(MaxExtendedAPDUSupportedLength>>8), byte(MaxExtendedAPDUSupportedLength&0xFF)
	if len(a.data) == 0 {
		// Case 2E: extended Le (3 bytes including the 0x00 marker).
		return append(header, 0x00, leHi, leLo)
	}
	// Case 4E: 0x00 marker + 2-byte Lc, then data, then 2-byte Le.
	lc := []byte{0x00, byte(len(a.data) >> 8), byte(len(a.data))}
	out := append(header, lc...)
	out = append(out, a.data...)
	out = append(out, leHi, leLo)
	return out
}
