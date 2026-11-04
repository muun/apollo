package libwallet

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"
	"strings"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"
	lndinput "github.com/lightningnetwork/lnd/input"
	"github.com/lightningnetwork/lnd/lnwire"

	"github.com/muun/libwallet/base/data/walletdb"
	"github.com/muun/libwallet/base/domain/cryptography/hdpath"
	"github.com/muun/libwallet/legacy/addresses"
	"github.com/muun/libwallet/legacy/btcsuitew/txscriptw"
	"github.com/muun/libwallet/legacy/sphinx"
	"github.com/muun/libwallet/platform/secret"
)

// changeBranchIndex is the branch, relative to the base key path, where change addresses live.
// It mirrors Schema.DerivationBranches.CHANGE in the common module.
const changeBranchIndex = 0

type IncomingSwap struct {
	Htlc             *IncomingSwapHtlc
	SphinxPacket     []byte
	PaymentHash      []byte
	PaymentAmountSat int64
	CollectSat       int64
}

type IncomingSwapHtlc struct {
	HtlcTx              []byte
	ExpirationHeight    int64
	SwapServerPublicKey []byte
}

type IncomingSwapFulfillmentData struct {
	FulfillmentTx      []byte
	CosignerSignature  []byte
	OutputVersion      int
	OutputPath         string
	MerkleTree         []byte // unused
	HtlcBlock          []byte // unused
	BlockHeight        int64  // unused
	ConfirmationTarget int64  // to validate fee rate, unused for now
}

type IncomingSwapFulfillmentResult struct {
	FulfillmentTx []byte
	Preimage      []byte
}

// String returns the fulfillment tx's length, without the tx and the preimage.
func (r IncomingSwapFulfillmentResult) String() string {
	return r.LogValue().String()
}

// Format prints String for every verb.
func (r IncomingSwapFulfillmentResult) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, r.String())
}

// LogValue returns the fulfillment tx's length, without the tx and the preimage.
func (r IncomingSwapFulfillmentResult) LogValue() slog.Value {
	return slog.GroupValue(slog.Int("fulfillment_tx_length", len(r.FulfillmentTx)))
}

func (s *IncomingSwap) getInvoice() (*walletdb.Invoice, error) {
	var invoice *walletdb.Invoice
	if err := Pool.WithDB(func(db *walletdb.DB) error {
		var err error
		invoice, err = db.FindByPaymentHash(s.PaymentHash)
		return err
	}); err != nil {
		return nil, err
	}
	return invoice, nil
}

// VerifyFulfillable checks that an incoming swap is fulfillable.
func (s *IncomingSwap) VerifyFulfillable(userKey *HDPrivateKey, net *Network) error {
	paymentHash := s.PaymentHash

	if len(paymentHash) != 32 {
		return errors.Errorf("VerifyFulfillable: received invalid hash len %v", len(paymentHash))
	}

	// Lookup invoice data matching this HTLC using the payment hash
	invoice, err := s.getInvoice()
	if err != nil {
		return errors.Errorf(
			"VerifyFulfillable: could not find invoice data for payment hash: %w",
			err,
		)
	}

	parentPath, err := hdpath.Parse(invoice.KeyPath)
	if err != nil {
		return errors.Errorf(
			"VerifyFulfillable: invoice key path is not valid: %v", invoice.KeyPath,
		)
	}
	identityKeyPath := parentPath.Child(identityKeyChildIndex)

	nodeHDKey, err := userKey.DeriveTo(identityKeyPath.String())
	if err != nil {
		return errors.Errorf("VerifyFulfillable: failed to derive key: %w", err)
	}
	nodeKey, err := nodeHDKey.key.ECPrivKey()
	if err != nil {
		return errors.Errorf("VerifyFulfillable: failed to get priv key: %w", err)
	}

	// implementation is allowed to send a few extra sats
	if invoice.AmountSat != 0 && invoice.AmountSat > s.PaymentAmountSat {
		return errors.Errorf(
			"VerifyFulfillable: payment amount (%v) does not match invoice amount (%v)",
			s.PaymentAmountSat,
			invoice.AmountSat,
		)
	}

	if len(s.SphinxPacket) == 0 {
		return nil
	}

	err = sphinx.Validate(
		s.SphinxPacket,
		paymentHash,
		invoice.PaymentSecret,
		nodeKey,
		0, // This is used internally by the sphinx decoder but it's not needed
		lnwire.MilliSatoshi(uint64(s.PaymentAmountSat)*1000),
		net.network,
	)
	if err != nil {
		return errors.Errorf("VerifyFulfillable: invalid sphinx: %w", err)
	}

	return nil
}

