package libwallet

import (
	"fmt"
	"io/ioutil"
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
	URI          string
	BIP70Url     string
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

	if len(queryValues["label"]) != 0 {
		label = queryValues["label"][0]
	}

	if len(queryValues["message"]) != 0 {
		message = queryValues["message"][0]
	}

	if len(queryValues["amount"]) != 0 {
		amount = queryValues["amount"][0]
	}

	if len(queryValues["lightning"]) != 0 {
		invoice, err := ParseInvoice(queryValues["lightning"][0], network)

		if err == nil {
			return &MuunPaymentURI{Invoice: invoice}, nil
		}
	}

	//BIP70 check
	if len(queryValues["r"]) != 0 {
		if len(address) > 0 {
			return &MuunPaymentURI{
				Address:  address,
				Label:    label,
				Message:  message,
				Amount:   amount,
				URI:      bitcoinUri,
				BIP70Url: queryValues["r"][0],
			}, nil
		}
		return &MuunPaymentURI{
			Label:    label,
			Message:  message,
			Amount:   amount,
			URI:      bitcoinUri,
			BIP70Url: queryValues["r"][0],
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

	return &MuunPaymentURI{
		Address: decodedAddress.String(),
		Label:   label,
		Message: message,
		Amount:  amount,
		URI:     bitcoinUri,
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

	body, err := ioutil.ReadAll(resp.Body)
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

	return &MuunPaymentURI{
		Address:      address,
		Message:      payDetails.Memo,
		Amount:       strconv.FormatUint(payDetails.Outputs[0].Amount, 10),
		BIP70Url:     url,
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
	newUri := rawInput

	newUri = strings.Replace(newUri, muunScheme, targetScheme, 1)

	if !strings.HasPrefix(strings.ToLower(newUri), targetScheme) {
		newUri = targetScheme + rawInput
	}

	components, err := url.Parse(newUri)
	if err != nil {
		return "", nil
	}

	return newUri, components
}
