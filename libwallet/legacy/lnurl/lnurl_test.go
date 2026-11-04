package lnurl

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/fiatjaf/go-lnurl"
	"github.com/lightningnetwork/lnd/lnwire"
)

type FakeWithdrawClient struct {
	WithdrawClient WithdrawClient
	ServerURL      string
}

func (fwc *FakeWithdrawClient) Get(urlString string) (*http.Response, error) {

	URL := parseURL(urlString)

	if URL.Host == "test.com" {
		host := parseURL(fwc.ServerURL).Host
		return fwc.WithdrawClient.Get(strings.Replace(urlString, "test.com", host, 1))
	} else {
		return fwc.WithdrawClient.Get(urlString)
	}
}

func parseURL(
	urlString string,
) *url.URL {
	URL, err := url.Parse(urlString)

	if err != nil {
		panic(err)
	}

	return URL
}

//goland:noinspection GoUnhandledErrorResult
func TestWithdraw(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		//goland:noinspection HttpUrlsUsage
		json.NewEncoder(w).Encode(&WithdrawResponse{
			K1:                 "foobar",
			Callback:           "http://" + r.Host + "/withdraw/complete",
			MaxWithdrawable:    1_000_000,
			DefaultDescription: "Withdraw from Lapp",
			Tag:                "withdrawRequest",
		})
	})
	mux.HandleFunc(
		"/withdraw/complete",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&Response{
				Status: StatusOK,
			})
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
		if amt != 1_000_000 {
			t.Fatalf("unexpected invoice amount: %v", amt)
		}
		if desc != "Withdraw from Lapp" {
			t.Fatalf("unexpected invoice description: %v", desc)
		}
		if host != "127.0.0.1" {
			t.Fatalf("unexpected host: %v", host)
		}
		return "12345", nil
	}

	var err string
	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 {
			err = e.Message
		}
	})
	if err != "" {
		t.Fatalf("expected withdraw to succeed, got: %v", err)
	}
}

//goland:noinspection GoUnhandledErrorResult,HttpUrlsUsage
func TestWithdrawWithCompatibilityTag(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&WithdrawResponse{
			K1:                 "foobar",
			Callback:           "http://" + r.Host + "/withdraw/complete",
			MaxWithdrawable:    1_000_000,
			DefaultDescription: "Withdraw from Lapp",
			Tag:                "withdraw",
		})
	})
	mux.HandleFunc(
		"/withdraw/complete",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&Response{
				Status: StatusOK,
			})
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
		if amt != 1_000_000 {
			t.Fatalf("unexpected invoice amount: %v", amt)
		}
		if desc != "Withdraw from Lapp" {
			t.Fatalf("unexpected invoice description: %v", desc)
		}
		if host != "127.0.0.1" {
			t.Fatalf("unexpected host: %v", host)
		}
		return "12345", nil
	}

	var err string
	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 {
			err = e.Message
		}
	})
	if err != "" {
		t.Fatalf("expected withdraw to succeed, got: %v", err)
	}
}

//goland:noinspection GoUnhandledErrorResult,HttpUrlsUsage
func TestWithdrawWithDifferentDomainHosts(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc(
		"/withdraw/",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&WithdrawResponse{
				K1: "foobar",
				// Callback url has a different host than localhost
				Callback:           "http://test.com/withdraw/complete",
				MaxWithdrawable:    1_000_000,
				DefaultDescription: "Withdraw from Lapp",
				Tag:                "withdraw",
			})
		},
	)

	mux.HandleFunc(
		"/withdraw/complete",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&Response{
				Status: StatusOK,
			})
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
		if amt != 1_000_000 {
			t.Fatalf("unexpected invoice amount: %v", amt)
		}
		if desc != "Withdraw from Lapp" {
			t.Fatalf("unexpected invoice description: %v", desc)
		}
		if host != "127.0.0.1" {
			t.Fatalf("unexpected host: %v", host)
		}
		return "12345", nil
	}

	// We "inject" FakeWithdrawClient to simulate test.com responds correctly (by redirecting to
	// localhost)
	originalClient := withdrawClient
	withdrawClient = &FakeWithdrawClient{originalClient, server.URL}
	t.Cleanup(func() {
		withdrawClient = originalClient // after test reset to its original value
	})

	var err string
	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 {
			err = e.Message
		}
	})
	if err != "" {
		t.Fatalf("expected withdraw to succeed, got: %v", err)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestDecodeError(t *testing.T) {
	qr := "lightning:abcde"

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrDecode {
			t.Fatalf("unexpected error code: %v", e.Code)
		}
		if e.Code == StatusContacting {
			t.Fatal("should not contact server")
		}
	})
}