// Fulfill validates and creates a fulfillment tx for the incoming swap.
// It returns the fullfillment tx and the preimage.
func (s *IncomingSwap) Fulfill(
	data *IncomingSwapFulfillmentData,
	userKey *HDPrivateKey, cosignerKey *HDPublicKey,
	net *Network) (*IncomingSwapFulfillmentResult, error) {

	if s.Htlc == nil {
		return nil, errors.Errorf("Fulfill: missing swap htlc data")
	}

	// TODO: add debug logs (e.g debug logging capabilities in libwallet)

	err := s.VerifyFulfillable(userKey, net)
	if err != nil {
		return nil, err
	}

	// Validate the fullfillment tx proposed by Muun.
	tx := wire.MsgTx{}
	err = tx.DeserializeNoWitness(bytes.NewReader(data.FulfillmentTx))
	if err != nil {
		return nil, errors.Errorf("Fulfill: could not deserialize fulfillment tx: %w", err)
	}
	if len(tx.TxIn) != 1 {
		return nil, errors.Errorf(
			"Fulfill: expected fulfillment tx to have exactly 1 input, found %d",
			len(tx.TxIn),
		)
	}
	if len(tx.TxOut) != 1 {
		return nil, errors.Errorf(
			"Fulfill: expected fulfillment tx to have exactly 1 output, found %d",
			len(tx.TxOut),
		)
	}

	err = verifyFulfillmentOutput(tx.TxOut[0], data, userKey, cosignerKey, net)
	if err != nil {
		return nil, err
	}

	// Lookup invoice data matching this HTLC using the payment hash
	invoice, err := s.getInvoice()
	if err != nil {
		return nil, errors.Errorf("Fulfill: could not find invoice data for payment hash: %w", err)
	}

	// Sign the htlc input (there is only one, at index 0)
	coin := coinIncomingSwap{
		Network:             net.network,
		CosignerSignature:   data.CosignerSignature,
		Sphinx:              s.SphinxPacket,
		HtlcTx:              s.Htlc.HtlcTx,
		PaymentHash256:      s.PaymentHash,
		SwapServerPublicKey: s.Htlc.SwapServerPublicKey,
		ExpirationHeight:    s.Htlc.ExpirationHeight,
		VerifyOutputAmount:  true,
		Collect:             btcutil.Amount(s.CollectSat),
	}
	err = coin.SignInput(0, &tx, userKey, cosignerKey)
	if err != nil {
		return nil, err
	}

	// Serialize and return the signed fulfillment tx
	var buf bytes.Buffer
	err = tx.Serialize(&buf)
	if err != nil {
		return nil, errors.Errorf("Fulfill: could not serialize fulfillment tx: %w", err)
	}
	return &IncomingSwapFulfillmentResult{
		FulfillmentTx: buf.Bytes(),
		Preimage:      invoice.Preimage,
	}, nil
}

