package libwallet

import (
	fmt "fmt"
	"strings"

	"github.com/lightningnetwork/lnd/zpay32"
	"github.com/pkg/errors"
)

// Invoice is muun's invoice struct
type Invoice struct {
	RawInvoice      string
	FallbackAddress *MuunPaymentURI
	Network         *Network
	MilliSat        string
	Destination     []byte
	PaymentHash     [32]byte
	Expiry          int64
	Description     string
}

const lightningScheme = "lightning:"

// ParseInvoice parses an Invoice from an invoice string and a network
func ParseInvoice(invoice string, network *Network) (*Invoice, error) {

	if strings.HasPrefix(strings.ToLower(invoice), lightningScheme) {
		// Remove lightning scheme from rawInvoice
		invoice = invoice[len(lightningScheme):]
	}

	parsedInvoice, err := zpay32.Decode(invoice, network.network)
	if err != nil {
		return nil, errors.Wrapf(err, "Couldnt parse invoice")
	}

	var fallbackAdd *MuunPaymentURI

	if parsedInvoice.FallbackAddr != nil {
		fallbackAdd, err = GetPaymentURI(parsedInvoice.FallbackAddr.String(), network)
		if err != nil {
			return nil, errors.Wrapf(err, "Couldnt get address")
		}
	}

	var description string
	if parsedInvoice.Description != nil {
		description = *parsedInvoice.Description
	}

	var milliSats string
	if parsedInvoice.MilliSat != nil {
		milliSats = fmt.Sprintf("%v", uint64(*parsedInvoice.MilliSat))
	}

	return &Invoice{
		RawInvoice:      invoice,
		FallbackAddress: fallbackAdd,
		Network:         network,
		MilliSat:        milliSats,
		Destination:     parsedInvoice.Destination.SerializeCompressed(),
		PaymentHash:     *parsedInvoice.PaymentHash,
		Expiry:          parsedInvoice.Timestamp.Unix() + int64(parsedInvoice.Expiry().Seconds()),
		Description:     description,
	}, nil
}