//goland:noinspection GoUnhandledErrorResult
func TestWrongTagError(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc(
		"/channelRequest",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&WithdrawResponse{
				Tag: "channelRequest",
			})
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/channelRequest", server.URL))

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrWrongTag {
			t.Fatalf("unexpected error code: %v", e.Code)
		}
		if e.Code == StatusInvoiceCreated {
			t.Fatal("should not create invoice")
		}
	})
}

//goland:noinspection GoUnhandledErrorResult
func TestUnreachableError(t *testing.T) {
	originalTimeout := httpClient.Timeout
	httpClient.Timeout = 1 * time.Second
	defer func() {
		httpClient.Timeout = originalTimeout
	}()

	// LNURL QR pointing to a non-responding domain
	qr := "LIGHTNING:LNURL1DP68GURN8GHJ7ARGD9EJUER0D4SKJM3WV3HK2UEWDEHHGTN90P5HXAPWV4UXZMTSD3JJUCM0D5LHXETRWFJHG0F3XGENGDGQ8EH52" //nolint:lll

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrUnreachable {
			t.Fatalf("unexpected error code: %v", e.Code)
		}
		if e.Code == StatusInvoiceCreated {
			t.Fatal("should not create invoice")
		}
	})

}

//goland:noinspection GoUnhandledErrorResult,HttpUrlsUsage
func TestErrorsLeaveOutURLs(t *testing.T) {
	// Nothing listens on port 1, so requests to it fail right away.
	const unreachable = "http://127.0.0.1:1"

	var server *httptest.Server
	withdrawWithCallback := func(callback func() string) http.HandlerFunc {
		return func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&WithdrawResponse{
				K1:                 "secret-k1",
				Callback:           callback(),
				MaxWithdrawable:    1_000_000,
				DefaultDescription: "Withdraw from Lapp",
				Tag:                "withdrawRequest",
			})
		}
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", withdrawWithCallback(func() string {
		return unreachable + "/withdraw/complete"
	}))
	mux.HandleFunc("/withdraw-redirected-callback/", withdrawWithCallback(func() string {
		return server.URL + "/redirect/"
	}))
	mux.HandleFunc("/withdraw-callback-invalid-escape/", withdrawWithCallback(func() string {
		return server.URL + "/complete%zz?token=secret-callback-token"
	}))
	mux.HandleFunc("/withdraw-callback-invalid-port/", withdrawWithCallback(func() string {
		return "https://service.example:secret-callback-token/complete"
	}))
	mux.HandleFunc("/withdraw-callback-invalid-host/", withdrawWithCallback(func() string {
		return "https://[secret-callback-token]/complete"
	}))
	// Redirects to an unparsable Location that keeps the request's query.
	mux.HandleFunc("/redirect/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Location", "/redirected%zz?"+r.URL.RawQuery)
		w.WriteHeader(http.StatusFound)
	})
	server = httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })
	// Its certificate isn't trusted by the LNURL HTTP client.
	tlsServer := httptest.NewTLSServer(mux)
	t.Cleanup(func() { tlsServer.Close() })

	encodeURL := func(rawURL string) string {
		qr, _ := encode(rawURL)
		return qr
	}

	tests := []struct {
		name string
		qr   string
		code int
		want string
	}{
		{
			name: "QR with an invalid escape",
			qr:   "https://lnurl.example/withdraw%zz?token=secret-voucher",
			code: ErrDecode,
			want: `QR decode: invalid URL escape "%zz"`,
		},
		{
			name: "QR with an invalid port",
			qr:   "https://lnurl.example:secret-voucher/withdraw",
			code: ErrDecode,
			want: "QR decode: invalid port",
		},
		{
			name: "QR with an unrecognized LNURL",
			qr:   "lnurlx://lnurl.example/secret-voucher",
			code: ErrDecode,
			want: "QR decode: invalid LNURL",
		},
		{
			name: "unreachable voucher request",
			qr:   encodeURL(unreachable + "/withdraw/?token=secret-voucher"),
			code: ErrUnreachable,
			want: "Voucher request to 127.0.0.1: connection refused",
		},
		{
			name: "untrusted voucher request",
			qr:   encodeURL(tlsServer.URL + "/withdraw/?token=secret-voucher"),
			code: ErrUnreachable,
			want: "Voucher request to 127.0.0.1: invalid TLS certificate",
		},
		{
			name: "voucher request redirected to an invalid URL",
			qr:   encodeURL(server.URL + "/redirect/?token=secret-voucher"),
			code: ErrUnreachable,
			want: "Voucher request to 127.0.0.1: service returned an invalid redirect URL",
		},
		{
			name: "callback URL with an invalid escape",
			qr:   encodeURL(server.URL + "/withdraw-callback-invalid-escape/"),
			code: ErrInvalidResponse,
			want: "Withdrawal response from 127.0.0.1: invalid callback URL: " +
				`invalid URL escape "%zz"`,
		},
		{
			name: "callback URL with an invalid port",
			qr:   encodeURL(server.URL + "/withdraw-callback-invalid-port/"),
			code: ErrInvalidResponse,
			want: "Withdrawal response from 127.0.0.1: invalid callback URL: invalid port",
		},
		{
			name: "callback URL with an invalid host",
			qr:   encodeURL(server.URL + "/withdraw-callback-invalid-host/"),
			code: ErrInvalidResponse,
			want: "Withdrawal response from 127.0.0.1: invalid callback URL: invalid host",
		},
		{
			name: "unreachable callback request",
			qr:   encodeURL(server.URL + "/withdraw/"),
			code: ErrUnreachable,
			want: "Callback request to 127.0.0.1: connection refused",
		},
		{
			name: "callback request redirected to an invalid URL",
			qr:   encodeURL(server.URL + "/withdraw-redirected-callback/"),
			code: ErrUnreachable,
			want: "Callback request to 127.0.0.1: service returned an invalid redirect URL",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
				return "secret-invoice", nil
			}

			var message string
			Withdraw(tt.qr, createInvoiceFunc, true, func(e *Event) {
				if e.Code == tt.code {
					message = e.Message
				}
			})

			if message != tt.want {
				t.Fatalf("expected error %d with message %q, got %q", tt.code, tt.want, message)
			}
		})
	}
}

