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
		panic(err)
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

	// Validate payment secret if it exists
	if payload.MPP != nil {
		paymentAddr := payload.MPP.PaymentAddr()
		total := payload.MultiPath().TotalMsat()

		if !bytes.Equal(paymentAddr[:], paymentSecret) {
			return errors.New("sphinx payment secret does not match")
		}

		if amountToForward < total {
			return fmt.Errorf("payment is multipart. forwarded amt = %v, total amt = %v", amountToForward, total)
		}
	}
	return nil
}
