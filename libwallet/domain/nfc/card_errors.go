package nfc

import "fmt"

// CardErrorCode represents our internal domain error codes
type CardErrorCode uint16

const (
	ErrInternal           CardErrorCode = 1
	ErrSlotOccupied       CardErrorCode = 2
	ErrSlotNotInitialized CardErrorCode = 3
	ErrAppletIdNotFound   CardErrorCode = 4 //nolint:staticcheck // TODO: const ErrAppletIdNotFound should be ErrAppletIDNotFound
	// ErrTransport marks a failure in the underlying NFC bridge (card moved away, link dropped, etc)
	ErrTransport CardErrorCode = 5
)

type CardError struct {
	Message string
	Code    CardErrorCode
}

// Error implements the error interface.
func (e *CardError) Error() string {
	return fmt.Sprintf("status %d: %s", e.Code, e.Message)
}

// newCardError creates a new CardError instance.
func newCardError(code CardErrorCode, message string) *CardError {
	return &CardError{
		Message: message,
		Code:    code,
	}
}