func TestErrorKeepsOnlySafeCausesOfURLErrors(t *testing.T) {
	const secretURL = "https://lnurl.example/withdraw/?token=secret-voucher"

	tests := []struct {
		name string
		err  error
		want string
	}{
		{
			name: "nested URL error",
			err: &url.Error{
				Op:  "parse",
				URL: secretURL,
				Err: &url.Error{Op: "parse", URL: secretURL, Err: url.EscapeError("%zz")},
			},
			want: "Voucher request to lnurl.example: request failed",
		},
		{
			name: "timeout",
			err:  &url.Error{Op: "Get", URL: secretURL, Err: context.DeadlineExceeded},
			want: "Voucher request to lnurl.example: timed out",
		},
		{
			name: "unknown host",
			err: &url.Error{Op: "Get", URL: secretURL, Err: &net.OpError{
				Op:  "dial",
				Net: "tcp",
				Err: &net.DNSError{Err: "no such host", Name: "lnurl.example", IsNotFound: true},
			}},
			want: "Voucher request to lnurl.example: no such host",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var message string
			n := notifier{notify: func(e *Event) { message = e.Message }}
			n.Error(ErrUnreachable, "Voucher request to lnurl.example", tt.err)

			if message != tt.want {
				t.Fatalf("expected %q, got %q", tt.want, message)
			}
		})
	}
}

//goland:noinspection GoUnhandledErrorResult,HttpUrlsUsage
func TestServiceError(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&WithdrawResponse{
			K1:                 "foobar",
			Callback:           "http://" + r.Host + "/withdraw/complete",
			MaxWithdrawable:    1_000_000,
			DefaultDescription: "Withdraw from Lapp",
			Tag:                "withdrawRequest",
		})
	})
	mux.HandleFunc(
		"/withdraw/complete",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&Response{
				Status: StatusError,
				Reason: "something something",
			})
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
		if amt != 1_000_000 {
			t.Fatalf("unexpected invoice amount: %v", amt)
		}
		if desc != "Withdraw from Lapp" {
			t.Fatalf("unexpected invoice description: %v", desc)
		}
		if host != "127.0.0.1" {
			t.Fatalf("unexpected host: %v", host)
		}
		return "12345", nil
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrResponse {
			t.Fatalf("unexpected error code: %v", e.Code)
		}
		if e.Code == StatusReceiving {
			t.Fatal("should not reach receiving status")
		}
	})
}