// verifyFulfillmentOutput checks that the fulfillment tx pays to an address of this wallet.
//
// Houston crafts the fulfillment tx and picks its output from the user's change address pool. The
// only way to know that output is ours is to rebuild its script from our own keys.
func verifyFulfillmentOutput(
	output *wire.TxOut,
	data *IncomingSwapFulfillmentData,
	userKey *HDPrivateKey,
	cosignerKey *HDPublicKey,
	net *Network,
) error {

	basePath, err := hdpath.Parse(userKey.Path)
	if err != nil {
		return errors.Errorf("Fulfill: user key path %v is not valid: %w", userKey.Path, err)
	}
	outputPath, err := hdpath.Parse(data.OutputPath)
	if err != nil {
		return errors.Errorf("Fulfill: output path %v is not valid: %w", data.OutputPath, err)
	}

	// Houston only ever fulfills to a change address, which is a single unhardened child of the
	// change branch. Other paths would be ours too, but we have no reason to accept them.
	if !outputPath.HasPrefix(basePath) {
		return errors.Errorf(
			"Fulfill: expected output path %v to hang from the user key at %v",
			data.OutputPath, userKey.Path,
		)
	}

	indexes := outputPath.IndexesFrom(basePath)
	if len(indexes) != 2 {
		return errors.Errorf(
			"Fulfill: expected output path %v to be two levels below the user key",
			data.OutputPath,
		)
	}

	branch, addressIndex := indexes[0], indexes[1]
	if branch.Index != changeBranchIndex || branch.Hardened || addressIndex.Hardened {
		return errors.Errorf(
			"Fulfill: expected output path %v to be an unhardened child of the change branch",
			data.OutputPath,
		)
	}

	derivedUserKey, err := userKey.PublicKey().DeriveTo(data.OutputPath)
	if err != nil {
		return errors.Errorf(
			"Fulfill: failed to derive user key to output path %v: %w", data.OutputPath, err,
		)
	}
	derivedCosignerKey, err := cosignerKey.DeriveTo(data.OutputPath)
	if err != nil {
		return errors.Errorf(
			"Fulfill: failed to derive cosigner key to output path %v: %w", data.OutputPath, err,
		)
	}

	address, err := addresses.Create(
		data.OutputVersion,
		&derivedUserKey.key,
		&derivedCosignerKey.key,
		data.OutputPath,
		net.network,
	)
	if err != nil {
		return errors.Errorf(
			"Fulfill: failed to build the output address with version %v: %w",
			data.OutputVersion, err,
		)
	}

	script, err := addressToScript(address.Address(), net)
	if err != nil {
		return errors.Errorf("Fulfill: %w", err)
	}

	if !bytes.Equal(output.PkScript, script) {
		return errors.Errorf(
			"Fulfill: expected fulfillment tx to pay to %v (script %v, path %v, version %v), "+
				"found script %v",
			address.Address(), hex.EncodeToString(script),
			data.OutputPath, data.OutputVersion,
			hex.EncodeToString(output.PkScript),
		)
	}

	return nil
}

// FulfillFullDebt gives the preimage matching a payment hash if we have it
func (s *IncomingSwap) FulfillFullDebt() (*IncomingSwapFulfillmentResult, error) {

	// Lookup invoice data matching this HTLC using the payment hash
	var secrets *walletdb.Invoice
	if err := Pool.WithDB(func(db *walletdb.DB) error {
		var err error
		secrets, err = db.FindByPaymentHash(s.PaymentHash)
		return err
	}); err != nil {
		return nil, errors.Errorf(
			"FulfillFullDebt: could not find invoice data for payment hash: %w",
			err,
		)
	}

	return &IncomingSwapFulfillmentResult{
		FulfillmentTx: nil,
		Preimage:      secrets.Preimage,
	}, nil
}

type coinIncomingSwap struct {
	Network             *chaincfg.Params
	CosignerSignature   []byte
	Sphinx              []byte
	HtlcTx              []byte
	PaymentHash256      []byte
	SwapServerPublicKey []byte
	ExpirationHeight    int64
	VerifyOutputAmount  bool // used only for fulfilling swaps through IncomingSwap
	Collect             btcutil.Amount
	Preimage            secret.Bytes[[]byte]
	HtlcOutputKeyPath   string
}

