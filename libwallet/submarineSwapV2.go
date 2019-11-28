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

func addUserSignatureInputSubmarineSwapV2(input Input, index int, tx *wire.MsgTx, privateKey *HDPrivateKey,
	muunKey *HDPublicKey) (*wire.TxIn, error) {

	submarineSwap := input.SubmarineSwapV2()
	if submarineSwap == nil {
		return nil, errors.Errorf("submarine swap data is nil for ss input")
	}

	if len(submarineSwap.ServerSignature()) == 0 {
		return nil, errors.Errorf("Swap server must provide signature")
	}

	witnessScript, err := createWitnessScriptSubmarineSwapV2(
		submarineSwap.PaymentHash256(),
		submarineSwap.UserPublicKey(),
		submarineSwap.MuunPublicKey(),
		submarineSwap.ServerPublicKey(),
		submarineSwap.BlocksForExpiration())
	if err != nil {
		return nil, err
	}

	sig, err := signNativeSegwitInput(input, index, tx, privateKey, witnessScript)
	if err != nil {
		return nil, err
	}

	txInput := tx.TxIn[index]
	txInput.Witness = wire.TxWitness{
		sig,
		submarineSwap.ServerSignature(),
		witnessScript,
	}

	return txInput, nil
}

func createWitnessScriptSubmarineSwapV2(paymentHash, userPubKey, muunPubKey, swapServerPubKey []byte, blocksForExpiration int64) ([]byte, error) {

	// It turns out that the payment hash present in an invoice is just the SHA256 of the
	// payment preimage, so we still have to do a pass of RIPEMD160 before pushing it to the
	// script
	paymentHash160 := ripemd160(paymentHash)
	muunPublicKeyHash160 := btcutil.Hash160(muunPubKey)

	// Equivalent miniscript (http://bitcoin.sipa.be/miniscript/):
	// or(
	//   and(pk(userPublicKey), pk(swapServerPublicKey)),
	//   or(
	//     and(pk(swapServerPublicKey), hash160(swapPaymentHash160)),
	//     and(pk(userPublicKey), and(pk(muunPublicKey), older(numBlocksForExpiration)))
	//   )
	// )
	//
	// However, we differ in that the size of the script was heavily optimized for spending the
	// first two branches (the collaborative close and the unilateral close by swapper), which
	// are the most probable to be used.

	builder := txscript.NewScriptBuilder().
		// Push the user public key to the second position of the stack
		AddData(userPubKey).
		AddOp(txscript.OP_SWAP).

		// Check whether the first stack item was a valid swap server signature
		AddData(swapServerPubKey).
		AddOp(txscript.OP_CHECKSIG).

		// If the swap server signature was correct
		AddOp(txscript.OP_IF).
		AddOp(txscript.OP_SWAP).

		// Check whether the second stack item was the payment preimage
		AddOp(txscript.OP_DUP).
		AddOp(txscript.OP_HASH160).
		AddData(paymentHash160).
		AddOp(txscript.OP_EQUAL).

		// If the preimage was correct
		AddOp(txscript.OP_IF).
		// We are done, leave just one true-ish item in the stack (there're 2
		// remaining items)
		AddOp(txscript.OP_DROP).

		// If the second stack item wasn't a valid payment preimage
		AddOp(txscript.OP_ELSE).

		// Validate that the second stack item was a valid user signature
		AddOp(txscript.OP_SWAP).
		AddOp(txscript.OP_CHECKSIG).
		AddOp(txscript.OP_ENDIF).

		// If the first stack item wasn't a valid server signature
		AddOp(txscript.OP_ELSE).
		// Validate that the blockchain height is big enough
		AddInt64(blocksForExpiration).
		AddOp(txscript.OP_CHECKSEQUENCEVERIFY).
		AddOp(txscript.OP_DROP).

		// Validate that the second stack item was a valid user signature
		AddOp(txscript.OP_CHECKSIGVERIFY).

		// Validate that the third stack item was the muun public key
		AddOp(txscript.OP_DUP).
		AddOp(txscript.OP_HASH160).
		AddData(muunPublicKeyHash160).
		AddOp(txscript.OP_EQUALVERIFY).

		// Notice that instead of directly pushing the public key here and checking the
		// signature P2PK-style, we pushed the hash of the public key, and require an
		// extra stack item with the actual public key, verifying the signature and
		// public key P2PKH-style.
		//
		// This trick reduces the on-chain footprint of the muun key from 33 bytes to
		// 20 bytes for the collaborative, and swap server's non-collaborative branches,
		// which are the most frequent ones.

		// Validate that the fourth stack item was a valid server signature
		AddOp(txscript.OP_CHECKSIG).
		AddOp(txscript.OP_ENDIF)

	return builder.Script()
}