//goland:noinspection GoUnhandledErrorResult
func TestInvalidResponseError(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc(
		"/withdraw/",
		func(w http.ResponseWriter, _ *http.Request) {
			w.Write([]byte("foobar"))
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrInvalidResponse {
			t.Fatalf("unexpected error code: %v", e.Code)
		}
		if e.Code == StatusInvoiceCreated {
			t.Fatal("should not reach invoice creation")
		}
	})
}

func TestUnsafeURLError(t *testing.T) {
	qr, _ := encode("http://localhost/withdraw")

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, false, func(e *Event) {
		if e.Code < 100 && e.Code != ErrUnsafeURL {
			t.Fatalf("unexpected error code: %v", e.Code)
		}
	})
}

func TestWrongTagInQR(t *testing.T) {
	// LNURL QR with a `login` tag value in its query params
	qr := "lightning:lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veax2un09e3k7mf0w5lhgct884kx7emfdcnxkvfa8qexxc35vymnxcf5xumkxvfsv4snxwph8qunzv3hxesnyv3jvv6nyv3e8yuxzvnpv4skvepnxg6rwv34xqck2c3sxcerzdpnv56r2dss2vt96" //nolint:lll

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrWrongTag {
			t.Fatalf("unexpected error code: %v", e.Code)
		}
		if e.Code == StatusContacting {
			t.Fatal("should not contact server")
		}
	})
}

func TestOnionLinkNotSupported(t *testing.T) {
	qr := "LNURL1DP68GUP69UHKVMM0VFSHYTN0DE5K7MSHXU8YD"

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrTorNotSupported {
			t.Fatalf("unexpected error code: %v", e.Code)
		}
		if e.Code == StatusContacting {
			t.Fatal("should not contact server")
		}
	})
}

//goland:noinspection GoUnhandledErrorResult
func TestExpiredCheck(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc(
		"/withdraw/",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&Response{
				Status: "ERROR",
				Reason: "something something Expired blabla",
			})
		},
	)
	mux.HandleFunc(
		"/withdraw/complete",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&Response{
				Status: StatusOK,
			})
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrRequestExpired {
			t.Fatalf("unexpected error code: %v", e.Code)
		}
		if e.Code == StatusInvoiceCreated {
			t.Fatal("should not create invoice")
		}
	})
}

//goland:noinspection GoUnhandledErrorResult,HttpUrlsUsage
func TestNoAvailableBalance(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&WithdrawResponse{
			K1:                 "foobar",
			Callback:           "http://" + r.Host + "/withdraw/complete",
			MaxWithdrawable:    0,
			MinWithdrawable:    0,
			DefaultDescription: "Withdraw from Lapp",
			Tag:                "withdrawRequest",
		})
	})
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrNoAvailableBalance {
			t.Fatalf("unexpected error code: %d", e.Code)
		}
		if e.Code == StatusInvoiceCreated {
			t.Fatalf("should not create invoice")
		}
	})
}

//goland:noinspection GoUnhandledErrorResult,HttpUrlsUsage
func TestNoRouteCheck(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&WithdrawResponse{
			K1:                 "foobar",
			Callback:           "http://" + r.Host + "/withdraw/complete",
			MaxWithdrawable:    1_000_000,
			DefaultDescription: "Withdraw from Lapp",
			Tag:                "withdrawRequest",
		})
	})
	mux.HandleFunc(
		"/withdraw/complete",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&Response{
				Status: StatusError,
				Reason: "Unable to pay LN Invoice: FAILURE_REASON_NO_ROUTE",
			})
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		return "12345", nil
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrNoRoute {
			t.Fatalf("unexpected error code: %d", e.Code)
		}
	})
}

