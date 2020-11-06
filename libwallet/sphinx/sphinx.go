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
	// Validate payment secret if it exists
	if payload.MPP != nil {
		paymentAddr := payload.MPP.PaymentAddr()

		if !bytes.Equal(paymentAddr[:], paymentSecret) {
			return errors.New("sphinx payment secret does not match")
		}
		if amount != 0 && payload.ForwardingInfo().AmountToForward > amount {
			return fmt.Errorf(
				"sphinx payment amount does not match (%v != %v)", amount, payload.ForwardingInfo().AmountToForward,
			)
		}
	}
	return nil
}
