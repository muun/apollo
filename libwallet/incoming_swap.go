package libwallet

import (
	"bytes"
	"crypto/sha256"
	"errors"
	"fmt"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
	"github.com/lightningnetwork/lnd/lnwire"
	"github.com/muun/libwallet/hdpath"
	"github.com/muun/libwallet/sphinx"
)

type coinIncomingSwap struct {
	Network             *chaincfg.Params
	MuunSignature       []byte
	Sphinx              []byte
	HtlcTx              []byte
	PaymentHash256      []byte
	SwapServerPublicKey []byte
	ExpirationHeight    int64
	VerifyOutputAmount  bool // used only for fulfilling swaps through IncomingSwap
	Collect             btcutil.Amount
}

func (c *coinIncomingSwap) SignInput(index int, tx *wire.MsgTx, userKey *HDPrivateKey, muunKey *HDPublicKey) error {
	// Deserialize the HTLC transaction
	htlcTx := wire.MsgTx{}
	err := htlcTx.Deserialize(bytes.NewReader(c.HtlcTx))
	if err != nil {
		return fmt.Errorf("could not deserialize htlc tx: %w", err)
	}

	// Lookup invoice data matching this HTLC using the payment hash
	db, err := openDB()
	if err != nil {
		return err
	}
	defer db.Close()

	secrets, err := db.FindByPaymentHash(c.PaymentHash256)
	if err != nil {
		return fmt.Errorf("could not find invoice data for payment hash: %w", err)
	}

	// Recreate the HTLC script to verify it matches the transaction. For this
	// we must derive the keys used in the HTLC script
	htlcKeyPath := hdpath.MustParse(secrets.KeyPath).Child(htlcKeyChildIndex)

	// Derive first the private key, which we are going to use for signing later
	userPrivateKey, err := userKey.DeriveTo(htlcKeyPath.String())
	if err != nil {
		return err
	}
	userPublicKey := userPrivateKey.PublicKey()

	muunPublicKey, err := muunKey.DeriveTo(htlcKeyPath.String())
	if err != nil {
		return err
	}

	htlcScript, err := c.createHtlcScript(userPublicKey, muunPublicKey)
	if err != nil {
		return fmt.Errorf("could not create htlc script: %w", err)
	}

	// Try to find the script we just built inside the HTLC output scripts
	htlcOutputIndex, err := c.findHtlcOutputIndex(&htlcTx, htlcScript)
	if err != nil {
		return err
	}

	// Next, we must validate the sphinx data. We derive the client identity
	// key used by this invoice with the key path stored in the db.
	identityKeyPath := hdpath.MustParse(secrets.KeyPath).Child(identityKeyChildIndex)

	nodeHDKey, err := userKey.DeriveTo(identityKeyPath.String())
	if err != nil {
		return err
	}
	nodeKey, err := nodeHDKey.key.ECPrivKey()
	if err != nil {
		return err
	}

	txInput := tx.TxIn[index]

	if txInput.PreviousOutPoint.Hash != htlcTx.TxHash() {
		return fmt.Errorf("expected fulfillment tx input to point to htlc tx")
	}
	if txInput.PreviousOutPoint.Index != uint32(htlcOutputIndex) {
		return fmt.Errorf("expected fulfillment tx input to point to correct htlc output")
	}

	sigHashes := txscript.NewTxSigHashes(tx)

	muunSigKey, err := muunPublicKey.key.ECPubKey()
	if err != nil {
		return err
	}

	// Verify Muun signature
	htlcOutputAmount := htlcTx.TxOut[htlcOutputIndex].Value
	err = verifyTxWitnessSignature(
		tx,
		sigHashes,
		index,
		htlcOutputAmount,
		htlcScript,
		c.MuunSignature,
		muunSigKey,
	)
	if err != nil {
		return fmt.Errorf("could not verify Muun signature for htlc: %w", err)
	}

	var outputAmount lnwire.MilliSatoshi
	if c.VerifyOutputAmount {
		outputAmount = lnwire.MilliSatoshi(tx.TxOut[0].Value * 1000)
	}

	// Now check the information we have against the sphinx created by the payer
	if len(c.Sphinx) > 0 {
		// This incoming swap might be collecting debt, which would be deducted from the outputAmount
		// so we add it back up so the amount will match with the sphinx
		expectedAmount := outputAmount + lnwire.NewMSatFromSatoshis(c.Collect)
		err = sphinx.Validate(
			c.Sphinx,
			c.PaymentHash256,
			secrets.PaymentSecret,
			nodeKey,
			uint32(c.ExpirationHeight),
			expectedAmount,
			c.Network,
		)
		if err != nil {
			return fmt.Errorf("could not verify sphinx blob: %w", err)
		}
	}

	// Sign the fulfillment tx
	sig, err := signNativeSegwitInput(
		index,
		tx,
		userPrivateKey,
		htlcScript,
		btcutil.Amount(htlcOutputAmount),
	)
	if err != nil {
		return fmt.Errorf("could not sign fulfillment tx: %w", err)
	}

	txInput.Witness = wire.TxWitness{
		secrets.Preimage,
		sig,
		c.MuunSignature,
		htlcScript,
	}

	return nil
}

