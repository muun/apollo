package nfc

import (
	"encoding/hex"
)

// apdu message structure as per documentation:
// https://www.cardlogix.com/glossary/apdu-application-protocol-data-unit-smart-card/
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
func newSelectAPDU(appletId string) (*apdu, error) {
	initByteCode, err := hex.DecodeString(appletId)
	if err != nil {
		return nil, err
	}

	return newAPDU(cla, insSelect, 4, 0, initByteCode), nil
}

func (a *apdu) serialize() []byte {
	return append([]byte{a.cls, a.ins, a.p1, a.p2, byte(len(a.data))}, a.data...)
}
