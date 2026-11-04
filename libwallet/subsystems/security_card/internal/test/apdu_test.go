package test

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

func TestSerializeShort_NoData(t *testing.T) {
	a := nfc.NewAPDU(0x80, 0x70, 0x00, 0x00, []byte{})
	require.Equal(t, []byte{0x80, 0x70, 0x00, 0x00, 0x00}, a.SerializeShort())
}

func TestSerializeShort_WithData(t *testing.T) {
	a := nfc.NewAPDU(0x80, 0x10, 0x00, 0x00, []byte{0xAA, 0xBB, 0xCC})
	require.Equal(
		t,
		[]byte{0x80, 0x10, 0x00, 0x00, 0x03, 0xAA, 0xBB, 0xCC},
		a.SerializeShort(),
	)
}

func TestSerializeShort_MaxData(t *testing.T) {
	data := make([]byte, nfc.MaxShortAPDUDataSize)
	for i := range data {
		data[i] = byte(i)
	}
	a := nfc.NewAPDU(0x80, 0x10, 0x00, 0x00, data)

	got := a.SerializeShort()
	require.Len(t, got, 5+nfc.MaxShortAPDUDataSize)
	require.Equal(t, byte(0xFF), got[4]) // Lc = 255
	require.Equal(t, data, got[5:])
}

func TestSerializeShort_OverflowPanics(t *testing.T) {
	data := make([]byte, nfc.MaxShortAPDUDataSize+1)
	a := nfc.NewAPDU(0x80, 0x10, 0x00, 0x00, data)
	require.PanicsWithError(
		t,
		"short APDU data 256 exceeds 255 bytes",
		func() { a.SerializeShort() },
	)
}

func TestNewSelectAPDU_MakesNoRoomForAResponse(t *testing.T) {
	// The Le-less form, for a selection that makes no room for an answer.
	a, err := nfc.NewSelectAPDU("A00000015100133B00", 0)
	require.NoError(t, err)

	require.Equal(
		t,
		[]byte{
			0x00, 0xA4, 0x04, 0x00,
			0x09, // Lc
			0xA0, 0x00, 0x00, 0x01, 0x51, 0x00, 0x13, 0x3B, 0x00,
		},
		a.SerializeShort(),
	)
}

func TestNewSelectAPDU_SpellsTheLargestResponseAsZero(t *testing.T) {
	a, err := nfc.NewSelectAPDU("A00000015100133B00", nfc.MaxShortAPDUResponseSize)
	require.NoError(t, err)

	require.Equal(
		t,
		[]byte{
			0x00, 0xA4, 0x04, 0x00,
			0x09, // Lc
			0xA0, 0x00, 0x00, 0x01, 0x51, 0x00, 0x13, 0x3B, 0x00,
			0x00, // Le, which the short form reads as 256
		},
		a.SerializeShort(),
	)
}

func TestNewSelectAPDU_CarriesASmallerResponseSizeAsItself(t *testing.T) {
	a, err := nfc.NewSelectAPDU("A00000015100133B00", 16)
	require.NoError(t, err)

	got := a.SerializeShort()
	require.Equal(t, byte(0x10), got[len(got)-1])
}

func TestNewSelectAPDU_RejectsANonHexAppletID(t *testing.T) {
	_, err := nfc.NewSelectAPDU("not hex", 0)
	require.Error(t, err)
}

func TestSerializeShort_ResponseOverflowPanics(t *testing.T) {
	a := nfc.NewAPDUWithLe(
		0x80, 0x10, 0x00, 0x00, []byte{0xAA}, nfc.MaxShortAPDUResponseSize+1,
	)

	require.PanicsWithError(
		t,
		"short APDU response 257 exceeds 256 bytes",
		func() { a.SerializeShort() },
	)
}

func TestSerializeShort_ResponseWithoutDataPanics(t *testing.T) {
	a := nfc.NewAPDUWithLe(0x80, 0x10, 0x00, 0x00, []byte{}, 16)

	require.PanicsWithError(
		t,
		"short APDU asks for a response but carries no data",
		func() { a.SerializeShort() },
	)
}

func TestSerializeExtended_NoData(t *testing.T) {
	// Case 2E: CLA INS P1 P2 | 0x00 LeHi LeLo (7 bytes)
	a := nfc.NewAPDU(0x80, 0x70, 0x00, 0x00, []byte{})
	require.Equal(
		t,
		[]byte{0x80, 0x70, 0x00, 0x00, 0x00, 0x7F, 0xFF},
		a.SerializeExtended(),
	)
}

func TestSerializeExtended_WithData(t *testing.T) {
	// Case 4E: CLA INS P1 P2 | 0x00 LcHi LcLo | Data | LeHi LeLo
	data := []byte{0xAA, 0xBB, 0xCC}
	a := nfc.NewAPDU(0x80, 0x10, 0x00, 0x00, data)
	require.Equal(
		t,
		[]byte{
			0x80, 0x10, 0x00, 0x00,
			0x00, 0x00, 0x03, // extended Lc = 3
			0xAA, 0xBB, 0xCC, // data
			0x7F, 0xFF, // extended Le = 32767
		},
		a.SerializeExtended(),
	)
}

func TestSerializeExtended_LcSpanning256(t *testing.T) {
	// Verify LcHi/LcLo encoding across the short-APDU boundary.
	data := make([]byte, 300)
	for i := range data {
		data[i] = byte(i % 256)
	}
	a := nfc.NewAPDU(0x80, 0x10, 0x00, 0x00, data)

	got := a.SerializeExtended()
	require.Len(t, got, 4+3+300+2)
	require.Equal(t, byte(0x00), got[4])              // extended marker
	require.Equal(t, byte(0x01), got[5])              // LcHi = 300 >> 8 = 1
	require.Equal(t, byte(0x2C), got[6])              // LcLo = 300 & 0xFF = 44
	require.Equal(t, data, got[7:7+300])              // data
	require.Equal(t, []byte{0x7F, 0xFF}, got[7+300:]) // Le
}

func TestSerializeExtended_MaxData(t *testing.T) {
	data := make([]byte, nfc.MaxExtendedAPDUSupportedLength)
	a := nfc.NewAPDU(0x80, 0x10, 0x00, 0x00, data)

	got := a.SerializeExtended()
	require.Len(t, got, 4+3+nfc.MaxExtendedAPDUSupportedLength+2)
	require.Equal(t, byte(0x7F), got[5]) // LcHi
	require.Equal(t, byte(0xFF), got[6]) // LcLo
}

func TestSerializeExtended_OverflowPanics(t *testing.T) {
	data := make([]byte, nfc.MaxExtendedAPDUSupportedLength+1)
	a := nfc.NewAPDU(0x80, 0x10, 0x00, 0x00, data)
	require.PanicsWithError(
		t,
		"extended APDU data 32768 exceeds 32767 bytes",
		func() { a.SerializeExtended() },
	)
}
