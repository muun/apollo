package libwallet

import (
	"fmt"
	"github.com/shopspring/decimal"
	"io"
	"math"
	"net/http"
	"net/url"
	"strconv"
	"strings"

	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/btcsuitew/btcutilw"
	"github.com/muun/libwallet/errors"

	"github.com/btcsuite/btcd/txscript"
	"google.golang.org/protobuf/proto"
)

// These constants are here for clients usage.
const (
	AddressVersionV1      = addresses.V1
	AddressVersionV2      = addresses.V2
	AddressVersionV3      = addresses.V3
	AddressVersionV4      = addresses.V4
	AddressVersionV5      = addresses.V5
	AddressVersionSwapsV1 = addresses.SubmarineSwapV1
	AddressVersionSwapsV2 = addresses.SubmarineSwapV2
)

// MuunPaymentURI is muun's uri struct
type MuunPaymentURI struct {
	Address      string
	Label        string
	Message      string
	Amount       string
	Uri          string
	Bip70Url     string
	CreationTime string
	ExpiresTime  string
	Invoice      *Invoice
}

const (
	bitcoinScheme = "bitcoin:"
	muunScheme    = "muun:"
)

// GetPaymentURI builds a MuunPaymentURI from text (Bitcoin Uri, Muun Uri or address) and a network
func GetPaymentURI(rawInput string, network *Network) (*MuunPaymentURI, error) {

	bitcoinUri, components := buildUriFromString(rawInput, bitcoinScheme)
	if components == nil {
		return nil, errors.Errorf(ErrInvalidURI, "failed to parse uri %v", rawInput)
	}

	if components.Scheme != "bitcoin" {
		return nil, errors.New(ErrInvalidURI, "Invalid scheme")
	}

	address := components.Opaque

	// When URIs are bitcoin:// the address comes in host
	// this happens in iOS that mostly ignores bitcoin: format
	if len(address) == 0 {
		address = components.Host
	}

	queryValues, err := url.ParseQuery(components.RawQuery)
	if err != nil {
		return nil, errors.Errorf(ErrInvalidURI, "Couldn't parse query: %v", err)
	}

	var label, message, amount string
	var invoice *Invoice

	for queryParam := range queryValues {

		if strings.ToLower(queryParam) == "label" {
			label = queryValues[queryParam][0]
		}

		if strings.ToLower(queryParam) == "message" {
			message = queryValues[queryParam][0]
		}

		if strings.ToLower(queryParam) == "amount" {
			rawAmount := queryValues[queryParam][0]

			// We're adding some extra flexibility in case on-chain amount comes in scientific notation
			// (bip21 standard doesn't allow it, but we've seen it in the wild). So, we'll try to parse the amount
			// string into a float and then convert it back to a string but using decimal notation (that's the 'f'
			// format in FormatFloat).
			numericAmount, err := strconv.ParseFloat(rawAmount, 64)
			if err != nil || math.IsNaN(numericAmount) || math.IsInf(numericAmount, 0) {
				amount = rawAmount
				// TODO we should probably return an error here but that breaks current assumptions in newop state
				// machine (see TestInvalidAmountEmitsInvalidAddress in state_test.go)
			} else {
				amount = strconv.FormatFloat(numericAmount, 'f', -1, 64)
			}
		}

		if strings.ToLower(queryParam) == "lightning" {
			invoice, err = ParseInvoice(queryValues[queryParam][0], network)

			if err != nil {
				return nil, errors.Errorf(ErrInvalidURI, "Couldn't parse query: %v", err)
			}
		}
	}

	// legacy Apollo P2P/contacts check
	if strings.Contains(rawInput, "contacts/") {
		return &MuunPaymentURI{
			Label:   label,
			Message: message,
			Amount:  amount,
			Uri:     bitcoinUri,
		}, nil
	}

	//BIP70 check
	if len(queryValues["r"]) != 0 {

		if invoice != nil && invoice.Sats != 0 {
			return nil, errors.New(ErrInvalidURI, "Bip70 uris can't be used with lightning invoices with amount")
		}

		if len(address) > 0 {
			return &MuunPaymentURI{
				Address:  address,
				Label:    label,
				Message:  message,
				Amount:   amount,
				Uri:      bitcoinUri,
				Bip70Url: queryValues["r"][0],
				Invoice:  invoice,
			}, nil
		}

		return &MuunPaymentURI{
			Label:    label,
			Message:  message,
			Amount:   amount,
			Uri:      bitcoinUri,
			Bip70Url: queryValues["r"][0],
			Invoice:  invoice,
		}, nil
	}

	// Bech32 check
	decodedAddress, err := btcutilw.DecodeAddress(address, network.network)
	if err != nil {
		return nil, fmt.Errorf("invalid address: %w", err)
	}

	if !decodedAddress.IsForNet(network.network) {
		return nil, errors.New(ErrInvalidURI, "Network mismatch")
	}

	// Check for unified QR Uris to have the same amount if they have one
	if invoice != nil {

		if invoice.Sats != 0 {
			invoiceAmount := decimal.NewFromInt(invoice.Sats).Div(decimal.NewFromInt(100_000_000)).String()

			// We ONLY mark the uri as invalid (and return an error) if both amount exists and are different. Otherwise,
			// we will allow a way to move forward with the payment by assuming the lightning part is the correct one.
			if amount != "" {
				if invoiceAmount != amount {
					return nil, errors.New(ErrInvalidURI, "Amount mismatch")
				}
			}
		}
	}

	return &MuunPaymentURI{
		Address: decodedAddress.String(),
		Label:   label,
		Message: message,
		Amount:  amount,
		Uri:     bitcoinUri,
		Invoice: invoice,
	}, nil

}

