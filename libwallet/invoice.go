package libwallet

import (
	"fmt"

	"github.com/lightningnetwork/lnd/zpay32"
	"github.com/muun/libwallet/errors"
)

// Invoice is muun's invoice struct
type Invoice struct {
	RawInvoice      string
	FallbackAddress *MuunPaymentURI
	Network         *Network
	MilliSat        string
	Destination     []byte
	PaymentHash     []byte
	Expiry          int64
	Description     string
	Sats            int64
}

const lightningScheme = "lightning:"

// ParseInvoice parses an Invoice from an invoice string and a network
func ParseInvoice(rawInput string, network *Network) (*Invoice, error) {

	_, components := buildUriFromString(rawInput, lightningScheme)
	if components == nil {
		return nil, errors.Errorf(ErrInvalidInvoice, "failed to parse uri %v", rawInput)
	}

	if components.Scheme != "lightning" {
		return nil, errors.Errorf(ErrInvalidInvoice, "invalid scheme %v", components.Scheme)
	}

	invoice := components.Opaque

	// When URIs are scheme:// the address comes in host
	// this happens in iOS that mostly ignores scheme: format
	if len(invoice) == 0 {
		invoice = components.Host
	}

	parsedInvoice, err := zpay32.Decode(invoice, network.network)
	if err != nil {
		return nil, errors.Errorf(ErrInvalidInvoice, "Couldn't parse invoice: %w", err)
	}

	var fallbackAdd *MuunPaymentURI

	if parsedInvoice.FallbackAddr != nil {
		fallbackAdd, err = GetPaymentURI(parsedInvoice.FallbackAddr.String(), network)
		if err != nil {
			return nil, errors.Errorf(ErrInvalidInvoice, "Couldn't get address: %w", err)
		}
	}

	var description string
	if parsedInvoice.Description != nil {
		description = *parsedInvoice.Description
	}

	var milliSats string
	var sats int64
	if parsedInvoice.MilliSat != nil {
		milliSat := uint64(*parsedInvoice.MilliSat)
		milliSats = fmt.Sprintf("%v", milliSat)
		sats = int64(milliSat / 1000)
	}

	return &Invoice{
		RawInvoice:      invoice,
		FallbackAddress: fallbackAdd,
		Network:         network,
		MilliSat:        milliSats,
		Destination:     parsedInvoice.Destination.SerializeCompressed(),
		PaymentHash:     parsedInvoice.PaymentHash[:],
		Expiry:          parsedInvoice.Timestamp.Unix() + int64(parsedInvoice.Expiry().Seconds()),
		Description:     description,
		Sats:            sats,
	}, nil
}