// NOTE: this method only works on segwit v0 txs
func (c *coinIncomingSwap) SignInput(
	index int,
	tx *wire.MsgTx,
	userKey *HDPrivateKey,
	cosignerKey *HDPublicKey,
) error {
	// Deserialize the HTLC transaction
	htlcTx := wire.MsgTx{}
	err := htlcTx.Deserialize(bytes.NewReader(c.HtlcTx))
	if err != nil {
		return errors.Errorf("could not deserialize htlc tx: %w", err)
	}

	// Lookup invoice data matching this HTLC using the payment hash. We keep the lookup result
	// separate from WithDB's own error so that a genuine "DB unavailable" failure surfaces
	// immediately, instead of being masked as a missing invoice and routed into the recovery
	// path below (which would sign with fallback-constructed secrets).
	var secrets *walletdb.Invoice
	var lookupErr error
	if err := Pool.WithDB(func(db *walletdb.DB) error {
		secrets, lookupErr = db.FindByPaymentHash(c.PaymentHash256)
		return nil
	}); err != nil {
		return err
	}
	if secrets == nil {

		// Note: there's an edge case where fulfillment txs can be dropped and clients may forgot
		// the invoice secrets (e.g if they logout) thus being unable to spend incoming swap inputs.
		// Here we try to collaborate with Houston (in the cases where preimage data was previously
		// revealed) to allow clients to spend these inputs that otherwise would be un-spendable.
		if c.Preimage.Len() > 0 {

			// There's actually several derivation paths involved in incoming swaps, all of which
			// are needed by the apps to sign an HTLC input. However, apps register only one of
			// these fully-derived paths. Lucky for us, apps internally derive all these paths out
			// of a single base path. We can simply drop the last level from the path we have to
			// obtain the common root.
			// See [ GenerateInvoiceSecrets ] in invoices.go for impl details.

			// Backend provides the htlcOutputKeypath, but what we store in the Invoice secrets db
			// table is the invoice's base key derivation path.
			index := strings.LastIndex(c.HtlcOutputKeyPath, "/")
			invoiceBaseKeyPath := c.HtlcOutputKeyPath[:index]

			secrets = &walletdb.Invoice{
				Preimage: c.Preimage.Reveal(),
				KeyPath:  invoiceBaseKeyPath,
			}
		} else {
			return errors.Errorf("could not find invoice data for payment hash: %w", lookupErr)
		}
	}

	parentPath, err := hdpath.Parse(secrets.KeyPath)
	if err != nil {
		return errors.Errorf("invalid invoice key path: %w", err)
	}

	// Recreate the HTLC script to verify it matches the transaction. For this
	// we must derive the keys used in the HTLC script
	htlcKeyPath := parentPath.Child(htlcKeyChildIndex)

	// Derive first the private key, which we are going to use for signing later
	userPrivateKey, err := userKey.DeriveTo(htlcKeyPath.String())
	if err != nil {
		return err
	}
	userPublicKey := userPrivateKey.PublicKey()

	cosignerPublicKey, err := cosignerKey.DeriveTo(htlcKeyPath.String())
	if err != nil {
		return err
	}

	htlcScript, err := c.createHtlcScript(userPublicKey, cosignerPublicKey)
	if err != nil {
		return errors.Errorf("could not create htlc script: %w", err)
	}

	// Try to find the script we just built inside the HTLC output scripts
	htlcOutputIndex, err := c.findHtlcOutputIndex(&htlcTx, htlcScript)
	if err != nil {
		return err
	}

	// Next, we must validate the sphinx data. We derive the client identity
	// key used by this invoice with the key path stored in the db.
	identityKeyPath := parentPath.Child(identityKeyChildIndex)

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
		return errors.Errorf("expected fulfillment tx input to point to htlc tx")
	}
	if txInput.PreviousOutPoint.Index != uint32(htlcOutputIndex) {
		return errors.Errorf("expected fulfillment tx input to point to correct htlc output")
	}

	sigHashes := lndinput.NewTxSigHashesV0Only(tx)

	cosignerSigKey, err := cosignerPublicKey.key.ECPubKey()
	if err != nil {
		return err
	}

	// Verify cosigner signature
	htlcOutputAmount := htlcTx.TxOut[htlcOutputIndex].Value
	err = verifyTxWitnessSignature(
		tx,
		sigHashes,
		index,
		htlcOutputAmount,
		htlcScript,
		c.CosignerSignature,
		cosignerSigKey,
	)
	if err != nil {
		return errors.Errorf("could not verify cosigner signature for htlc: %w", err)
	}

	var outputAmount, expectedAmount lnwire.MilliSatoshi
	if c.VerifyOutputAmount {
		outputAmount = lnwire.MilliSatoshi(tx.TxOut[0].Value * 1000)

		// This incoming swap might be collecting debt, which would be deducted from the
		// outputAmount so we add it back up so the amount will match with the sphinx
		expectedAmount = outputAmount + lnwire.NewMSatFromSatoshis(c.Collect)
	}

	// Now check the information we have against the sphinx created by the payer.
	// Note: we avoid this validation if we're collaboratively signing with Houston
	// (since we don't know payment secret)
	if len(c.Sphinx) > 0 && len(secrets.PaymentSecret) > 0 {
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
			return errors.Errorf("could not verify sphinx blob: %w", err)
		}
	}

	// Sign the fulfillment tx
	sig, err := signNativeSegwitInputV0(
		index,
		tx,
		userPrivateKey,
		htlcScript,
		btcutil.Amount(htlcOutputAmount),
	)
	if err != nil {
		return errors.Errorf("could not sign fulfillment tx: %w", err)
	}

	txInput.Witness = wire.TxWitness{
		secrets.Preimage,
		sig,
		c.CosignerSignature,
		htlcScript,
	}

	return nil
}