//goland:noinspection GoUnhandledErrorResult,HttpUrlsUsage
func TestExtraQueryParams(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&WithdrawResponse{
			K1:                 "foobar",
			Callback:           "http://" + r.Host + "/withdraw/complete?foo=bar",
			MaxWithdrawable:    1_000_000,
			DefaultDescription: "Withdraw from Lapp",
			Tag:                "withdrawRequest",
		})
	})
	mux.HandleFunc("/withdraw/complete", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("foo") != "bar" {
			t.Fatalf("Expected foo=bar in query params. Got URL: %v", r.URL.String())
		}
		json.NewEncoder(w).Encode(&Response{
			Status: StatusOK,
		})
	})
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
		if amt != 1_000_000 {
			t.Fatalf("unexpected invoice amount: %v", amt)
		}
		if desc != "Withdraw from Lapp" {
			t.Fatalf("unexpected invoice description: %v", desc)
		}
		if host != "127.0.0.1" {
			t.Fatalf("unexpected host: %v", host)
		}
		return "12345", nil
	}

	var err string
	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 {
			err = e.Message
		}
	})
	if err != "" {
		t.Fatalf("expected withdraw to succeed, got: %v", err)
	}
}

//goland:noinspection GoUnhandledErrorResult,HttpUrlsUsage
func TestStringlyTypedNumberFields(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&struct {
			Response
			Tag                string `json:"tag"`
			K1                 string `json:"k1"`
			Callback           string `json:"callback"`
			MaxWithdrawable    string `json:"maxWithdrawable"`
			MinWithdrawable    string `json:"minWithdrawable"`
			DefaultDescription string `json:"defaultDescription"`
		}{
			K1:                 "foobar",
			Callback:           "http://" + r.Host + "/withdraw/complete",
			MaxWithdrawable:    "1000000",
			MinWithdrawable:    "0",
			DefaultDescription: "Withdraw from Lapp",
			Tag:                "withdrawRequest",
		})
	})
	mux.HandleFunc(
		"/withdraw/complete",
		func(w http.ResponseWriter, _ *http.Request) {
			json.NewEncoder(w).Encode(&Response{
				Status: StatusOK,
			})
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
		if amt != 1_000_000 {
			t.Fatalf("unexpected invoice amount: %v", amt)
		}
		if desc != "Withdraw from Lapp" {
			t.Fatalf("unexpected invoice description: %v", desc)
		}
		if host != "127.0.0.1" {
			t.Fatalf("unexpected host: %v", host)
		}
		return "12345", nil
	}

	var err string
	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 {
			err = e.Message
		}
	})
	if err != "" {
		t.Fatalf("expected withdraw to succeed, got: %v", err)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestErrorContainsResponseBody(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc(
		"/withdraw/",
		func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(400)
			w.Write([]byte("this is a custom error response"))
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 {
			if e.Code != ErrInvalidResponse {
				t.Fatalf("unexpected error code: %v", e.Code)
			}
			if !strings.Contains(e.Message, "this is a custom error response") {
				t.Fatalf("expected error message to contain response, got `%s`", e.Message)
			}
		}
		if e.Code == StatusInvoiceCreated {
			t.Fatal("should not reach invoice creation")
		}
	})
}

//goland:noinspection GoUnhandledErrorResult,HttpUrlsUsage
func TestErrorContainsResponseBodyForFinishRequest(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&WithdrawResponse{
			K1:                 "foobar",
			Callback:           "http://" + r.Host + "/withdraw/complete",
			MaxWithdrawable:    1_000_000,
			DefaultDescription: "Withdraw from Lapp",
			Tag:                "withdrawRequest",
		})
	})
	mux.HandleFunc(
		"/withdraw/complete",
		func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(400)
			w.Write([]byte("this is a custom error response"))
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		return "12345", nil
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 {
			if e.Code != ErrInvalidResponse {
				t.Fatalf("unexpected error code: %v", e.Code)
			}
			if !strings.Contains(e.Message, "this is a custom error response") {
				t.Fatalf("expected error message to contain response, got `%s`", e.Message)
			}
		}
		if e.Code == StatusReceiving {
			t.Fatal("should not reach receiving status")
		}
	})
}

//goland:noinspection GoUnhandledErrorResult
func TestForbidden(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc(
		"/withdraw/",
		func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(403)
			w.Write([]byte("Forbidden"))
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 {
			if e.Code != ErrForbidden {
				t.Fatalf("unexpected error code: %v", e.Code)
			}
		}
		if e.Code == StatusInvoiceCreated {
			t.Fatal("should not reach invoice creation")
		}
	})
}

