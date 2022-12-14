package libwallet

import (
	"fmt"
	"github.com/muun/libwallet/errors"
	"github.com/shopspring/decimal"
)

// GenerateBip21Uri generates the serialized version (string) of a bitcoin uri from a MuunPaymentUri
func GenerateBip21Uri(uri *MuunPaymentURI) (string, error) {

	if uri.Address == "" {
		return "", errors.New(ErrInvalidURI, "On chain address is required for bip21 uris")
	}

	var invoice = ""
	if uri.Invoice != nil {
		invoice = "lightning=" + uri.Invoice.RawInvoice

		if uri.Invoice.Sats != 0 && uri.Amount != "" {
			invoiceAmount := decimal.NewFromInt(uri.Invoice.Sats).Div(decimal.NewFromInt(100_000_000)).String()
			if invoiceAmount != uri.Amount {
				return "", errors.New(ErrInvalidURI, fmt.Sprintf("Amount mismatch %v: %v", invoiceAmount, uri.Amount))
			}
		}
	}

	var amount = ""
	if uri.Amount != "" {
		amount = "amount=" + uri.Amount
	}

	if amount != "" && invoice != "" {
		invoice = "&" + invoice
	}

	return bitcoinScheme + uri.Address + "?" + amount + invoice, nil
}
