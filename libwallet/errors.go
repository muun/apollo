package libwallet

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
	switch e := err.(type) {
	case coder:
		return e.Code()
	default:
		return ErrUnknown
	}
}
