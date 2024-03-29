package sphinx

import (
	"bytes"
	"errors"
	"fmt"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcd/chaincfg"
	lndsphinx "github.com/lightningnetwork/lightning-onion"
	"github.com/lightningnetwork/lnd/htlcswitch/hop"
	"github.com/lightningnetwork/lnd/lnwire"
)

// Validate checks that the onion blob is valid and matches the invoice parameters.
// Pass 0 as amount to skip amount validation.
func Validate(
	onionBlob []byte,
	paymentHash []byte,
	paymentSecret []byte,
	nodeKey *btcec.PrivateKey,
	expiry uint32,
	amount lnwire.MilliSatoshi,
	net *chaincfg.Params,
) error {
	router := lndsphinx.NewRouter(nodeKey, net, lndsphinx.NewMemoryReplayLog())
	if err := router.Start(); err != nil {
		return fmt.Errorf("could not start router for validating onion blob: %w", err)
	}
	onionProcessor := hop.NewOnionProcessor(router)
	onionProcessor.Start()
	iterator, code := onionProcessor.DecodeHopIterator(
		bytes.NewReader(onionBlob),
		paymentHash,
		expiry,
	)
	if code != lnwire.CodeNone {
		return fmt.Errorf("failed decode sphinx due to %v", code.String())
	}
	payload, err := iterator.HopPayload()
	if err != nil {
		return err
	}

	amountToForward := payload.ForwardingInfo().AmountToForward
	if amount != 0 && amountToForward > amount {
		return fmt.Errorf(
			"sphinx payment amount does not match (%v != %v)", amount, amountToForward,
		)
	}

	// We require TLV onion
	if payload.MPP == nil {
		return fmt.Errorf("TLV onion is missing")
	}

	// We require payment secret
	paymentAddr := payload.MPP.PaymentAddr()
	if !bytes.Equal(paymentAddr[:], paymentSecret) {
		return errors.New("sphinx payment secret does not match")
	}

	// We don't accept multipart
	total := payload.MultiPath().TotalMsat()
	if amountToForward < total {
		return fmt.Errorf("payment is multipart. forwarded amt = %v, total amt = %v", amountToForward, total)
	}

	return nil
}
