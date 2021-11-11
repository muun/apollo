package swaps

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"

	"github.com/btcsuite/btcd/txscript"
	"github.com/muun/libwallet/btcsuitew/btcutilw"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcutil"
	"github.com/lightningnetwork/lnd/zpay32"
	"github.com/muun/libwallet/addresses"
)

func (swap *SubmarineSwap) validateV1(rawInvoice string, userPublicKey, muunPublicKey *KeyDescriptor, network *chaincfg.Params) error {

	invoice, err := zpay32.Decode(rawInvoice, network)
	if err != nil {
		return fmt.Errorf("failed to decode invoice: %w", err)
	}

	// Check the payment hash matches

	serverPaymentHash, err := hex.DecodeString(swap.FundingOutput.ServerPaymentHashInHex)
	if err != nil {
		return fmt.Errorf("server payment hash is not valid hex: %w", err)
	}

	if !bytes.Equal(invoice.PaymentHash[:], serverPaymentHash) {
		return fmt.Errorf("payment hash doesn't match %v != %v", hex.EncodeToString(invoice.PaymentHash[:]), swap.FundingOutput.ServerPaymentHashInHex)
	}

	// TODO: check that timelock is acceptable

	// Validate that the refund address is one we can derive

	swapRefundAddress := swap.FundingOutput.UserRefundAddress
	derivedUserKey, err := userPublicKey.DeriveTo(swapRefundAddress.DerivationPath())
	if err != nil {
		return fmt.Errorf("failed to derive user key: %w", err)
	}
	derivedMuunKey, err := muunPublicKey.DeriveTo(swapRefundAddress.DerivationPath())
	if err != nil {
		return fmt.Errorf("failed to derive muun key: %w", err)
	}

	refundAddress, err := addresses.Create(
		swapRefundAddress.Version(),
		derivedUserKey,
		derivedMuunKey,
		swapRefundAddress.DerivationPath(),
		network,
	)
	if err != nil {
		return fmt.Errorf("failed to generate refund address: %w", err)
	}

	if refundAddress.Address() != swapRefundAddress.Address() {
		return fmt.Errorf("refund address doesn't match generated (%v != %v)", swapRefundAddress.Address(), refundAddress.Address())
	}

	// Check the swap's witness script is a valid swap script

	serverPubKey, err := hex.DecodeString(swap.FundingOutput.ServerPublicKeyInHex)
	if err != nil {
		return fmt.Errorf("server pub key is not hex: %w", err)
	}

	witnessScript, err := CreateWitnessScriptSubmarineSwapV1(
		swapRefundAddress.Address(),
		serverPaymentHash,
		serverPubKey,
		swap.FundingOutput.UserLockTime,
		network)
	if err != nil {
		return fmt.Errorf("failed to compute witness script: %w", err)
	}

	redeemScript, err := createNonNativeSegwitRedeemScript(witnessScript)
	if err != nil {
		return fmt.Errorf("failed to build redeem script: %w", err)
	}

	address, err := btcutil.NewAddressScriptHash(redeemScript, network)
	if err != nil {
		return fmt.Errorf("failed to build address for swap script: %w", err)
	}

	if address.EncodeAddress() != swap.FundingOutput.OutputAddress {
		return fmt.Errorf("address for swap script mismatch (%v != %v)", address.EncodeAddress(), swap.FundingOutput.OutputAddress)
	}

	if len(swap.PreimageInHex) > 0 {
		preimage, err := hex.DecodeString(swap.PreimageInHex)
		if err != nil {
			return fmt.Errorf("preimagehex is not actually hex: %w", err)
		}

		calculatedPaymentHash := sha256.Sum256(preimage)
		if !bytes.Equal(invoice.PaymentHash[:], calculatedPaymentHash[:]) {
			return fmt.Errorf("payment hash doesn't match preimage (%v != hash(%v)", invoice.PaymentHash, swap.PreimageInHex)
		}
	}

	return nil
}

func CreateWitnessScriptSubmarineSwapV1(refundAddress string, paymentHash []byte, swapServerPubKey []byte, lockTime int64, network *chaincfg.Params) ([]byte, error) {

	// It turns out that the payment hash present in an invoice is just the SHA256 of the
	// payment preimage, so we still have to do a pass of RIPEMD160 before pushing it to the
	// script
	paymentHash160 := ripemd160(paymentHash)
	decodedRefundAddress, err := btcutilw.DecodeAddress(refundAddress, network)
	if err != nil {
		return nil, fmt.Errorf("refund address is invalid: %w", err)
	}

	refundAddressHash := decodedRefundAddress.ScriptAddress()

	builder := txscript.NewScriptBuilder()
	builder.AddOp(txscript.OP_DUP)

	// Condition to decide which branch to follow:
	builder.AddOp(txscript.OP_HASH160).
		AddData(paymentHash160).
		AddOp(txscript.OP_EQUAL)

	// SubmarineSwap service spending script, for successful LN payments:
	builder.AddOp(txscript.OP_IF).
		AddOp(txscript.OP_DROP).
		AddData(swapServerPubKey)

	// User spending script, for failed LN payments:
	builder.AddOp(txscript.OP_ELSE).
		AddInt64(lockTime).
		AddOp(txscript.OP_CHECKLOCKTIMEVERIFY).
		AddOp(txscript.OP_DROP).
		AddOp(txscript.OP_DUP).
		AddOp(txscript.OP_HASH160).
		AddData(refundAddressHash).
		AddOp(txscript.OP_EQUALVERIFY)

	// Final verification for both branches:
	builder.AddOp(txscript.OP_ENDIF).
		AddOp(txscript.OP_CHECKSIG)

	return builder.Script()
}
