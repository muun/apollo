package model

import "github.com/muun/libwallet/platform/preconditions"

// PairedCard is the card the sign flow last paired with, as the selection
// that opened its session named it: by the firmware version it reported and,
// from the firmware that reports one, by its short device id. A record a V2
// pairing left holds a marker in place of a version, which no V3 card reports.
//
// It is what the flow compares the tapped card against to decide whether
// it has to pair before it signs. Two cards are the same card when every
// field matches, so a card of the same firmware with another id is another
// card, and a firmware without an id cannot tell its cards apart.
type PairedCard struct {
	// Version is the firmware version the card reported,
	// major(u16be) || minor(u16be) in hex, or the marker of a V2 pairing.
	Version string

	// ShortDeviceIDInHex is the id the card reported at SELECT, in hex. Empty
	// for a firmware that reports none.
	ShortDeviceIDInHex string
}

// NewPairedCard returns the card a pairing recorded. shortDeviceIDInHex is
// empty for a firmware that reports none.
func NewPairedCard(version string, shortDeviceIDInHex string) *PairedCard {
	preconditions.CheckNotEmptyf(version, "a paired card has a version")
	return &PairedCard{Version: version, ShortDeviceIDInHex: shortDeviceIDInHex}
}
