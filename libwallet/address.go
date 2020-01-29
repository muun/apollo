package libwallet

import (
	"io/ioutil"
	"net/http"
	"net/url"
	"strconv"
	"strings"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcutil"
	"github.com/golang/protobuf/proto"
	"github.com/pkg/errors"
)

// These constants are here for clients usage.
const (
	AddressVersionSwapsV1 = 101
	AddressVersionSwapsV2 = 102
)

type AddressVersion int

const (
	addressV1              AddressVersion = 1
	addressV2              AddressVersion = 2
	addressV3              AddressVersion = 3
	addressV4              AddressVersion = 4
	addressSubmarineSwapV1 AddressVersion = AddressVersionSwapsV1
	addressSubmarineSwapV2 AddressVersion = AddressVersionSwapsV2
)

type muunAddress struct {
	version        AddressVersion
	derivationPath string
	address        string
}

func newMuunAddress(version AddressVersion, userPublicKey, muunPublicKey *HDPublicKey) (MuunAddress, error) {
	if userPublicKey.Path != muunPublicKey.Path {
		return nil, errors.Errorf("paths must match for address generation (%v != %v)", userPublicKey.Path, muunPublicKey.Path)
	}

	switch version {
	case addressV1:
		return CreateAddressV1(userPublicKey)
	case addressV2:
		return CreateAddressV2(userPublicKey, muunPublicKey)
	case addressV3:
		return CreateAddressV3(userPublicKey, muunPublicKey)
	case addressV4:
		return CreateAddressV4(userPublicKey, muunPublicKey)
	case addressSubmarineSwapV1:
		return nil, errors.Errorf("can't manually create a submarine swap v1 address")
	case addressSubmarineSwapV2:
		return nil, errors.Errorf("can't manually create a submarine swap v2 address")
	}

	return nil, errors.Errorf("unknown version %v", version)
}

func (a *muunAddress) Version() int {
	return int(a.version)
}

func (a *muunAddress) DerivationPath() string {
	return a.derivationPath
}

func (a *muunAddress) Address() string {
	return a.address
}

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

	bitcoinUri := buildUriFromString(rawInput)

	components, err := url.Parse(bitcoinUri)
	if err != nil {
		return nil, err
	}

	if components.Scheme != "bitcoin" {
		return nil, errors.New("Invalid scheme")
	}

	base58Address := components.Opaque

	// When URIs are bitcoin:// the address comes in host
	// this happens in iOS that mostly ignores bitcoin: format
	if len(base58Address) == 0 {
		base58Address = components.Host
	}

	queryValues, err := url.ParseQuery(components.RawQuery)
	if err != nil {
		return nil, errors.Wrapf(err, "Couldnt parse query")
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
		if len(base58Address) > 0 {
			return &MuunPaymentURI{
				Address:  base58Address,
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
	validatedBase58Address, err := btcutil.DecodeAddress(base58Address, network.network)
	if err != nil {
		return nil, err
	}

	if !validatedBase58Address.IsForNet(network.network) {
		return nil, errors.Errorf("Network mismatch")
	}

	return &MuunPaymentURI{
		Address: validatedBase58Address.String(),
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
		return nil, errors.Wrapf(err, "Failed to create request to: %s", url)
	}

	req.Header.Set("Accept", "application/bitcoin-paymentrequest")

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return nil, errors.Wrapf(err, "Failed to make request to: %s", url)
	}
	defer resp.Body.Close()

	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return nil, errors.Wrapf(err, "Failed to read body response")
	}

	payReq := &PaymentRequest{}
	err = proto.Unmarshal(body, payReq)
	if err != nil {
		return nil, errors.Wrapf(err, "Failed to Unmarshall paymentRequest")
	}

	payDetails := &PaymentDetails{}

	err = proto.Unmarshal(payReq.SerializedPaymentDetails, payDetails)
	if err != nil {
		return nil, errors.Wrapf(err, "Failed to Unmarshall paymentDetails")
	}

	if len(payDetails.Outputs) == 0 {
		return nil, errors.New("No outputs provided")
	}

	address, err := getAddressFromScript(payDetails.Outputs[0].Script, network)
	if err != nil {
		errors.Wrapf(err, "Failed to get address")
	}

	return &MuunPaymentURI{
		Address:      address,
		Message:      *payDetails.Memo,
		Amount:       strconv.FormatUint(*payDetails.Outputs[0].Amount, 10),
		BIP70Url:     url,
		CreationTime: strconv.FormatUint(*payDetails.Time, 10),
		ExpiresTime:  strconv.FormatUint(*payDetails.Expires, 10),
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

func buildUriFromString(rawInput string) string {
	newUri := rawInput

	newUri = strings.Replace(newUri, muunScheme, bitcoinScheme, 1)

	if !strings.Contains(newUri, bitcoinScheme) {
		newUri = bitcoinScheme + rawInput
	}

	return newUri
}