func (c *coinIncomingSwap) FullySignInput(
	index int,
	tx *wire.MsgTx,
	userKey, cosignerKey *HDPrivateKey,
) error {
	// Lookup invoice data matching this HTLC using the payment hash
	var secrets *walletdb.Invoice
	if err := Pool.WithDB(func(db *walletdb.DB) error {
		var err error
		secrets, err = db.FindByPaymentHash(c.PaymentHash256)
		return err
	}); err != nil {
		return errors.Errorf("could not find invoice data for payment hash: %w", err)
	}

	derivedCosignerKey, err := cosignerKey.DeriveTo(secrets.KeyPath)
	if err != nil {
		return errors.Errorf("failed to derive cosigner key: %w", err)
	}

	cosignerSignature, err := c.signature(
		index,
		tx,
		userKey.PublicKey(),
		derivedCosignerKey.PublicKey(),
		derivedCosignerKey,
	)
	if err != nil {
		return err
	}
	c.CosignerSignature = cosignerSignature
	return c.SignInput(index, tx, userKey, cosignerKey.PublicKey())
}

// Format prints LogValue for every verb.
// Format and LogValue take value receivers, unlike the other methods, so values print
// redacted too, not only pointers.
func (c coinIncomingSwap) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, c.LogValue().String())
}

// LogValue returns the coin without the preimage.
func (c coinIncomingSwap) LogValue() slog.Value {
	return slog.GroupValue(
		slog.String("htlc_output_key_path", c.HtlcOutputKeyPath),
		slog.Int64("expiration_height", c.ExpirationHeight),
		slog.String("payment_hash", hex.EncodeToString(c.PaymentHash256)),
		slog.String("swap_server_public_key", hex.EncodeToString(c.SwapServerPublicKey)),
		slog.Int64("collect_sat", int64(c.Collect)),
		slog.Bool("verify_output_amount", c.VerifyOutputAmount),
	)
}

func (c *coinIncomingSwap) createHtlcScript(
	userPublicKey, cosignerPublicKey *HDPublicKey,
) ([]byte, error) {
	return createHtlcScript(
		userPublicKey.Raw(),
		cosignerPublicKey.Raw(),
		c.SwapServerPublicKey,
		c.ExpirationHeight,
		c.PaymentHash256,
	)
}

func (c *coinIncomingSwap) signature(
	index int,
	tx *wire.MsgTx,
	userKey *HDPublicKey,
	cosignerKey *HDPublicKey,
	signingKey *HDPrivateKey,
) ([]byte, error) {

	htlcTx := wire.MsgTx{}
	err := htlcTx.Deserialize(bytes.NewReader(c.HtlcTx))
	if err != nil {
		return nil, errors.Errorf("could not deserialize htlc tx: %w", err)
	}

	htlcScript, err := c.createHtlcScript(userKey, cosignerKey)
	if err != nil {
		return nil, errors.Errorf("could not create htlc script: %w", err)
	}

	htlcOutputIndex, err := c.findHtlcOutputIndex(&htlcTx, htlcScript)
	if err != nil {
		return nil, err
	}

	prevOutAmount := htlcTx.TxOut[htlcOutputIndex].Value

	sig, err := signNativeSegwitInputV0(
		index,
		tx,
		signingKey,
		htlcScript,
		btcutil.Amount(prevOutAmount),
	)
	if err != nil {
		return nil, errors.Errorf("could not sign fulfillment tx: %w", err)
	}
	return sig, nil
}

func (c *coinIncomingSwap) findHtlcOutputIndex(htlcTx *wire.MsgTx, htlcScript []byte) (int, error) {
	witnessHash := sha256.Sum256(htlcScript)
	address, err := btcutil.NewAddressWitnessScriptHash(witnessHash[:], c.Network)
	if err != nil {
		return 0, errors.Errorf("could not create htlc address: %w", err)
	}

	pkScript, err := txscriptw.PayToAddrScript(address)
	if err != nil {
		return 0, errors.Errorf("could not create pk script: %w", err)
	}

	// Try to find the script we just built inside the HTLC output scripts
	for i, out := range htlcTx.TxOut {
		if bytes.Equal(pkScript, out.PkScript) {
			return i, nil
		}
	}

	return 0, errors.New("could not find valid htlc output in htlc tx")
}

func createHtlcScript(
	userPublicKey, cosignerPublicKey, swapServerPublicKey []byte,
	expiry int64,
	paymentHash []byte,
) ([]byte, error) {
	sb := txscript.NewScriptBuilder()
	sb.AddData(cosignerPublicKey)
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
