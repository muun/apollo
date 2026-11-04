package server

import "github.com/muun/libwallet/server/api"

type errorCode struct {
	Code    int
	Message string
	Type    api.ErrorType
}

var errorCodes = struct {
	ErrKeyEmpty                       errorCode
	ErrValueEmpty                     errorCode
	ErrItemsEmpty                     errorCode
	ErrSignInternalError              errorCode
	ErrSignMacValidation              errorCode
	ErrChallengeExpired               errorCode
	ErrPairInternalError              errorCode
	ErrAppletNotFound                 errorCode
	ErrUnsupportedCardVersion         errorCode
	ErrCardMustPairAgain              errorCode
	ErrCardOutOfPairings              errorCode
	ErrInvalidRateWindow              errorCode
	ErrMarketplaceProviderUnavailable errorCode
	ErrMarketplaceCardOutOfStock      errorCode
	ErrMarketplaceCardDeprecated      errorCode
	ErrMarketplaceCountryUnreachable  errorCode
	ErrUnknown                        errorCode
}{
	// key-value storage errors:
	ErrKeyEmpty: errorCode{
		Code:    14_001,
		Message: "Key can not be empty",
		Type:    api.ErrorType_CLIENT,
	},
	ErrValueEmpty: errorCode{
		Code:    14_002,
		Message: "Value can not be empty",
		Type:    api.ErrorType_CLIENT,
	},
	ErrItemsEmpty: errorCode{
		Code:    14_003,
		Message: "Items can not be empty",
		Type:    api.ErrorType_CLIENT,
	},

	// security cards errors:
	ErrSignInternalError: errorCode{
		Code:    14_100,
		Message: "Sign internal error",
		Type:    api.ErrorType_LIBWALLET,
	},
	ErrSignMacValidation: errorCode{
		Code:    14_101,
		Message: "Mac validation failure",
		Type:    api.ErrorType_LIBWALLET,
	},
	ErrChallengeExpired: errorCode{
		Code:    14_102,
		Message: "Challenge expired",
		Type:    api.ErrorType_LIBWALLET,
	},
	ErrPairInternalError: errorCode{
		Code:    14_103,
		Message: "Pair internal error",
		Type:    api.ErrorType_LIBWALLET,
	},
	// 14_104 stays unused: released apps read it as no slots left on the card.
	ErrAppletNotFound: errorCode{
		Code:    14_105,
		Message: "Muun applet id not found",
		Type:    api.ErrorType_LIBWALLET,
	},
	ErrUnsupportedCardVersion: errorCode{
		Code:    14_106,
		Message: "Unsupported security card version",
		Type:    api.ErrorType_LIBWALLET,
	},
	ErrCardMustPairAgain: errorCode{
		Code:    14_107,
		Message: "Security card needs to be paired again",
		Type:    api.ErrorType_LIBWALLET,
	},
	ErrCardOutOfPairings: errorCode{
		Code:    14_108,
		Message: "Security card has no pairings left",
		Type:    api.ErrorType_LIBWALLET,
	},

	// exchange rate errors:
	ErrInvalidRateWindow: errorCode{
		Code:    14_200,
		Message: "Exchange rate window is invalid",
		Type:    api.ErrorType_CLIENT,
	},

	// security cards marketplace errors:
	// TODO: replace these messages with product's copy once it's defined.
	ErrMarketplaceProviderUnavailable: errorCode{
		Code:    14_300,
		Message: "Security card provider unavailable",
		Type:    api.ErrorType_LIBWALLET,
	},
	ErrMarketplaceCardOutOfStock: errorCode{
		Code:    14_301,
		Message: "Security card out of stock",
		Type:    api.ErrorType_LIBWALLET,
	},
	ErrMarketplaceCardDeprecated: errorCode{
		Code:    14_302,
		Message: "Security card no longer sold",
		Type:    api.ErrorType_LIBWALLET,
	},
	ErrMarketplaceCountryUnreachable: errorCode{
		Code:    14_303,
		Message: "Security card provider doesn't ship to the country",
		Type:    api.ErrorType_LIBWALLET,
	},

	ErrUnknown: errorCode{
		Code:    14_999,
		Message: "Unknown error",
		Type:    api.ErrorType_LIBWALLET,
	},
}
