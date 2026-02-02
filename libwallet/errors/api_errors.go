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
	ErrKeyEmpty          ErrorCode
	ErrValueEmpty        ErrorCode
	ErrItemsEmpty        ErrorCode
	ErrSignInternalError ErrorCode
	ErrSignMacValidation ErrorCode
	ErrChallengeExpired  ErrorCode
	ErrPairInternalError ErrorCode
	ErrNoSlotsAvailable  ErrorCode
	ErrAppletNotFound    ErrorCode
	ErrUnknown           ErrorCode
}{
	// key-value storage errors:
	ErrKeyEmpty:   ErrorCode{Code: 14_001, Message: "Key can not be empty", Type: CLIENT},
	ErrValueEmpty: ErrorCode{Code: 14_002, Message: "Value can not be empty", Type: CLIENT},
	ErrItemsEmpty: ErrorCode{Code: 14_003, Message: "Items can not be empty", Type: CLIENT},

	// security cards errors:
	ErrSignInternalError: ErrorCode{Code: 14_100, Message: "Sign internal error", Type: LIBWALLET},
	ErrSignMacValidation: ErrorCode{Code: 14_101, Message: "Mac validation failure", Type: LIBWALLET},
	ErrChallengeExpired:  ErrorCode{Code: 14_102, Message: "Challenge expired", Type: LIBWALLET},
	ErrPairInternalError: ErrorCode{Code: 14_103, Message: "Pair internal error", Type: LIBWALLET},
	ErrNoSlotsAvailable:  ErrorCode{Code: 14_104, Message: "No slots available for pairing a card", Type: LIBWALLET},
	ErrAppletNotFound:    ErrorCode{Code: 14_105, Message: "Muun applet id not found", Type: LIBWALLET},

	ErrUnknown: ErrorCode{Code: 14_999, Message: "Unknown error", Type: LIBWALLET},
}