func ValidateSubmarineSwapV2(rawInvoice string, userPublicKey *HDPublicKey, muunPublicKey *HDPublicKey, swap SubmarineSwap, originalExpirationInBlocks int64, network *Network) error {

	fundingOutput := swap.FundingOutput()

	invoice, err := ParseInvoice(rawInvoice, network)
	if err != nil {
		return errors.Wrapf(err, "failed to decode invoice")
	}

	// Check the payment hash matches

	serverPaymentHash, err := hex.DecodeString(fundingOutput.ServerPaymentHashInHex())
	if err != nil {
		return errors.Wrapf(err, "server payment hash is not valid hex")
	}

	if !bytes.Equal(invoice.PaymentHash[:], serverPaymentHash) {
		return errors.Errorf("payment hash doesn't match %v != %v", invoice.PaymentHash, fundingOutput.ServerPaymentHashInHex())
	}

	destination, err := hex.DecodeString(swap.Receiver().PublicKey())
	if err != nil {
		return errors.Wrapf(err, "destination is not valid hex")
	}

	if !bytes.Equal(invoice.Destination[:], destination) {
		return errors.Errorf("destination doesnt match %v != %v", invoice.Destination, swap.Receiver().PublicKey())
	}

	if fundingOutput.ExpirationInBlocks() != originalExpirationInBlocks {
		return errors.Errorf("expiration in blocks doesnt match %v != %v", originalExpirationInBlocks, fundingOutput.ExpirationInBlocks())
	}

	// Validate that we can derive the addresses involved
	derivationPath := fundingOutput.UserPublicKey().Path

	derivedUserKey, err := userPublicKey.DeriveTo(derivationPath)
	if err != nil {
		return errors.Wrapf(err, "failed to derive user key")
	}

	if !bytes.Equal(derivedUserKey.Raw(), fundingOutput.UserPublicKey().Raw()) {
		return errors.Errorf("user pub keys dont match %v != %v", derivedUserKey.String(), fundingOutput.UserPublicKey().String())
	}

	derivedMuunKey, err := muunPublicKey.DeriveTo(derivationPath)
	if err != nil {
		return errors.Wrapf(err, "failed to derive muun key")
	}

	if !bytes.Equal(derivedMuunKey.Raw(), fundingOutput.MuunPublicKey().Raw()) {
		return errors.Errorf("muun pub keys dont match %v != %v", derivedMuunKey.String(), fundingOutput.MuunPublicKey().String())
	}

	// Check the swap's witness script is a valid swap script

	serverPubKey, err := hex.DecodeString(swap.FundingOutput().ServerPublicKeyInHex())
	if err != nil {
		return errors.Wrapf(err, "server pub key is not hex")
	}

	witnessScript, err := createWitnessScriptSubmarineSwapV2(
		serverPaymentHash,
		derivedUserKey.Raw(),
		derivedMuunKey.Raw(),
		serverPubKey,
		swap.FundingOutput().ExpirationInBlocks())
	if err != nil {
		return errors.Wrapf(err, "failed to compute witness script")
	}

	witnessScriptHash := sha256.Sum256(witnessScript)
	address, err := btcutil.NewAddressWitnessScriptHash(witnessScriptHash[:], network.network)
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