func (c *coinIncomingSwap) FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error {
	// Lookup invoice data matching this HTLC using the payment hash
	db, err := openDB()
	if err != nil {
		return err
	}
	defer db.Close()

	secrets, err := db.FindByPaymentHash(c.PaymentHash256)
	if err != nil {
		return fmt.Errorf("could not find invoice data for payment hash: %w", err)
	}

	derivedMuunKey, err := muunKey.DeriveTo(secrets.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive muun key: %w", err)
	}

	muunSignature, err := c.signature(index, tx, userKey.PublicKey(), derivedMuunKey.PublicKey(), derivedMuunKey)
	if err != nil {
		return err
	}
	c.MuunSignature = muunSignature
	return c.SignInput(index, tx, userKey, muunKey.PublicKey())
}

func (c *coinIncomingSwap) createHtlcScript(userPublicKey, muunPublicKey *HDPublicKey) ([]byte, error) {
	return createHtlcScript(
		userPublicKey.Raw(),
		muunPublicKey.Raw(),
		c.SwapServerPublicKey,
		c.ExpirationHeight,
		c.PaymentHash256,
	)
}

func (c *coinIncomingSwap) signature(index int, tx *wire.MsgTx, userKey *HDPublicKey, muunKey *HDPublicKey,
	signingKey *HDPrivateKey) ([]byte, error) {

	htlcTx := wire.MsgTx{}
	err := htlcTx.Deserialize(bytes.NewReader(c.HtlcTx))
	if err != nil {
		return nil, fmt.Errorf("could not deserialize htlc tx: %w", err)
	}

	htlcScript, err := c.createHtlcScript(userKey, muunKey)
	if err != nil {
		return nil, fmt.Errorf("could not create htlc script: %w", err)
	}

	htlcOutputIndex, err := c.findHtlcOutputIndex(&htlcTx, htlcScript)
	if err != nil {
		return nil, err
	}

	prevOutAmount := htlcTx.TxOut[htlcOutputIndex].Value

	sig, err := signNativeSegwitInput(
		index,
		tx,
		signingKey,
		htlcScript,
		btcutil.Amount(prevOutAmount),
	)
	if err != nil {
		return nil, fmt.Errorf("could not sign fulfillment tx: %w", err)
	}
	return sig, nil
}

func (c *coinIncomingSwap) findHtlcOutputIndex(htlcTx *wire.MsgTx, htlcScript []byte) (int, error) {
	witnessHash := sha256.Sum256(htlcScript)
	address, err := btcutil.NewAddressWitnessScriptHash(witnessHash[:], c.Network)
	if err != nil {
		return 0, fmt.Errorf("could not create htlc address: %w", err)
	}

	pkScript, err := txscript.PayToAddrScript(address)
	if err != nil {
		return 0, fmt.Errorf("could not create pk script: %w", err)
	}

	// Try to find the script we just built inside the HTLC output scripts
	for i, out := range htlcTx.TxOut {
		if bytes.Equal(pkScript, out.PkScript) {
			return i, nil
		}
	}

	return 0, errors.New("could not find valid htlc output in htlc tx")
}

func createHtlcScript(userPublicKey, muunPublicKey, swapServerPublicKey []byte, expiry int64, paymentHash []byte) ([]byte, error) {
	sb := txscript.NewScriptBuilder()
	sb.AddData(muunPublicKey)
	sb.AddOp(txscript.OP_CHECKSIG)
	sb.AddOp(txscript.OP_NOTIF)
	sb.AddOp(txscript.OP_DUP)
	sb.AddOp(txscript.OP_HASH160)
	sb.AddData(btcutil.Hash160(swapServerPublicKey))
	sb.AddOp(txscript.OP_EQUALVERIFY)
	sb.AddOp(txscript.OP_CHECKSIGVERIFY)
	sb.AddInt64(expiry)
	sb.AddOp(txscript.OP_CHECKLOCKTIMEVERIFY)
	sb.AddOp(txscript.OP_ELSE)
	sb.AddData(userPublicKey)
	sb.AddOp(txscript.OP_CHECKSIGVERIFY)
	sb.AddOp(txscript.OP_SIZE)
	sb.AddInt64(32)
	sb.AddOp(txscript.OP_EQUALVERIFY)
	sb.AddOp(txscript.OP_HASH160)
	sb.AddData(ripemd160(paymentHash))
	sb.AddOp(txscript.OP_EQUAL)
	sb.AddOp(txscript.OP_ENDIF)
	return sb.Script()
}