// DoPaymentRequestCall builds a MuunPaymentUri from a url and a network. Handling BIP70 to 72
func DoPaymentRequestCall(url string, network *Network) (*MuunPaymentURI, error) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request to: %s", url)
	}

	req.Header.Set("Accept", "application/bitcoin-paymentrequest")

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return nil, errors.Errorf(ErrNetwork, "failed to make request to: %s", url)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, errors.Errorf(ErrNetwork, "Failed to read body response: %w", err)
	}

	payReq := &PaymentRequest{}
	err = proto.Unmarshal(body, payReq)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshal payment request: %w", err)
	}

	payDetails := &PaymentDetails{}

	err = proto.Unmarshal(payReq.SerializedPaymentDetails, payDetails)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshall payment details: %w", err)
	}

	if len(payDetails.Outputs) == 0 {
		return nil, fmt.Errorf("no outputs provided")
	}

	address, err := getAddressFromScript(payDetails.Outputs[0].Script, network)
	if err != nil {
		return nil, fmt.Errorf("failed to get address: %w", err)
	}

	amount := float64(payDetails.Outputs[0].Amount) / 100_000_000

	return &MuunPaymentURI{
		Address:      address,
		Message:      payDetails.Memo,
		Amount:       strconv.FormatFloat(amount, 'f', -1, 64),
		Bip70Url:     url,
		CreationTime: strconv.FormatUint(payDetails.Time, 10),
		ExpiresTime:  strconv.FormatUint(payDetails.Expires, 10),
	}, nil
}

func getAddressFromScript(script []byte, network *Network) (string, error) {
	pkScript, err := txscript.ParsePkScript(script)
	if err != nil {
		return "", err
	}
	address, err := pkScript.Address(network.network)
	if err != nil {
		return "", err
	}
	return address.String(), nil
}

func buildUriFromString(rawInput string, targetScheme string) (string, *url.URL) {
	newUri := strings.Replace(rawInput, muunScheme, targetScheme, 1)
	if !strings.HasPrefix(strings.ToLower(newUri), targetScheme) {
		newUri = targetScheme + rawInput
	}

	components, err := url.Parse(newUri)
	if err != nil {
		return "", nil
	}

	return newUri, components
}
