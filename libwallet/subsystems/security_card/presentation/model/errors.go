package model

import "fmt"

type MuunAppletNotFoundError struct {
	Message string
	Cause   error
}

func (e MuunAppletNotFoundError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("muun applet id not found: %s: %v", e.Message, e.Cause)
	}
	return "muun applet id not found"
}

func (e MuunAppletNotFoundError) Unwrap() error {
	return e.Cause
}

type InvalidMacError struct {
	Message string
	Cause   error
}

func (e InvalidMacError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("mac is invalid: %s: %v", e.Message, e.Cause)
	}
	return "mac is invalid"
}

func (e InvalidMacError) Unwrap() error {
	return e.Cause
}

type ChallengeExpiredError struct {
	Message string
	Cause   error
}

func (e ChallengeExpiredError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf(
			"challenge is expired: %s: %v", e.Message, e.Cause,
		)
	}
	return "challenge is expired"
}

func (e ChallengeExpiredError) Unwrap() error {
	return e.Cause
}

// UnsupportedCardVersionError is returned when the tapped card's applet answered the selection
// in a shape this client does not know, or with nothing at all: a firmware newer than the app,
// which has to be updated to talk to it, one from before any firmware in the field, or a card
// that is not READY or cannot reach its metadata applet. Updating the app fixes only the first.
type UnsupportedCardVersionError struct {
	Message string
	Cause   error
}

func (e UnsupportedCardVersionError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf(
			"unsupported card version: %s: %v",
			e.Message, e.Cause,
		)
	}
	return fmt.Sprintf("unsupported card version: %s", e.Message)
}

func (e UnsupportedCardVersionError) Unwrap() error {
	return e.Cause
}

// CardMustPairAgainError is returned when the card has answered too many challenges without
// one ever confirming that the server received its response, and refuses the rest. The cap is
// what bounds the traces a side-channel attack can collect under one secret, so the card only
// clears it on a new pairing. The user has to pair the card again.
type CardMustPairAgainError struct {
	Message string
	Cause   error
}

func (e CardMustPairAgainError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("card must pair again: %s: %v", e.Message, e.Cause)
	}
	return fmt.Sprintf("card must pair again: %s", e.Message)
}

func (e CardMustPairAgainError) Unwrap() error {
	return e.Cause
}

// CardOutOfPairingsError is returned when the card has handed out the last of the pairings it
// has for its life and refuses to pair again. Each pairing uses one of the card's key agreement
// keys, and the cap on uses per key is what bounds the traces a side-channel attack can collect
// on it, so the card never clears it. The user needs another card.
type CardOutOfPairingsError struct {
	Message string
	Cause   error
}

func (e CardOutOfPairingsError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("card has no pairings left: %s: %v", e.Message, e.Cause)
	}
	return fmt.Sprintf("card has no pairings left: %s", e.Message)
}

func (e CardOutOfPairingsError) Unwrap() error {
	return e.Cause
}

// PairInternalError reports a failure of the pairing that signing runs on its
// own when the card is not paired at the version it expects.
type PairInternalError struct {
	Message string
	Cause   error
}

func (e PairInternalError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf(
			"error during pairing: %s: %v", e.Message, e.Cause,
		)
	}
	return "error during pairing"
}

func (e PairInternalError) Unwrap() error {
	return e.Cause
}

// CardFirmwareVersionContextKey names, in an error's context, the firmware version of the card
// the error happened with.
const CardFirmwareVersionContextKey = "card_firmware_version"

// TappedCardError is a failure with a card that had already named its firmware on SELECT. It
// reads as its cause, so what the apps show and match on stays the same, and it carries the
// firmware version for them to report along with the error.
type TappedCardError struct {
	FirmwareVersion string
	Cause           error
}

func (e TappedCardError) Error() string {
	if e.Cause != nil {
		return e.Cause.Error()
	}
	return fmt.Sprintf("security card error on firmware %s", e.FirmwareVersion)
}

func (e TappedCardError) Unwrap() error {
	return e.Cause
}

// ErrorContext names the firmware of the card the error happened with.
func (e TappedCardError) ErrorContext() map[string]string {
	return map[string]string{CardFirmwareVersionContextKey: e.FirmwareVersion}
}
