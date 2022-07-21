package lnurl

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/fiatjaf/go-lnurl"
	"github.com/lightningnetwork/lnd/lnwire"
)

const (
	StatusOK    = "OK"
	StatusError = "ERROR"
)

type Response struct {
	Status string `json:"status,omitempty"`
	Reason string `json:"reason,omitempty"`
}

// stringOrNumber is used to parse either a string or a number in a JSON object
type stringOrNumber float64

func (x *stringOrNumber) UnmarshalJSON(b []byte) error {
	var v stringOrNumber
	var f float64
	err := json.Unmarshal(b, &f)
	if err != nil {
		var s string
		ferr := json.Unmarshal(b, &s)
		if ferr != nil {
			return err
		}
		f, ferr = strconv.ParseFloat(s, 64)
		if ferr != nil {
			return err
		}
	}
	v = stringOrNumber(f)
	*x = v
	return nil
}

type WithdrawResponse struct {
	Response
	Tag                string         `json:"tag"`
	K1                 string         `json:"k1"`
	Callback           string         `json:"callback"`
	MaxWithdrawable    stringOrNumber `json:"maxWithdrawable"`
	MinWithdrawable    stringOrNumber `json:"minWithdrawable"`
	DefaultDescription string         `json:"defaultDescription"`
}

// After adding new codes here, remember to export them in the root libwallet
// module so that the apps can consume them.
const (
	ErrNone                int = 0
	ErrDecode              int = 1
	ErrUnsafeURL           int = 2
	ErrUnreachable         int = 3
	ErrInvalidResponse     int = 4
	ErrResponse            int = 5
	ErrUnknown             int = 6
	ErrWrongTag            int = 7
	ErrNoAvailableBalance  int = 8
	ErrRequestExpired      int = 9
	ErrNoRoute             int = 10
	ErrTorNotSupported     int = 11
	ErrAlreadyUsed         int = 12
	ErrForbidden           int = 13
	ErrCountryNotSupported int = 14 // By LNURL Service Provider

	StatusContacting     int = 100
	StatusInvoiceCreated int = 101
	StatusReceiving      int = 102
)

const zebedeeHostConst = "api.zebedee.io"

// This should definitely be a const but to simplify testing we treat it as a "conf var"
var zebedeeHost = zebedeeHostConst

type Event struct {
	Code     int
	Message  string
	Metadata EventMetadata
}

type EventMetadata struct {
	Host    string
	Invoice string
}

var httpClient = http.Client{Timeout: 15 * time.Second}

type CreateInvoiceFunction func(amt lnwire.MilliSatoshi, desc string, host string) (string, error)

func Validate(qr string) bool {
	_, err := decode(qr)
	return err == nil
}

// Withdraw will parse an LNURL withdraw QR and begin a withdraw process.
// Caller must wait for the actual payment after this function has notified success.
func Withdraw(qr string, createInvoiceFunc CreateInvoiceFunction, allowUnsafe bool, notify func(e *Event)) {
	notifier := notifier{notify: notify}

	// decode the qr
	qrUrl, err := decode(qr)
	if err != nil {
		notifier.Error(ErrDecode, err)
		return
	}
	if strings.HasSuffix(qrUrl.Host, ".onion") {
		notifier.Errorf(ErrTorNotSupported, "Tor onion links are not supported")
		return
	}
	tag := qrUrl.Query().Get("tag")
	if tag != "" && !isWithdrawRequest(tag) {
		notifier.Errorf(ErrWrongTag, "QR is not a LNURL withdraw request")
		return
	}
	if !allowUnsafe && qrUrl.Scheme != "https" {
		notifier.Errorf(ErrUnsafeURL, "URL from QR is not secure")
		return
	}
	host := qrUrl.Hostname()
	notifier.SetHost(host)

	// update contacting
	notifier.Status(StatusContacting)

	// start withdraw with service
	resp, err := httpClient.Get(qrUrl.String())
	if err != nil {
		notifier.Error(ErrUnreachable, err)
		return
	}
	defer resp.Body.Close()

	if code, reason := validateHttpResponse(resp); code != ErrNone {
		notifier.Errorf(code, reason)
		return
	}

	// parse response
	var wr WithdrawResponse
	err = json.NewDecoder(resp.Body).Decode(&wr)
	if err != nil {
		notifier.Errorf(ErrInvalidResponse, "failed to parse response: %v", err)
		return
	}
	if code, reason := wr.Validate(); code != ErrNone {
		notifier.Errorf(code, reason)
		return
	}

	callbackURL, err := url.Parse(wr.Callback)
	if err != nil {
		notifier.Errorf(ErrInvalidResponse, "invalid callback URL: %v", err)
		return
	}
	if !allowUnsafe && callbackURL.Scheme != "https" {
		notifier.Errorf(ErrUnsafeURL, "callback URL is not secure")
		return
	}
	if callbackURL.Host != qrUrl.Host {
		notifier.Errorf(ErrInvalidResponse, "callback URL does not match QR host")
		return
	}

	// generate invoice
	amount := lnwire.MilliSatoshi(int64(wr.MaxWithdrawable))
	invoice, err := createInvoiceFunc(amount, wr.DefaultDescription, host)
	if err != nil {
		notifier.Error(ErrUnknown, err)
		return
	}
	notifier.SetInvoice(invoice)
	notifier.Status(StatusInvoiceCreated)

	// Mutate the query params so we keep those the original URL had
	callbackQuery := callbackURL.Query()
	callbackQuery.Add("k1", wr.K1)
	callbackQuery.Add("pr", invoice)
	callbackURL.RawQuery = callbackQuery.Encode()

	// Confirm withdraw with service
	// Use an httpClient with a higher timeout for reliability with slow LNURL services
	withdrawClient := http.Client{Timeout: 3 * time.Minute}
	fresp, err := withdrawClient.Get(callbackURL.String())
	if err != nil {
		notifier.Errorf(ErrUnreachable, "failed to get response from callback URL: %v", err)
		return
	}
	defer fresp.Body.Close()

	if code, reason := validateHttpResponse(fresp); code != ErrNone {
		notifier.Errorf(code, reason)
		return
	}

	// parse response
	var fr Response
	err = json.NewDecoder(fresp.Body).Decode(&fr)
	if err != nil {
		notifier.Errorf(ErrInvalidResponse, "failed to parse response: %v", err)
		return
	}

	if code, reason := fr.Validate(); code != ErrNone {
		notifier.Errorf(code, reason)
		return
	}

	notifier.Status(StatusReceiving)
}

