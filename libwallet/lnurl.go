package libwallet

import (
	"github.com/lightningnetwork/lnd/lnwire"
	"github.com/muun/libwallet/lnurl"
)

type LNURLEvent struct {
	Code     int
	Message  string
	Metadata *LNURLEventMetadata
}

type LNURLEventMetadata struct {
	Host      string
	Invoice   string
}

const (
	LNURLErrDecode              = lnurl.ErrDecode
	LNURLErrUnsafeURL           = lnurl.ErrUnsafeURL
	LNURLErrUnreachable         = lnurl.ErrUnreachable
	LNURLErrInvalidResponse     = lnurl.ErrInvalidResponse
	LNURLErrResponse            = lnurl.ErrResponse
	LNURLErrUnknown             = lnurl.ErrUnknown
	LNURLErrWrongTag            = lnurl.ErrWrongTag
	LNURLErrNoAvailableBalance  = lnurl.ErrNoAvailableBalance
	LNURLErrRequestExpired      = lnurl.ErrRequestExpired
	LNURLErrNoRoute             = lnurl.ErrNoRoute
	LNURLErrTorNotSupported     = lnurl.ErrTorNotSupported
	LNURLErrAlreadyUsed         = lnurl.ErrAlreadyUsed
	LNURLErrForbidden           = lnurl.ErrForbidden
	LNURLErrCountryNotSupported = lnurl.ErrCountryNotSupported
	LNURLStatusContacting       = lnurl.StatusContacting
	LNURLStatusInvoiceCreated   = lnurl.StatusInvoiceCreated
	LNURLStatusReceiving        = lnurl.StatusReceiving
)

type LNURLListener interface {
	OnUpdate(e *LNURLEvent)
	OnError(e *LNURLEvent)
}

func LNURLValidate(qr string) bool {
	return lnurl.Validate(qr)
}

// Withdraw will parse an LNURL withdraw QR and begin a withdraw process.
// Caller must wait for the actual payment after this function has notified success.
func LNURLWithdraw(invoiceBuilder *InvoiceBuilder, qr string, listener LNURLListener) {
	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
		metadata := &OperationMetadata{
			LnurlSender: host,
		}

		return invoiceBuilder.AmountMSat(int64(amt)).
			Description(desc).
			Metadata(metadata).
			Build()
	}

	allowUnsafe := invoiceBuilder.net != Mainnet()

	go lnurl.Withdraw(qr, createInvoiceFunc, allowUnsafe, func(e *lnurl.Event) {
		event := &LNURLEvent{
			Code:    e.Code,
			Message: e.Message,
			Metadata: &LNURLEventMetadata{
				Host:      e.Metadata.Host,
				Invoice:   e.Metadata.Invoice,
			},
		}
		if event.Code < 100 {
			listener.OnError(event)
		} else {
			listener.OnUpdate(event)
		}
	})
}
