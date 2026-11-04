package lnurl

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/url"
	"slices"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/fiatjaf/go-lnurl"
	"github.com/go-errors/errors"
	"github.com/lightningnetwork/lnd/lnwire"

	"github.com/muun/libwallet/platform/tlsdistrust"
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

// Format prints LogValue for every verb.
// Format and LogValue take value receivers, unlike the other methods, so values print
// redacted too, not only pointers.
func (wr WithdrawResponse) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, wr.LogValue().String())
}

// LogValue returns the response without k1 and the callback.
func (wr WithdrawResponse) LogValue() slog.Value {
	return slog.GroupValue(
		slog.String("status", wr.Status),
		slog.String("reason", wr.Reason),
		slog.String("tag", wr.Tag),
		slog.Float64("max_withdrawable", float64(wr.MaxWithdrawable)),
		slog.Float64("min_withdrawable", float64(wr.MinWithdrawable)),
		slog.String("default_description", wr.DefaultDescription),
	)
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

// Extracted to external vars make Withdraw "more testable"
var httpClient = http.Client{Timeout: 15 * time.Second, Transport: tlsdistrust.Transport()}
var withdrawClient WithdrawClient = &http.Client{
	Timeout:   3 * time.Minute,
	Transport: tlsdistrust.Transport(),
}

type WithdrawClient interface {
	Get(url string) (resp *http.Response, err error)
}

type CreateInvoiceFunction func(amt lnwire.MilliSatoshi, desc string, host string) (string, error)

func Validate(qr string) bool {
	_, err := decode(qr)
	return err == nil
}

// Withdraw will parse an LNURL withdraw QR and begin a withdraw process.
// Caller must wait for the actual payment after this function has notified success.
func Withdraw(
	qr string,
	createInvoiceFunc CreateInvoiceFunction,
	allowUnsafe bool,
	notify func(e *Event),
) {
	notifier := notifier{notify: notify}

	// decode the qr
	qrURL, err := decode(qr)
	if err != nil {
		notifier.Error(ErrDecode, "QR decode", err)
		return
	}
	if strings.HasSuffix(qrURL.Host, ".onion") {
		notifier.Errorf(ErrTorNotSupported, "Tor onion links are not supported")
		return
	}
	tag := qrURL.Query().Get("tag")
	if tag != "" && !isWithdrawRequest(tag) {
		notifier.Errorf(ErrWrongTag, "QR is not a LNURL withdraw request")
		return
	}
	if !allowUnsafe && qrURL.Scheme != "https" {
		notifier.Errorf(ErrUnsafeURL, "URL from QR is not secure")
		return
	}
	host := qrURL.Hostname()
	notifier.SetHost(host)

	// update contacting
	notifier.Status(StatusContacting)

	// start withdraw with service
	req, err := http.NewRequestWithContext(context.Background(), "GET", qrURL.String(), nil)
	if err != nil {
		notifier.Error(ErrUnknown, "Voucher request to "+host, err)
		return
	}
	resp, err := httpClient.Do(req)
	if err != nil {
		notifier.Error(ErrUnreachable, "Voucher request to "+host, err)
		return
	}
	defer func() { _ = resp.Body.Close() }()

	if code, reason := validateHTTPResponse(resp); code != ErrNone {
		notifier.Errorf(code, "%s", reason)
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
		notifier.Errorf(code, "%s", reason)
		return
	}

	callbackURL, err := url.Parse(wr.Callback)
	if err != nil {
		notifier.Error(
			ErrInvalidResponse, "Withdrawal response from "+host+": invalid callback URL", err,
		)
		return
	}
	if !allowUnsafe && callbackURL.Scheme != "https" {
		notifier.Errorf(ErrUnsafeURL, "callback URL is not secure")
		return
	}
	// We don't check for "callbackURL.Host == qrURL.Host" since for withdraw it does not add
	// any security.
	// Note: for other lnurl action it will definitely be a requirement.

	// generate invoice
	amount := lnwire.MilliSatoshi(int64(wr.MaxWithdrawable))
	invoice, err := createInvoiceFunc(amount, wr.DefaultDescription, host)
	if err != nil {
		notifier.Error(ErrUnknown, "Invoice creation", err)
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
	fresp, err := withdrawClient.Get(callbackURL.String())
	if err != nil {
		notifier.Error(ErrUnreachable, "Callback request to "+callbackURL.Hostname(), err)
		return
	}
	defer func() { _ = fresp.Body.Close() }()

	if code, reason := validateHTTPResponse(fresp); code != ErrNone {
		notifier.Errorf(code, "%s", reason)
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
		notifier.Errorf(code, "%s", reason)
		return
	}

	notifier.Status(StatusReceiving)
}

func validateHTTPResponse(
	resp *http.Response,
) (int, string) {

	if resp.StatusCode >= 400 {
		// try to obtain response body
		if bytesBody, err := io.ReadAll(resp.Body); err == nil {
			code := ErrInvalidResponse
			if resp.StatusCode == 403 {
				if strings.Contains(resp.Request.URL.Host, zebedeeHost) {
					code = ErrCountryNotSupported
				} else {
					code = ErrForbidden
				}
			}

			return code, fmt.Sprintf(
				"unexpected status code in response: %v, body: %s",
				resp.StatusCode,
				string(bytesBody),
			)
		}
	}

	if resp.StatusCode >= 300 {
		return ErrInvalidResponse, fmt.Sprintf(
			"unexpected status code in response: %v",
			resp.StatusCode,
		)
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
	// Mainly to emulate ErrUnreachable error in CI, UI tests
	"is unresponsive": ErrUnreachable,
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

//goland:noinspection HttpUrlsUsage
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
		// go-lnurl's errors can quote the QR, which may carry the withdraw token.
		return nil, errors.New("invalid LNURL")
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

// Error notifies status with "<step>: <reason>", where reason is err's message. If err holds a
// *url.Error, whose URL can carry the LNURL voucher, k1 or the invoice, reason is only a known
// safe cause, such as the invalid escape or the refused connection, or a fixed description.
func (n *notifier) Error(status int, step string, err error) {
	reason := err.Error()
	var urlErr *url.Error
	if errors.As(err, &urlErr) {
		reason = "request failed"
		var nestedURLErr *url.Error
		var escapeErr url.EscapeError
		var invalidHostErr url.InvalidHostError
		var dnsErr *net.DNSError
		var errno syscall.Errno
		var certErr *tls.CertificateVerificationError
		switch {
		case errors.As(urlErr.Err, &nestedURLErr):
			// A nested *url.Error carries its own URL, so nothing behind it is kept.
		case urlErr.Op == "parse":
			// url.Parse messages that quote nothing from the URL.
			fixedParseReasons := []string{
				"missing protocol scheme",
				"net/url: invalid control character in URL",
				"empty url",
				"invalid URI for request",
				"first path segment in URL cannot contain colon",
				"net/url: invalid userinfo",
				"missing ']' in host",
				"invalid IP-literal",
			}
			reason = "invalid URL"
			parseReason := urlErr.Err.Error()
			switch {
			case errors.As(urlErr.Err, &escapeErr):
				reason = escapeErr.Error()
			case errors.As(urlErr.Err, &invalidHostErr):
				reason = invalidHostErr.Error()
			case slices.Contains(fixedParseReasons, parseReason):
				reason = parseReason
			case strings.HasPrefix(parseReason, "invalid port "):
				reason = "invalid port"
			case strings.HasPrefix(parseReason, "invalid host: "):
				reason = "invalid host"
			}
		case urlErr.Timeout():
			reason = "timed out"
		case errors.As(urlErr.Err, &dnsErr):
			reason = dnsErr.Err
		case errors.As(urlErr.Err, &errno):
			reason = errno.Error()
		case errors.As(urlErr.Err, &certErr):
			reason = "invalid TLS certificate"
		case strings.HasPrefix(urlErr.Err.Error(), "failed to parse Location header"):
			reason = "service returned an invalid redirect URL"
		}
	}
	n.notify(&Event{Code: status, Message: step + ": " + reason, Metadata: n.metadata})
}

func (n *notifier) Errorf(
	status int,
	format string,
	a ...any,
) {
	msg := fmt.Sprintf(format, a...)
	n.notify(&Event{Code: status, Message: msg, Metadata: n.metadata})
}
