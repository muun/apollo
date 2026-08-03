package nfc

import (
	"encoding/hex"

	"github.com/go-errors/errors"
)

// ISO 7816-4 length limits for the two APDU wire formats.
const (
	// MaxShortApduDataSize is the maximum data field the short form can
	// carry: Lc is a single byte.
	MaxShortApduDataSize = 255

	// MaxExtendedApduSupportedLength caps the Lc/Le fields of an extended
	// APDU at 32767. This is our card's limit (JCOP stores them as signed
	// 16-bit shorts and rejects larger values with 0x6700), not an ISO
	// 7816-4 one.
	MaxExtendedApduSupportedLength = 0x7FFF
)

// apdu message structure as per documentation:
// https://www.cardlogix.com/glossary/apdu-application-protocol-data-unit-smart-card/
//
// Two wire formats are supported via serializeShort and serializeExtended;
// the caller picks based on the target applet's ISO 7816-4 capabilities.
type apdu struct {
	cls  byte   // Class of instruction
	ins  byte   // Instruction code
	p1   byte   // Instruction parameter 1
	p2   byte   // Instruction parameter 2
	data []byte // String of bytes sent in the data field of the command
}

func newAPDU(cls byte, ins byte, p1 byte, p2 byte, data []byte) *apdu {
	return &apdu{cls: cls, ins: ins, p1: p1, p2: p2, data: data}
}

// newSelectAPDU builds the ISO select apdu to pick the applet. [00 a4 04 00 (appletId)].
// This is required to get started.
func newSelectAPDU(
	appletId string, //nolint:staticcheck // TODO: func parameter appletId should be appletID
) (*apdu, error) {
	initByteCode, err := hex.DecodeString(appletId)
	if err != nil {
		return nil, err
	}

	return newAPDU(cla, insSelect, 4, 0, initByteCode), nil
}

// serializeShort produces an ISO 7816-4 short-form APDU on the wire:
//
//	CLA INS P1 P2 Lc Data
//
// Lc is a single byte, so data must be at most 255 bytes. Data larger than
// that is a caller bug (short-only applets can't send it); panic instead of
// silently truncating.
func (a *apdu) serializeShort() []byte {
	if len(a.data) > MaxShortApduDataSize {
		panic(errors.Errorf(
			"short APDU data %d exceeds %d bytes",
			len(a.data), MaxShortApduDataSize,
		))
	}
	return append([]byte{a.cls, a.ins, a.p1, a.p2, byte(len(a.data))}, a.data...)
}

// serializeExtended produces an ISO 7816-4 extended-length APDU on the wire.
// Two shapes depending on whether data is present:
//
//	No data (case 2E):  CLA INS P1 P2 | 0x00 LeHi LeLo
//	With data (case 4E): CLA INS P1 P2 | 0x00 LcHi LcLo | Data | LeHi LeLo
//
// Le is fixed to MaxExtendedApduSupportedLength (0x7FFF), the largest response the
// card accepts (Java Card caps Ne at a signed short, 0..32767). Requesting
// 0xFFFF would make the JCRE reject the APDU with 0x6700. The card still
// returns only what it has. Command data above the same 32767 cap panics.
func (a *apdu) serializeExtended() []byte {
	if len(a.data) > MaxExtendedApduSupportedLength {
		panic(errors.Errorf(
			"extended APDU data %d exceeds %d bytes",
			len(a.data), MaxExtendedApduSupportedLength,
		))
	}
	header := []byte{a.cls, a.ins, a.p1, a.p2}
	leHi, leLo := byte(MaxExtendedApduSupportedLength>>8), byte(MaxExtendedApduSupportedLength&0xFF)
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