func validateHttpResponse(resp *http.Response) (int, string) {

	if resp.StatusCode >= 400 {
		// try to obtain response body
		if bytesBody, err := ioutil.ReadAll(resp.Body); err == nil {
			code := ErrInvalidResponse
			if resp.StatusCode == 403 {
				if strings.Contains(resp.Request.URL.Host, zebedeeHost) {
					code = ErrCountryNotSupported
				} else {
					code = ErrForbidden
				}
			}

			return code, fmt.Sprintf("unexpected status code in response: %v, body: %s", resp.StatusCode, string(bytesBody))
		}
	}

	if resp.StatusCode >= 300 {
		return ErrInvalidResponse, fmt.Sprintf("unexpected status code in response: %v", resp.StatusCode)
	}

	return ErrNone, ""
}

func (wr *WithdrawResponse) Validate() (int, string) {

	if wr.Status == StatusError {
		return mapReasonToErrorCode(wr.Reason), wr.Reason
	}

	if !isWithdrawRequest(wr.Tag) {
		return ErrWrongTag, "QR is not a LNURL withdraw request"
	}

	if wr.MaxWithdrawable <= 0 {
		return ErrNoAvailableBalance, "no available balance to withdraw"
	}

	return ErrNone, ""
}

func (fr *Response) Validate() (int, string) {

	if fr.Status == StatusError {
		return mapReasonToErrorCode(fr.Reason), fr.Reason
	}

	return ErrNone, ""
}

// reasons maps from parts of responses to the error code. The string can be in
// any part of the response, and has to be lowercased to simplify matching.
// Try to also document the original error string above the pattern.
var reasons = map[string]int{
	"route":   ErrNoRoute,
	"expired": ErrRequestExpired,
	// This Withdrawal Request is already being processed by another wallet. (zebedee)
	"already being processed": ErrAlreadyUsed,
	// This Withdrawal Request can only be processed once (zebedee)
	"request can only be processed once": ErrAlreadyUsed,
	// Withdraw is spent (lnbits)
	"withdraw is spent": ErrAlreadyUsed,
	// Withdraw link is empty (lnbits)
	"withdraw link is empty": ErrAlreadyUsed,
	// This LNURL has already been used (thndr.io)
	"has already been used": ErrAlreadyUsed,
}

func mapReasonToErrorCode(reason string) int {

	reason = strings.ToLower(reason)

	for pattern, code := range reasons {
		if strings.Contains(reason, pattern) {
			return code
		}
	}

	// Simply an invalid response for some unknown reason
	return ErrResponse
}

func decode(qr string) (*url.URL, error) {
	// handle fallback scheme
	var toParse string
	if strings.HasPrefix(qr, "http://") || strings.HasPrefix(qr, "https://") {
		u, err := url.Parse(qr)
		if err != nil {
			return nil, err
		}
		toParse = u.Query().Get("lightning")
	} else {
		// Remove muun: prefix, including the :// version for iOS
		qr = strings.Replace(qr, "muun://", "", 1)
		qr = strings.Replace(qr, "muun:", "", 1)

		// Use a consistent prefix
		if !strings.HasPrefix(strings.ToLower(qr), "lightning:") {
			qr = "lightning:" + qr
		}

		uri, err := url.Parse(qr)
		if err != nil {
			return nil, err
		}

		if len(uri.Opaque) > 0 {
			// This catches lightning:LNURL
			toParse = uri.Opaque
		} else {
			// And this catches lightning://LNURL which is needed for iOS
			toParse = uri.Host
		}
	}
	u, err := lnurl.LNURLDecode(toParse)
	if err != nil {
		return nil, err
	}
	return url.Parse(u)
}

// We allow "withdraw" as a valid LNURL withdraw tag because, even though not in spec, there are
// implementations in the wild using it and accepting it as valid (e.g azte.co)
func isWithdrawRequest(tag string) bool {
	return tag == "withdrawRequest" || tag == "withdraw"
}

type notifier struct {
	metadata EventMetadata
	notify   func(*Event)
}

func (n *notifier) SetHost(host string) {
	n.metadata.Host = host
}

func (n *notifier) SetInvoice(invoice string) {
	n.metadata.Invoice = invoice
}

func (n *notifier) Status(status int) {
	n.notify(&Event{Code: status, Metadata: n.metadata})
}

func (n *notifier) Error(status int, err error) {
	n.notify(&Event{Code: status, Message: err.Error(), Metadata: n.metadata})
}

func (n *notifier) Errorf(status int, format string, a ...interface{}) {
	msg := fmt.Sprintf(format, a...)
	n.notify(&Event{Code: status, Message: msg, Metadata: n.metadata})
}
