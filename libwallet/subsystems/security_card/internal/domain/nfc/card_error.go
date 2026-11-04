package nfc

import "fmt"

// CardErrorCode represents our internal domain error codes
type CardErrorCode uint16

const (
	ErrInternal           CardErrorCode = 1
	ErrSlotNotInitialized CardErrorCode = 3
	ErrAppletIDNotFound   CardErrorCode = 4
	// ErrTransport marks a failure in the underlying NFC bridge (card moved away, link dropped, etc)
	ErrTransport CardErrorCode = 5
	// ErrCardMustPairAgain marks a card that answered too many challenges without one ever
	// confirming the ratchet, and will refuse the rest until it is paired again.
	ErrCardMustPairAgain CardErrorCode = 6
	// ErrUnknownFirmware marks a card whose applet answered its selection in a shape this client
	// does not know, or with nothing at all: a firmware newer than the client, which has to be
	// updated before it can talk to the card, one from before any firmware in the field, or a
	// card that is not READY or cannot reach its metadata applet.
	ErrUnknownFirmware CardErrorCode = 7
	// ErrCardOutOfPairings marks a card that handed out the last of the pairings it has for its
	// life. Unlike ErrCardMustPairAgain, no pairing recovers it: the card is done.
	ErrCardOutOfPairings CardErrorCode = 8
)

type CardError struct {
	Message string
	Code    CardErrorCode
}

func newCardError(code CardErrorCode, message string) *CardError {
	return &CardError{
		Message: message,
		Code:    code,
	}
}

func (e *CardError) Error() string {
	return fmt.Sprintf("status %d: %s", e.Code, e.Message)
}