//goland:noinspection GoUnhandledErrorResult
func TestZebedee403MapsToCountryNotSupported(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc(
		"/withdraw/",
		func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(403)
			w.Write([]byte("Forbidden"))
		},
	)
	server := httptest.NewServer(mux)
	t.Cleanup(func() { server.Close() })

	// Super ugly hack to emulate that local endpoint is zebedee
	zebedeeHost = "127.0.0.1"
	t.Cleanup(func() {
		zebedeeHost = zebedeeHostConst // after test reset to its original value
	})

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(_ lnwire.MilliSatoshi, _ string, _ string) (string, error) {
		panic("should not reach here")
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 {
			if e.Code != ErrCountryNotSupported {
				t.Fatalf("unexpected error code: %v", e.Code)
			}
		}
		if e.Code == StatusInvoiceCreated {
			t.Fatal("should not reach invoice creation")
		}
	})
}

func encode(url string) (string, error) {
	return lnurl.LNURLEncode(url)
}

func TestWithdrawResponse_Validate(t *testing.T) {

	type fields struct {
		Response           Response
		Tag                string
		K1                 string
		Callback           string
		MaxWithdrawable    stringOrNumber
		MinWithdrawable    stringOrNumber
		DefaultDescription string
	}
	errorResponse := func(reason string) fields {
		return fields{
			Response: Response{
				Status: StatusError,
				Reason: reason,
			},
		}
	}

	tests := []struct {
		name   string
		fields fields
		want   int
	}{
		{
			"invalid tag",
			fields{Tag: "blebidy"},
			ErrWrongTag,
		},
		{
			"negative withdraw",
			fields{MaxWithdrawable: -1, Tag: "withdraw"},
			ErrNoAvailableBalance,
		},
		{
			"valid",
			fields{
				Response: Response{
					Status: StatusOK,
				},
				Tag:             "withdraw",
				MaxWithdrawable: 10,
			},
			ErrNone,
		},
		{
			"already being processed",
			errorResponse("This Withdrawal Request is already being processed by another wallet"),
			ErrAlreadyUsed,
		},
		{
			"can only be processed only once",
			errorResponse("This Withdrawal Request can only be processed once"),
			ErrAlreadyUsed,
		},
		{
			"withdraw is spent",
			errorResponse("Withdraw is spent"),
			ErrAlreadyUsed,
		},
		{
			"withdraw link is empty",
			errorResponse("Withdraw link is empty"),
			ErrAlreadyUsed,
		},
		{
			"has already been used",
			errorResponse("This LNURL has already been used"),
			ErrAlreadyUsed,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			wr := &WithdrawResponse{
				Response:           tt.fields.Response,
				Tag:                tt.fields.Tag,
				K1:                 tt.fields.K1,
				Callback:           tt.fields.Callback,
				MaxWithdrawable:    tt.fields.MaxWithdrawable,
				MinWithdrawable:    tt.fields.MinWithdrawable,
				DefaultDescription: tt.fields.DefaultDescription,
			}
			got, _ := wr.Validate()
			if got != tt.want {
				t.Errorf("Validate() got = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestValidate(t *testing.T) {
	type args struct {
		qr string
	}
	tests := []struct {
		name string
		args args
		want bool
	}{
		{
			name: "plain",
			args: args{
				"LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV", //nolint:lll
			},
			want: true,
		},
		{
			name: "lightning scheme",
			args: args{
				"lightning:LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV", //nolint:lll
			},
			want: true,
		},
		{
			name: "HTTP fallback scheme",
			args: args{
				"https://example.com/?lightning=LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV", //nolint:lll
			},
			want: true,
		},
		{
			name: "muun scheme",
			args: args{
				"muun:LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV", //nolint:lll
			},
			want: true,
		},
		{
			name: "muun scheme with double slashes",
			args: args{
				"muun://LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV", //nolint:lll
			},
			want: true,
		},
		{
			name: "lightning scheme with double slashes",
			args: args{
				"lightning://LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV", //nolint:lll
			},
			want: true,
		},
		{
			name: "muun + lightning schemes",
			args: args{
				"muun:lightning:LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV", //nolint:lll
			},
			want: true,
		},
		{
			name: "muun + lightning schemes with double slashes",
			args: args{
				"muun://lightning:LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV", //nolint:lll
			},
			want: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := Validate(tt.args.qr); got != tt.want {
				t.Errorf("Validate() = %v, want %v", got, tt.want)
			}
		})
	}
}
