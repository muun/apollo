package nfc

import (
	"testing"

	"github.com/stretchr/testify/require"
)

func TestSerializeShort_NoData(t *testing.T) {
	a := newAPDU(0x80, 0x70, 0x00, 0x00, []byte{})
	require.Equal(t, []byte{0x80, 0x70, 0x00, 0x00, 0x00}, a.serializeShort())
}

func TestSerializeShort_WithData(t *testing.T) {
	a := newAPDU(0x80, 0x10, 0x00, 0x00, []byte{0xAA, 0xBB, 0xCC})
	require.Equal(
		t,
		[]byte{0x80, 0x10, 0x00, 0x00, 0x03, 0xAA, 0xBB, 0xCC},
		a.serializeShort(),
	)
}

func TestSerializeShort_MaxData(t *testing.T) {
	data := make([]byte, MaxShortApduDataSize)
	for i := range data {
		data[i] = byte(i)
	}
	a := newAPDU(0x80, 0x10, 0x00, 0x00, data)

	got := a.serializeShort()
	require.Len(t, got, 5+MaxShortApduDataSize)
	require.Equal(t, byte(0xFF), got[4]) // Lc = 255
	require.Equal(t, data, got[5:])
}

func TestSerializeShort_OverflowPanics(t *testing.T) {
	data := make([]byte, MaxShortApduDataSize+1)
	a := newAPDU(0x80, 0x10, 0x00, 0x00, data)
	require.PanicsWithError(
		t,
		"short APDU data 256 exceeds 255 bytes",
		func() { a.serializeShort() },
	)
}

func TestSerializeExtended_NoData(t *testing.T) {
	// Case 2E: CLA INS P1 P2 | 0x00 LeHi LeLo (7 bytes)
	a := newAPDU(0x80, 0x70, 0x00, 0x00, []byte{})
	require.Equal(
		t,
		[]byte{0x80, 0x70, 0x00, 0x00, 0x00, 0x7F, 0xFF},
		a.serializeExtended(),
	)
}

func TestSerializeExtended_WithData(t *testing.T) {
	// Case 4E: CLA INS P1 P2 | 0x00 LcHi LcLo | Data | LeHi LeLo
	data := []byte{0xAA, 0xBB, 0xCC}
	a := newAPDU(0x80, 0x10, 0x00, 0x00, data)
	require.Equal(
		t,
		[]byte{
			0x80, 0x10, 0x00, 0x00,
			0x00, 0x00, 0x03, // extended Lc = 3
			0xAA, 0xBB, 0xCC, // data
			0x7F, 0xFF, // extended Le = 32767
		},
		a.serializeExtended(),
	)
}

func TestSerializeExtended_LcSpanning256(t *testing.T) {
	// Verify LcHi/LcLo encoding across the short-APDU boundary.
	data := make([]byte, 300)
	for i := range data {
		data[i] = byte(i % 256)
	}
	a := newAPDU(0x80, 0x10, 0x00, 0x00, data)

	got := a.serializeExtended()
	require.Len(t, got, 4+3+300+2)
	require.Equal(t, byte(0x00), got[4])              // extended marker
	require.Equal(t, byte(0x01), got[5])              // LcHi = 300 >> 8 = 1
	require.Equal(t, byte(0x2C), got[6])              // LcLo = 300 & 0xFF = 44
	require.Equal(t, data, got[7:7+300])              // data
	require.Equal(t, []byte{0x7F, 0xFF}, got[7+300:]) // Le
}

func TestSerializeExtended_MaxData(t *testing.T) {
	data := make([]byte, MaxExtendedApduSupportedLength)
	a := newAPDU(0x80, 0x10, 0x00, 0x00, data)

	got := a.serializeExtended()
	require.Len(t, got, 4+3+MaxExtendedApduSupportedLength+2)
	require.Equal(t, byte(0x7F), got[5]) // LcHi
	require.Equal(t, byte(0xFF), got[6]) // LcLo
}

func TestSerializeExtended_OverflowPanics(t *testing.T) {
	data := make([]byte, MaxExtendedApduSupportedLength+1)
	a := newAPDU(0x80, 0x10, 0x00, 0x00, data)
	require.PanicsWithError(
		t,
		"extended APDU data 32768 exceeds 32767 bytes",
		func() { a.serializeExtended() },
	)
}
