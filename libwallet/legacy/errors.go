package libwallet

import "errors"

const (
	ErrUnknown               = 1
	ErrInvalidURI            = 2
	ErrNetwork               = 3
	ErrInvalidPrivateKey     = 4
	ErrInvalidDerivationPath = 5
	ErrInvalidInvoice        = 6
)

func ErrorCode(err error) int64 {
	type coder interface {
		Code() int64
	}
	var e coder
	if errors.As(err, &e) {
		return e.Code()
	}
	return ErrUnknown
}
