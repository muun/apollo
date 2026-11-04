package nfc

import (
	"encoding/binary"
	"encoding/hex"
	"fmt"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
)

// ShortDeviceIDSizeV3 is device_model_id(8) || 4 bytes that tell a card of
// that model from another. Which bytes those are is the firmware
// generation's, and its certificate is what attests them.
const ShortDeviceIDSizeV3 = 12

// The shapes a V3 selection answer comes in, by firmware generation. Each
// one extends the last, so a length names a generation.
const (
	selectResponseVersionOnlySizeV3  = VersionSizeV3
	SelectResponseWithDeviceIDSizeV3 = VersionSizeV3 + ShortDeviceIDSizeV3
)

// SelectResponseV3 is what the V3 applet answers the ISO SELECT with, before
// anything has been authenticated.
//
// Wire layout, by firmware generation:
//
//	firmware_version(4)                        the first firmware to answer
//	firmware_version(4) || short_device_id(12) from the next one on
type SelectResponseV3 struct {
	// FirmwareVersion is major(u16be) || minor(u16be), the same field the
	// metadata carries.
	FirmwareVersion [VersionSizeV3]byte

	// ShortDeviceID names the card before anything is authenticated, laid out
	// as ShortDeviceIDSizeV3 describes. Nil when the firmware is the previous
	// one.
	ShortDeviceID *[ShortDeviceIDSizeV3]byte
}

// NewSelectResponseV3 builds the answer from its fields. shortDeviceID is nil
// for a firmware that does not send one.
func NewSelectResponseV3(
	firmwareVersion [VersionSizeV3]byte,
	shortDeviceID *[ShortDeviceIDSizeV3]byte,
) *SelectResponseV3 {
	return &SelectResponseV3{
		FirmwareVersion: firmwareVersion,
		ShortDeviceID:   shortDeviceID,
	}
}

// ShortDeviceIDInHex returns the short device id as the client records it,
// matches it against the challenge payloads and names the card in the sign
// submit, or an empty string when the firmware reports none.
func (r *SelectResponseV3) ShortDeviceIDInHex() string {
	if r.ShortDeviceID == nil {
		return ""
	}
	return hex.EncodeToString(r.ShortDeviceID[:])
}

// FirmwareVersionName returns the firmware version the way people name it,
// major.minor ("3.1"), for the native layer and the traces to report.
func (r *SelectResponseV3) FirmwareVersionName() string {
	major := binary.BigEndian.Uint16(r.FirmwareVersion[:2])
	minor := binary.BigEndian.Uint16(r.FirmwareVersion[2:])
	return fmt.Sprintf("%d.%d", major, minor)
}

// PairedCard returns the card as this answer names it, the way a pairing records it: the
// firmware version in hex and, from the firmware that reports one, the short device id.
func (r *SelectResponseV3) PairedCard() *model.PairedCard {
	return model.NewPairedCard(hex.EncodeToString(r.FirmwareVersion[:]), r.ShortDeviceIDInHex())
}

// ParseSelectResponseV3 reads the selection answer, routing on its length so
// that one client reads every firmware generation without being told which
// one it tapped.
//
// An empty answer is refused. Firmware from before the payload answers that
// way, and no card in the field runs it. Current firmware also answers with
// nothing when the card is not READY or cannot reach its metadata applet,
// and such a card cannot pair either.
//
// Only the lengths a known firmware sends are read. A prefix reading would
// take any four leading bytes for a version, and a card OS answering the
// selection with something of its own would pass as one.
func ParseSelectResponseV3(data []byte) (*SelectResponseV3, error) {
	if len(data) == 0 {
		return nil, errors.New("the applet reported no firmware version")
	}

	var firmwareVersion [VersionSizeV3]byte
	var shortDeviceID *[ShortDeviceIDSizeV3]byte

	switch len(data) {
	case selectResponseVersionOnlySizeV3:
		copy(firmwareVersion[:], data)
	case SelectResponseWithDeviceIDSizeV3:
		copy(firmwareVersion[:], data[:VersionSizeV3])
		shortDeviceID = new([ShortDeviceIDSizeV3]byte)
		copy(shortDeviceID[:], data[VersionSizeV3:])
	default:
		return nil, errors.Errorf(
			"select response is %d bytes; a V3 applet answers with %d or %d",
			len(data), selectResponseVersionOnlySizeV3, SelectResponseWithDeviceIDSizeV3,
		)
	}

	return NewSelectResponseV3(firmwareVersion, shortDeviceID), nil
}
