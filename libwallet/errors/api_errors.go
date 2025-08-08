package errors

type ErrorType int

const (
	CLIENT ErrorType = iota
	LIBWALLET
)

type ErrorCode struct {
	Code    int
	Message string
	Type    ErrorType
}

var ErrorCodes = struct {
	ErrKeyEmpty   ErrorCode
	ErrValueEmpty ErrorCode
	ErrItemsEmpty ErrorCode
	ErrUnknown    ErrorCode
}{
	// key-value storage errors:
	ErrKeyEmpty:   ErrorCode{Code: 14_001, Message: "Key can not be empty", Type: CLIENT},
	ErrValueEmpty: ErrorCode{Code: 14_002, Message: "Value can not be empty", Type: CLIENT},
	ErrItemsEmpty: ErrorCode{Code: 14_003, Message: "Items can not be empty", Type: CLIENT},

	ErrUnknown: ErrorCode{Code: 14_999, Message: "Unknown error", Type: LIBWALLET},
}
