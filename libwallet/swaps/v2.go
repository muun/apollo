package swaps

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcutil"
	"github.com/btcsuite/btcutil/hdkeychain"
	"github.com/lightningnetwork/lnd/zpay32"
	"github.com/pkg/errors"
)

func (swap *SubmarineSwap) validateV2(rawInvoice string, userPublicKey, muunPublicKey *KeyDescriptor, originalExpirationInBlocks int64, network *chaincfg.Params) error {

	fundingOutput := swap.FundingOutput

	invoice, err := zpay32.Decode(rawInvoice, network)
	if err != nil {
		return fmt.Errorf("failed to decode invoice: %w", err)
	}

	// Check the payment hash matches

	serverPaymentHash, err := hex.DecodeString(fundingOutput.ServerPaymentHashInHex)
	if err != nil {
		return fmt.Errorf("server payment hash is not valid hex: %w", err)
	}

	if !bytes.Equal(invoice.PaymentHash[:], serverPaymentHash) {
		return fmt.Errorf("payment hash doesn't match %v != %v", hex.EncodeToString(invoice.PaymentHash[:]), fundingOutput.ServerPaymentHashInHex)
	}

	destination, err := hex.DecodeString(swap.Receiver.PublicKey)
	if err != nil {
		return fmt.Errorf("destination is not valid hex: %w", err)
	}

	if !bytes.Equal(invoice.Destination.SerializeCompressed(), destination) {
		return fmt.Errorf("destination doesnt match %v != %v", invoice.Destination.SerializeCompressed(), swap.Receiver.PublicKey)
	}

	if fundingOutput.ExpirationInBlocks != originalExpirationInBlocks {
		return fmt.Errorf("expiration in blocks doesnt match %v != %v", originalExpirationInBlocks, fundingOutput.ExpirationInBlocks)
	}

	// Validate that we can derive the addresses involved
	derivationPath := fundingOutput.KeyPath

	derivedUserKey, err := userPublicKey.DeriveTo(derivationPath)
	if err != nil {
		return fmt.Errorf("failed to derive user key: %w", err)
	}

	if derivedUserKey.String() != fundingOutput.UserPublicKey.String() {
		return fmt.Errorf("user pub keys dont match %v != %v", derivedUserKey.String(), fundingOutput.UserPublicKey.String())
	}

	derivedMuunKey, err := muunPublicKey.DeriveTo(derivationPath)
	if err != nil {
		return fmt.Errorf("failed to derive muun key: %w", err)
	}

	if derivedMuunKey.String() != fundingOutput.MuunPublicKey.String() {
		return fmt.Errorf("muun pub keys dont match %v != %v", derivedMuunKey.String(), fundingOutput.MuunPublicKey.String())
	}

	// Check the swap's witness script is a valid swap script

	serverPubKey, err := hex.DecodeString(swap.FundingOutput.ServerPublicKeyInHex)
	if err != nil {
		return fmt.Errorf("server pub key is not hex: %w", err)
	}

	witnessScript, err := CreateWitnessScriptSubmarineSwapV2(
		serverPaymentHash,
		encodeRaw(derivedUserKey),
		encodeRaw(derivedMuunKey),
		serverPubKey,
		swap.FundingOutput.ExpirationInBlocks)
	if err != nil {
		return fmt.Errorf("failed to compute witness script: %w", err)
	}

	witnessScriptHash := sha256.Sum256(witnessScript)
	address, err := btcutil.NewAddressWitnessScriptHash(witnessScriptHash[:], network)
	if err != nil {
		return fmt.Errorf("failed to build address for swap script: %w", err)
	}

	if address.EncodeAddress() != swap.FundingOutput.OutputAddress {
		return fmt.Errorf("address for swap script mismatch (%v != %v)", address.EncodeAddress(), swap.FundingOutput.OutputAddress)
	}

	if len(swap.PreimageInHex) > 0 {
		preimage, err := hex.DecodeString(swap.PreimageInHex)
		if err != nil {
			return errors.Wrapf(err, "preimagehex is not actually hex 🤔")
		}

		calculatedPaymentHash := sha256.Sum256(preimage)
		if !bytes.Equal(invoice.PaymentHash[:], calculatedPaymentHash[:]) {
			return fmt.Errorf("payment hash doesn't match preimage (%v != hash(%v)", invoice.PaymentHash, swap.PreimageInHex)
		}
	}

	return nil
}

func CreateWitnessScriptSubmarineSwapV2(paymentHash, userPubKey, muunPubKey, swapServerPubKey []byte, blocksForExpiration int64) ([]byte, error) {

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

func encodeRaw(key *hdkeychain.ExtendedKey) []byte {
	publicKey, err := key.ECPubKey()
	if err != nil {
		panic(err)
	}
	return publicKey.SerializeCompressed()
}
