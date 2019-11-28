package libwallet

import (
	"github.com/go-errors/errors"
)

type SubmarineSwap interface {
	SwapUuid() string
	Invoice() string
	Receiver() SubmarineSwapReceiver
	FundingOutput() SubmarineSwapFundingOutput

	PreimageInHex() string
}

type SubmarineSwapReceiver interface {
	Alias() string
	PublicKey() string
}

type SubmarineSwapFundingOutput interface {
	ScriptVersion() int64

	OutputAddress() string
	OutputAmount() int64
	ConfirmationsNeeded() int
	ServerPaymentHashInHex() string
	ServerPublicKeyInHex() string

	UserLockTime() int64

	// v1 only
	UserRefundAddress() MuunAddress

	// v2 only
	ExpirationInBlocks() int64
	UserPublicKey() *HDPublicKey
	MuunPublicKey() *HDPublicKey
}

func ValidateSubmarineSwap(rawInvoice string, userPublicKey *HDPublicKey, muunPublicKey *HDPublicKey, swap SubmarineSwap, originalExpirationInBlocks int64, network *Network) error {

	switch AddressVersion(swap.FundingOutput().ScriptVersion()) {
	case addressSubmarineSwapV1:
		return ValidateSubmarineSwapV1(rawInvoice, userPublicKey, muunPublicKey, swap, network)
	case addressSubmarineSwapV2:
		return ValidateSubmarineSwapV2(rawInvoice, userPublicKey, muunPublicKey, swap, originalExpirationInBlocks, network)
	}

	return errors.Errorf("unknown swap version %v", swap.FundingOutput().ScriptVersion())
}
