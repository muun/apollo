package libwallet

import (
	"encoding/json"
	"fmt"
	goLnurl "github.com/fiatjaf/go-lnurl"
	"github.com/muun/libwallet/lnurl"
	"net/http"
	"net/http/httptest"
	"testing"
)

func encode(url string) (string, error) {
	return goLnurl.LNURLEncode(url)
}

type testLNURLListener struct {
	expectedSuccess bool
	status          int
	expectedErr     int
	ch              chan string
}

func (listener *testLNURLListener) OnUpdate(event *LNURLEvent) {
	if listener.expectedSuccess {

		switch event.Code {
		case lnurl.StatusContacting:
			if listener.status != 0 {
				listener.ch <- fmt.Sprintf("expected withdraw status to be %v, got: %v", lnurl.StatusContacting, event.Code)
			} else {
				listener.status = 1
			}

		case lnurl.StatusInvoiceCreated:
			if listener.status != 1 {
				listener.ch <- fmt.Sprintf("expected withdraw status to be %v, got: %v", lnurl.StatusInvoiceCreated, event.Code)
			} else {
				listener.status = 2
			}

		case lnurl.StatusReceiving:
			if listener.status != 2 {
				listener.ch <- fmt.Sprintf("expected withdraw status to be %v, got: %v", lnurl.StatusReceiving, event.Code)
			} else {
				listener.status = 3
				listener.ch <- "DONE"
			}
		}

	} else {
		listener.ch <- fmt.Sprintf("expected withdraw to error, got: %v", event)
	}
}

func (listener *testLNURLListener) OnError(event *LNURLEvent) {
	if listener.expectedSuccess {
		listener.ch <- fmt.Sprintf("expected withdraw to succeed, got: %v", event)
	} else {
		listener.ch <- "DONE"
	}
}

func TestLNURLWithdrawAllowUnsafe(t *testing.T) {
	setup()

	network := Regtest()

	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&lnurl.WithdrawResponse{
			K1:                 "foobar",
			Callback:           "http://" + r.Host + "/withdraw/complete",
			MaxWithdrawable:    1_000_000,
			DefaultDescription: "Withdraw from Lapp",
			Tag:                "withdrawRequest",
		})
	})
	mux.HandleFunc("/withdraw/complete", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&lnurl.Response{
			Status: lnurl.StatusOK,
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	userKey, _ := NewHDPrivateKey(randomBytes(32), network)
	userKey.Path = "m/schema:1'/recovery:1'"

	invoiceBuilder := InvoiceBuilder{}
	invoiceBuilder.Network(network)
	invoiceBuilder.UserKey(userKey)
	invoiceBuilder.AddRouteHints(&RouteHints{
		Pubkey:                    "03c48d1ff96fa32e2776f71bba02102ffc2a1b91e2136586418607d32e762869fd",
		FeeBaseMsat:               1000,
		FeeProportionalMillionths: 1000,
		CltvExpiryDelta:           8,
	})

	listener := testLNURLListener{ch: make(chan string)}
	listener.expectedSuccess = true

	LNURLWithdraw(&invoiceBuilder, qr, &listener)

	result := <-listener.ch
	if result != "DONE" {
		t.Fatalf("%s", result)
	}

	invoiceBuilder.Network(Mainnet())

	listener = testLNURLListener{ch: make(chan string)}
	listener.expectedSuccess = false
	listener.expectedErr = lnurl.ErrUnsafeURL

	LNURLWithdraw(&invoiceBuilder, qr, &listener)

	result = <-listener.ch
	if result != "DONE" {
		t.Fatalf("%s", result)
	}
}
