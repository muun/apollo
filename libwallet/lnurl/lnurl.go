package lnurl

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
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

type WithdrawResponse struct {
	Response
	Tag                string  `json:"tag"`
	K1                 string  `json:"k1"`
	Callback           string  `json:"callback"`
	MaxWithdrawable    float64 `json:"maxWithdrawable"`
	MinWithdrawable    float64 `json:"minWithdrawable"`
	DefaultDescription string  `json:"defaultDescription"`
}

// After adding new codes here, remember to export them in the root libwallet
// module so that the apps can consume them.
const (
	ErrDecode             int = 1
	ErrUnsafeURL          int = 2
	ErrUnreachable        int = 3
	ErrInvalidResponse    int = 4
	ErrResponse           int = 5
	ErrUnknown            int = 6
	ErrWrongTag           int = 7
	ErrNoAvailableBalance int = 8
	ErrRequestExpired     int = 9
	ErrNoRoute            int = 10
	ErrTorNotSupported    int = 11
	StatusContacting      int = 100
	StatusInvoiceCreated  int = 101
	StatusReceiving       int = 102
)

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
	if tag != "" && tag != "withdrawRequest" {
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
	if resp.StatusCode >= 300 {
		notifier.Errorf(ErrInvalidResponse, "unexpected status code in response: %v", err)
		return
	}
	// parse response
	var wr WithdrawResponse
	err = json.NewDecoder(resp.Body).Decode(&wr)
	if err != nil {
		notifier.Errorf(ErrInvalidResponse, "failed to parse response: %v", err)
		return
	}
	if wr.Status == StatusError {
		if strings.Contains(strings.ToLower(wr.Reason), "expired") {
			notifier.Errorf(ErrRequestExpired, wr.Reason)
		} else {
			notifier.Errorf(ErrResponse, wr.Reason)
		}
		return
	}
	if wr.Tag != "withdrawRequest" {
		notifier.Errorf(ErrWrongTag, "QR is not a LNURL withdraw request")
		return
	}
	if wr.MaxWithdrawable <= 0 {
		notifier.Errorf(ErrNoAvailableBalance, "no available balance to withdraw")
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
	query := callbackURL.Query()
	query.Add("k1", wr.K1)
	query.Add("pr", invoice)

	callbackURL.RawQuery = query.Encode()
	// Confirm withdraw with service
	// Use an httpClient with a higher timeout for reliability with slow LNURL services
	withdrawClient := http.Client{Timeout: 3 * time.Minute}
	resp, err = withdrawClient.Get(callbackURL.String())
	if err != nil {
		notifier.Errorf(ErrUnreachable, "failed to get response from callback URL: %v", err)
		return
	}
	if resp.StatusCode >= 300 {
		notifier.Errorf(ErrInvalidResponse, "unexpected status code in response: %v", err)
		return
	}
	// parse response
	var fr Response
	err = json.NewDecoder(resp.Body).Decode(&fr)
	if err != nil {
		notifier.Errorf(ErrInvalidResponse, "failed to parse response: %v", err)
		return
	}
	if fr.Status == StatusError {
		if strings.Contains(strings.ToLower(fr.Reason), "route") {
			notifier.Errorf(ErrNoRoute, fr.Reason)
		} else {
			notifier.Errorf(ErrResponse, fr.Reason)
		}
		return
	}
	notifier.Status(StatusReceiving)
}

func decode(qr string) (*url.URL, error) {
	// handle fallback scheme
	if strings.HasPrefix(qr, "http://") || strings.HasPrefix(qr, "https://") {
		u, err := url.Parse(qr)
		if err != nil {
			return nil, err
		}
		qr = u.Query().Get("lightning")
	} else {
		// remove lightning prefix
		if strings.HasPrefix(strings.ToLower(qr), "lightning:") {
			qr = qr[len("lightning:"):]
		}
	}
	u, err := lnurl.LNURLDecode(qr)
	if err != nil {
		return nil, err
	}
	return url.Parse(string(u))
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
