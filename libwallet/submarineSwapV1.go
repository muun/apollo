package libwallet

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
	"github.com/pkg/errors"
)

func addUserSignatureInputSubmarineSwapV1(input Input, index int, tx *wire.MsgTx, privateKey *HDPrivateKey,
	muunKey *HDPublicKey) (*wire.TxIn, error) {

	submarineSwap := input.SubmarineSwapV1()
	if submarineSwap == nil {
		return nil, errors.Errorf("submarine swap data is nil for ss input")
	}

	witnessScript, err := createWitnessScriptSubmarineSwapV1(submarineSwap.RefundAddress(),
		submarineSwap.PaymentHash256(),
		submarineSwap.ServerPublicKey(),
		submarineSwap.LockTime(),
		privateKey.Network)
	if err != nil {
		return nil, err
	}

	redeemScript, err := createNonNativeSegwitRedeemScript(witnessScript)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to build reedem script for signing")
	}

	sig, err := signNonNativeSegwitInput(input, index, tx, privateKey, redeemScript, witnessScript)
	if err != nil {
		return nil, err
	}

	txInput := tx.TxIn[index]
	txInput.Witness = wire.TxWitness{sig, privateKey.PublicKey().Raw(), witnessScript}

	return txInput, nil
}

//lint:ignore U1000 unused function for consistency with other schemes
func createRedeemScriptSubmarineSwapForUser(publicKey *HDPublicKey) {

}

func createWitnessScriptSubmarineSwapV1(refundAddress string, paymentHash []byte, swapServerPubKey []byte, lockTime int64, network *Network) ([]byte, error) {

	// It turns out that the payment hash present in an invoice is just the SHA256 of the
	// payment preimage, so we still have to do a pass of RIPEMD160 before pushing it to the
	// script
	paymentHash160 := ripemd160(paymentHash)
	decodedRefundAddress, err := btcutil.DecodeAddress(refundAddress, network.network)
	if err != nil {
		return nil, errors.Wrapf(err, "refund address is invalid")
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

func ValidateSubmarineSwapV1(rawInvoice string, userPublicKey *HDPublicKey, muunPublicKey *HDPublicKey, swap SubmarineSwap, network *Network) error {

	invoice, err := ParseInvoice(rawInvoice, network)
	if err != nil {
		return errors.Wrapf(err, "failed to decode invoice")
	}

	// Check the payment hash matches

	serverPaymentHash, err := hex.DecodeString(swap.FundingOutput().ServerPaymentHashInHex())
	if err != nil {
		return errors.Wrapf(err, "server payment hash is not valid hex")
	}

	if !bytes.Equal(invoice.PaymentHash[:], serverPaymentHash) {
		return errors.Errorf("payment hash doesn't match %v != %v", invoice.PaymentHash, swap.FundingOutput().ServerPaymentHashInHex())
	}

	// TODO: check that timelock is acceptable

	// Validate that the refund address is one we can derive

	swapRefundAddress := swap.FundingOutput().UserRefundAddress()
	derivedUserKey, err := userPublicKey.DeriveTo(swapRefundAddress.DerivationPath())
	if err != nil {
		return errors.Wrapf(err, "failed to derive user key")
	}
	derivedMuunKey, err := muunPublicKey.DeriveTo(swapRefundAddress.DerivationPath())
	if err != nil {
		return errors.Wrapf(err, "failed to derive muun key")
	}

	refundAddress, err := newMuunAddress(AddressVersion(swapRefundAddress.Version()), derivedUserKey, derivedMuunKey)
	if err != nil {
		return errors.Wrapf(err, "failed to generate refund address")
	}

	if refundAddress.Address() != swapRefundAddress.Address() {
		return errors.Errorf("refund address doesn't match generated (%v != %v)", swapRefundAddress.Address(), refundAddress.Address())
	}

	// Check the swap's witness script is a valid swap script

	serverPubKey, err := hex.DecodeString(swap.FundingOutput().ServerPublicKeyInHex())
	if err != nil {
		return errors.Wrapf(err, "server pub key is not hex")
	}

	witnessScript, err := createWitnessScriptSubmarineSwapV1(
		swapRefundAddress.Address(),
		serverPaymentHash,
		serverPubKey,
		swap.FundingOutput().UserLockTime(),
		network)
	if err != nil {
		return errors.Wrapf(err, "failed to compute witness script")
	}

	redeemScript, err := createNonNativeSegwitRedeemScript(witnessScript)
	if err != nil {
		return errors.Wrapf(err, "failed to build redeem script")
	}

	address, err := btcutil.NewAddressScriptHash(redeemScript, network.network)
	if err != nil {
		return errors.Wrapf(err, "failed to build address for swap script")
	}

	if address.EncodeAddress() != swap.FundingOutput().OutputAddress() {
		return errors.Errorf("address for swap script mismatch (%v != %v)", address.EncodeAddress(), swap.FundingOutput().OutputAddress())
	}

	if len(swap.PreimageInHex()) > 0 {
		preimage, err := hex.DecodeString(swap.PreimageInHex())
		if err != nil {
			return errors.Wrapf(err, "preimagehex is not actually hex 🤔")
		}

		calculatedPaymentHash := sha256.Sum256(preimage)
		if !bytes.Equal(invoice.PaymentHash[:], calculatedPaymentHash[:]) {
			return errors.Errorf("payment hash doesn't match preimage (%v != hash(%v)", invoice.PaymentHash, swap.PreimageInHex())
		}
	}

	return nil
}
