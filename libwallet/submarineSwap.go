package libwallet

import (
	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/swaps"
)

type SubmarineSwap interface {
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
	data := swaps.SubmarineSwap{
		Invoice: swap.Invoice(),
		Receiver: swaps.SubmarineSwapReceiver{
			Alias:     swap.Receiver().Alias(),
			PublicKey: swap.Receiver().PublicKey(),
		},
		FundingOutput: createSwapFundingOutput(swap.FundingOutput()),
		PreimageInHex: swap.PreimageInHex(),
	}
	return data.Validate(
		rawInvoice,
		&swaps.KeyDescriptor{Key: &userPublicKey.key, Path: userPublicKey.Path},
		&swaps.KeyDescriptor{Key: &muunPublicKey.key, Path: muunPublicKey.Path},
		originalExpirationInBlocks,
		network.network,
	)
}

func createSwapFundingOutput(output SubmarineSwapFundingOutput) swaps.SubmarineSwapFundingOutput {
	out := swaps.SubmarineSwapFundingOutput{
		ScriptVersion:          output.ScriptVersion(),
		OutputAddress:          output.OutputAddress(),
		OutputAmount:           output.OutputAmount(),
		ConfirmationsNeeded:    output.ConfirmationsNeeded(),
		ServerPaymentHashInHex: output.ServerPaymentHashInHex(),
		ServerPublicKeyInHex:   output.ServerPublicKeyInHex(),
		UserLockTime:           output.UserLockTime(),
	}
	switch out.ScriptVersion {
	case AddressVersionSwapsV1:
		out.UserRefundAddress = addresses.New(
			output.UserRefundAddress().Version(),
			output.UserRefundAddress().DerivationPath(),
			output.UserRefundAddress().Address(),
		)
	case AddressVersionSwapsV2:
		out.ExpirationInBlocks = output.ExpirationInBlocks()
		out.UserPublicKey = &output.UserPublicKey().key
		out.MuunPublicKey = &output.MuunPublicKey().key
		out.KeyPath = output.UserPublicKey().Path
	}
	return out
}
