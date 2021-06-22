package lnurl

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/fiatjaf/go-lnurl"
	"github.com/lightningnetwork/lnd/lnwire"
)

func TestWithdraw(t *testing.T) {
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
	mux.HandleFunc("/withdraw/complete", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&Response{
			Status: StatusOK,
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()

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

func TestDecodeError(t *testing.T) {
	qr := "lightning:abcde"

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
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

func TestWrongTagError(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/channelRequest", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&WithdrawResponse{
			Tag: "channelRequest",
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	qr, _ := encode(fmt.Sprintf("%s/channelRequest", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
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

func TestUnreachableError(t *testing.T) {
	originalTimeout := httpClient.Timeout
	httpClient.Timeout = 1 * time.Second
	defer func() {
		httpClient.Timeout = originalTimeout
	}()

	// LNURL QR pointing to a non-responding domain
	qr := "LIGHTNING:LNURL1DP68GURN8GHJ7ARGD9EJUER0D4SKJM3WV3HK2UEWDEHHGTN90P5HXAPWV4UXZMTSD3JJUCM0D5LHXETRWFJHG0F3XGENGDGQ8EH52"

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
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
	mux.HandleFunc("/withdraw/complete", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&Response{
			Status: StatusError,
			Reason: "something something",
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()

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

func TestInvalidResponseError(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("foobar"))
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
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

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
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
	qr := "lightning:lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veax2un09e3k7mf0w5lhgct884kx7emfdcnxkvfa8qexxc35vymnxcf5xumkxvfsv4snxwph8qunzv3hxesnyv3jvv6nyv3e8yuxzvnpv4skvepnxg6rwv34xqck2c3sxcerzdpnv56r2dss2vt96"

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
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

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
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

func TestExpiredCheck(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/withdraw/", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&Response{
			Status: "ERROR",
			Reason: "something something Expired blabla",
		})
	})
	mux.HandleFunc("/withdraw/complete", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&Response{
			Status: StatusOK,
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
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
	defer server.Close()

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
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
	mux.HandleFunc("/withdraw/complete", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(&Response{
			Status: StatusError,
			Reason: "Unable to pay LN Invoice: FAILURE_REASON_NO_ROUTE",
		})
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	qr, _ := encode(fmt.Sprintf("%s/withdraw", server.URL))

	createInvoiceFunc := func(amt lnwire.MilliSatoshi, desc string, host string) (string, error) {
		return "12345", nil
	}

	Withdraw(qr, createInvoiceFunc, true, func(e *Event) {
		if e.Code < 100 && e.Code != ErrNoRoute {
			t.Fatalf("unexpected error code: %d", e.Code)
		}
	})
}

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
	defer server.Close()

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

func TestValidate(t *testing.T) {
	link := "lightning:LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV"

	ok := Validate(link)
	if !ok {
		t.Fatal("expected to validate link")
	}
}

func TestValidateFallbackScheme(t *testing.T) {
	link := "https://example.com/?lightning=LNURL1DP68GUP69UHKCMMRV9KXSMMNWSARWVPCXQHKCMN4WFKZ7AMFW35XGUNPWULHXETRWFJHG0F3XGENGDGK59DKV"

	ok := Validate(link)
	if !ok {
		t.Fatal("expected to validate link with fallback scheme")
	}
}

func encode(url string) (string, error) {
	return lnurl.LNURLEncode(url)
}
