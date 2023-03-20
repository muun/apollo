package libwallet

import (
	"encoding/hex"
	"fmt"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"

	"google.golang.org/protobuf/proto"
)

const (
	address        = "2NDhvuRPCYXq4fB8SprminieZ2a1i3JFXyS"
	amountURI      = address + "?amount=1.2"
	completeURI    = amountURI + "&label=hola&message=mensaje%20con%20espacios"
	uriWithSlashes = "bitcoin://" + amountURI

	invalidAddress = "2NDhvuRPCYXq4fB8SprminieZ2a1i3JFXya"
	randomText     = "fooo"

	bip70URL                   = "https://bitpay.com/i/KXCEAtJQssR9vG2BxdjFwx"
	bip70NonRetroCompatAddress = bitcoinScheme + "?r=" + bip70URL
	bip70RetroCompatAddress    = bitcoinScheme + address + "?r=" + bip70URL

	invoice               = "lnbcrt1pwtpd4xpp55meuklpslk5jtxytyh7u2q490c2xhm68dm3a94486zntsg7ad4vsdqqcqzys763w70h39ze44ngzhdt2mag84wlkefqkphuy7ssg4la5gt9vcpmqts00fnapf8frs928mc5ujfutzyu8apkezhrfvydx82l40w0fckqqmerzjc"
	invoiceHashHex        = "a6f3cb7c30fda925988b25fdc502a57e146bef476ee3d2d6a7d0a6b823dd6d59"
	invoiceDestinationHex = "028cfad4e092191a41f081bedfbe5a6e8f441603c78bf9001b8fb62ac0858f20edasd"

	invoice100Sat               = "lnbcrt1u1p3hdgr2pp50m67ca8yyejjlzwmf02fvlu4kejf8twmftxfu7l3jhesnlfu0hjqdqqcqzpgxqyz5vqsp56yu7l6qqftslzhclnfwtnrlwtsrpn2nvy08kzskarhj76lqspenq9qyyssqet8mqeg5yqh06aqf9sqnkya6mud3qat84s0gdplmd3dpncsjjesj5ja24n8qxwt2d968g4laeggl0txtjy48razr7unpnk4fmga87dgqj88quh"
	invoice100SatHashHex        = "7ef5ec74e426652f89db4bd4967f95b66493addb4acc9e7bf195f309fd3c7de4"
	invoice100SatDestinationHex = "03373f5fb6babc2627cc3003646cc19cc2225bd699013e3e29c6b94857596c1c15"

	invoice19Sat               = "lnbcrt190n1p3clxyrpp5w0l3cr5s49vasv3npx9ud6apw6agpv02aq5r70fhhvz5vatlhglsdqqcqzpgxqyz5vqsp5pjdr6rjpghugd5pyafa7shphqup744rtr4d7smrkfjs26cgyshyq9qyyssqx0gfkqdf3y344ejpzl3zqjyl4qwgw3xm4x4v5da73rrshw94ch0nqz3rfrgeykkws3nypystqttty562r604scgqv09agq3t7cxz8zcpf5em4q"
	invoice19SatHashHex        = "73ff1c0e90a959d83233098bc6eba176ba80b1eae8283f3d37bb0546757fba3f"
	invoice19SatDestinationHex = "03373f5fb6babc2627cc3003646cc19cc2225bd699013e3e29c6b94857596c1c15"

	lightningParam                   = "lightning="
	bip21UnifiedQr                   = bitcoinScheme + address + "?" + lightningParam + invoice
	bip21UnifiedQrWithAmount         = bitcoinScheme + address + "?amount=0.000001&" + lightningParam + invoice100Sat
	bip21UnifiedQrWithAmountMismatch = bitcoinScheme + address + "?amount=2&" + lightningParam + invoice100Sat
	bip21UnifiedQrInconsistentCase1  = bitcoinScheme + address + "?" + lightningParam + invoice100Sat
	bip21UnifiedQrInconsistentCase2  = bitcoinScheme + address + "?amount=2&" + lightningParam + invoice

	bip21UnifiedQrBip70RetroCompat              = bip70RetroCompatAddress + "&" + lightningParam + invoice
	bip21UnifiedQrBip70RetroCompatWithAmount    = bip70RetroCompatAddress + "&" + lightningParam + invoice100Sat
	bip21UnifiedQrBip70NonRetroCompat           = bip70NonRetroCompatAddress + "&" + lightningParam + invoice
	bip21UnifiedQrBip70NonRetroCompatWithAmount = bip70NonRetroCompatAddress + "&" + lightningParam + invoice100Sat
)

func TestGetPaymentURI(t *testing.T) {

	invoiceDestination, _ := hex.DecodeString(invoiceDestinationHex)
	invoicePaymentHash := make([]byte, 32)
	hex.Decode(invoicePaymentHash[:], []byte(invoiceHashHex))

	invoiceWithAmountDestination, _ := hex.DecodeString(invoice100SatDestinationHex)
	invoiceWithAmountPaymentHash := make([]byte, 32)
	hex.Decode(invoiceWithAmountPaymentHash[:], []byte(invoice100SatHashHex))

	type args struct {
		address string
		network Network
	}

	tests := []struct {
		name    string
		args    args
		want    *MuunPaymentURI
		wantErr bool
	}{

		{
			name: "validAddress",
			args: args{
				address: address,
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				Address: address,
				Uri:     bitcoinScheme + address,
			},
		},
		{
			name: "amountValidAddress",
			args: args{
				address: amountURI,
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				Address: address,
				Amount:  "1.2",
				Uri:     bitcoinScheme + amountURI,
			},
		},
		{
			name: "completeValidAddress",
			args: args{
				address: completeURI,
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				Address: address,
				Amount:  "1.2",
				Label:   "hola",
				Message: "mensaje con espacios",
				Uri:     bitcoinScheme + completeURI,
			},
		},
		{
			name: "completeValidAddress with scientific notation amount",
			args: args{
				address: address + "?amount=01.2e-3" + "&label=hola&message=mensaje%20con%20espacios",
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				Address: address,
				Amount:  "0.0012",
				Label:   "hola",
				Message: "mensaje con espacios",
				Uri:     bitcoinScheme + address + "?amount=01.2e-3" + "&label=hola&message=mensaje%20con%20espacios",
			},
		},
		{
			name: "invalidAddress",
			args: args{
				address: invalidAddress,
				network: *Regtest(),
			},
			wantErr: true,
		},
		{
			name: "randomText",
			args: args{
				address: randomText,
				network: *Regtest(),
			},
			wantErr: true,
		},
		{
			name: "BIP70NonRetroCompatAddress",
			args: args{
				address: bip70NonRetroCompatAddress,
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				Uri:      bip70NonRetroCompatAddress,
				Bip70Url: bip70URL,
			},
		},
		{
			name: "BIP70RetroCompatAddress",
			args: args{
				address: bip70RetroCompatAddress,
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				Address:  address,
				Uri:      bip70RetroCompatAddress,
				Bip70Url: bip70URL,
			},
		},
		{
			name: "URL like address",
			args: args{
				address: uriWithSlashes,
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				Address: address,
				Uri:     uriWithSlashes,
				Amount:  "1.2",
			},
		},
		{
			name: "bip21 uri edge case",
			args: args{
				address: "bitcoin:2NDhvuRPCYXq4fB8SprminieZ2a1i3JFXyS?",
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				Address: "2NDhvuRPCYXq4fB8SprminieZ2a1i3JFXyS",
				Uri:     "bitcoin:2NDhvuRPCYXq4fB8SprminieZ2a1i3JFXyS?",
			},
		},
		{
			name: "bad url",
			args: args{
				address: ":foo#%--",
				network: *Regtest(),
			},
			wantErr: true,
		},
		{
			name: "bad query",
			args: args{
				address: "bitcoin:123123?%&-=asd",
				network: *Regtest(),
			},
			wantErr: true,
		},
		{
			name: "bad amount format",
			args: args{
				address: address + "?amount=Nan" + "&label=hola&message=mensaje%20con%20espacios",
				network: *Regtest(),
			},
			// TODO we should probably return an error here but that breaks current assumptions in newop state
			// machine (see TestInvalidAmountEmitsInvalidAddress in state_test.go)
			want: &MuunPaymentURI{
				Address: address,
				Uri:     bitcoinScheme + address + "?amount=Nan" + "&label=hola&message=mensaje%20con%20espacios",
				Amount:  "Nan",
				Label:   "hola",
				Message: "mensaje con espacios",
			},
		},
		{
			name: "bad amount format 2",
			args: args{
				address: address + "?amount=hola" + "&label=hola&message=mensaje%20con%20espacios",
				network: *Regtest(),
			},
			// TODO we should probably return an error here but that breaks current assumptions in newop state
			// machine (see TestInvalidAmountEmitsInvalidAddress in state_test.go)
			want: &MuunPaymentURI{
				Address: address,
				Uri:     bitcoinScheme + address + "?amount=hola" + "&label=hola&message=mensaje%20con%20espacios",
				Amount:  "hola",
				Label:   "hola",
				Message: "mensaje con espacios",
			},
		},
		{
			name: "network mismatch",
			args: args{
				address: amountURI,
				network: *Mainnet(),
			},
			wantErr: true,
		},
		{
			name: "BIP21 with lightning",
			args: args{
				address: bip21UnifiedQr,
				network: *network,
			},
			want: &MuunPaymentURI{
				Address: address,
				Uri:     bitcoinScheme + address + "?lightning=" + invoice,

				Invoice: &Invoice{
					RawInvoice:      invoice,
					FallbackAddress: nil,
					Network:         network,
					MilliSat:        "",
					Destination:     invoiceDestination,
					PaymentHash:     invoicePaymentHash,
					Description:     "",
				}},
		},
		{
			name: "BIP21 with lightning with amount",
			args: args{
				address: bip21UnifiedQrWithAmount,
				network: *network,
			},
			want: &MuunPaymentURI{
				Address: address,
				Amount:  "0.000001",
				Uri:     bip21UnifiedQrWithAmount,

				Invoice: &Invoice{
					RawInvoice:      invoice100Sat,
					FallbackAddress: nil,
					Network:         network,
					MilliSat:        "100000",
					Sats:            100,
					Destination:     invoiceWithAmountDestination,
					PaymentHash:     invoiceWithAmountPaymentHash,
					Description:     "",
				}},
		},
		{
			name: "BIP21 with lightning with amount mismatch error",
			args: args{
				address: bip21UnifiedQrWithAmountMismatch,
				network: *network,
			},
			wantErr: true,
		},
		{
			name: "BIP21 with lightning with on-chain amount in scientific notation",
			args: args{
				address: bitcoinScheme + address + "?amount=1e-6&" + lightningParam + invoice100Sat,
				network: *network,
			},
			want: &MuunPaymentURI{
				Address: address,
				Amount:  "0.000001",
				Uri:     bitcoinScheme + address + "?amount=1e-6&" + lightningParam + invoice100Sat,

				Invoice: &Invoice{
					RawInvoice:      invoice100Sat,
					FallbackAddress: nil,
					Network:         network,
					MilliSat:        "100000",
					Sats:            100,
					Destination:     invoiceWithAmountDestination,
					PaymentHash:     invoiceWithAmountPaymentHash,
					Description:     "",
				}},
		},
		{
			name: "BIP21 with lightning invoice with amount but without amount uri/query param",
			args: args{
				address: bip21UnifiedQrInconsistentCase1,
				network: *network,
			},
			// Instead of marking this uri as invalid (technically correct) and showing an error we allow a way forward
			// for the payment and assume the lightning part is the correct one
			want: &MuunPaymentURI{
				Address: address,
				Uri:     bip21UnifiedQrInconsistentCase1,

				Invoice: &Invoice{
					RawInvoice:      invoice100Sat,
					FallbackAddress: nil,
					Network:         network,
					MilliSat:        "100000",
					Sats:            100,
					Destination:     invoiceWithAmountDestination,
					PaymentHash:     invoiceWithAmountPaymentHash,
					Description:     "",
				}},
		},
		{
			name: "BIP21 with lightning invoice without amount but with amount uri/query param",
			args: args{
				address: bip21UnifiedQrInconsistentCase2,
				network: *network,
			},
			// Instead of marking this uri as invalid (technically correct) and showing an error we allow a way forward
			// for the payment and assume the lightning part is the correct one
			want: &MuunPaymentURI{
				Address: address,
				Amount:  "2",
				Uri:     bip21UnifiedQrInconsistentCase2,

				Invoice: &Invoice{
					RawInvoice:      invoice,
					FallbackAddress: nil,
					Network:         network,
					Destination:     invoiceDestination,
					PaymentHash:     invoicePaymentHash,
					Description:     "",
				}},
		},
		{
			name: "BIP70 retrocompat with lightning",
			args: args{
				address: bip21UnifiedQrBip70RetroCompat,
				network: *network,
			},
			want: &MuunPaymentURI{
				Address:  address,
				Uri:      bip21UnifiedQrBip70RetroCompat,
				Bip70Url: bip70URL,

				Invoice: &Invoice{
					RawInvoice:      invoice,
					FallbackAddress: nil,
					Network:         network,
					Destination:     invoiceDestination,
					PaymentHash:     invoicePaymentHash,
					Description:     "",
				}},
		},
		{
			name: "BIP70 retrocompat with lightning with amount",
			args: args{
				address: bip21UnifiedQrBip70RetroCompatWithAmount,
				network: *network,
			},
			wantErr: true,
		},
		{
			name: "BIP70 non retrocompat with lightning",
			args: args{
				address: bip21UnifiedQrBip70NonRetroCompat,
				network: *network,
			},
			want: &MuunPaymentURI{
				Uri:      bip21UnifiedQrBip70NonRetroCompat,
				Bip70Url: bip70URL,

				Invoice: &Invoice{
					RawInvoice:      invoice,
					FallbackAddress: nil,
					Network:         network,
					Destination:     invoiceDestination,
					PaymentHash:     invoicePaymentHash,
					Description:     "",
				}},
		},
		{
			name: "BIP70 non retrocompat with lightning with amount",
			args: args{
				address: bip21UnifiedQrBip70NonRetroCompatWithAmount,
				network: *network,
			},
			wantErr: true,
		},
		{
			name: "ALL CAPS",
			args: args{
				address: "BITCOIN:BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2",
				network: *Mainnet(),
			},
			want: &MuunPaymentURI{
				Address: strings.ToLower("BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2"),
				Uri:     "BITCOIN:BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2",
			},
		},
		{
			name: "MiXeD Case",
			args: args{
				address: "BiTcOiN:BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2",
				network: *Mainnet(),
			},
			want: &MuunPaymentURI{
				Address: strings.ToLower("BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2"),
				Uri:     "BiTcOiN:BC1QSQP0D3TY8AAA8N9J8R0D2PF3G40VN4AS9TPWY3J9R3GK5K64VX6QWPAXH2",
			},
		},
		{
			name: "MiXeD Case lightning param",
			args: args{
				address: "BiTcOiN:" + address + "?LiGhTnInG=" + invoice,
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				Address: address,
				Uri:     "BiTcOiN:" + address + "?LiGhTnInG=" + invoice,

				Invoice: &Invoice{
					RawInvoice:      invoice,
					FallbackAddress: nil,
					Network:         network,
					MilliSat:        "",
					Destination:     invoiceDestination,
					PaymentHash:     invoicePaymentHash,
					Description:     "",
				}},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := GetPaymentURI(tt.args.address, &tt.args.network)
			if (err != nil) != tt.wantErr {
				t.Errorf("GetPaymentURI() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if got != nil && got.Invoice != nil {
				// expiry is relative to now, so ignore it
				got.Invoice.Expiry = 0
			}
			if !reflect.DeepEqual(got, tt.want) {
				var invoiceDiff = ""
				if !reflect.DeepEqual(got.Invoice, tt.want.Invoice) {
					invoiceDiff = fmt.Sprintf("Invoice = %+v, want %+v", got.Invoice, tt.want.Invoice)
				}
				t.Errorf("GetPaymentURI() = %+v, want %+v. %v", got, tt.want, invoiceDiff)
			}
		})
	}
}

func Test_normalizeAddress(t *testing.T) {
	type args struct {
		rawAddress   string
		targetScheme string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{
			name: "normalAddress",
			args: args{
				rawAddress:   address,
				targetScheme: bitcoinScheme,
			},
			want: bitcoinScheme + address,
		},
		{
			name: "bitcoinAddress",
			args: args{
				rawAddress:   bitcoinScheme + address,
				targetScheme: bitcoinScheme,
			},
			want: bitcoinScheme + address,
		},
		{
			name: "muunAddress",
			args: args{
				rawAddress:   muunScheme + address,
				targetScheme: bitcoinScheme,
			},
			want: bitcoinScheme + address,
		},
		{
			name: "muun to lightning",
			args: args{
				rawAddress:   muunScheme + address,
				targetScheme: lightningScheme,
			},
			want: lightningScheme + address,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got, _ := buildUriFromString(tt.args.rawAddress, tt.args.targetScheme); got != tt.want {
				t.Errorf("buildUriFromString() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestDoPaymentRequestCall(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/payment-request/", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Accept") != "application/bitcoin-paymentrequest" {
			t.Fatal("expected Accept header to be application/bitcoin-paymentrequest")
		}

		script, _ := hex.DecodeString("76a9146efcf883b4b6f9997be9a0600f6c095fe2bd2d9288ac")

		serializedPaymentDetails, _ := proto.Marshal(&PaymentDetails{
			Network: "test",
			Outputs: []*Output{
				{
					Script: script,
					Amount: 2500,
				},
			},
			Time:         100000,
			Expires:      102000,
			Memo:         "Hello World",
			PaymentUrl:   "http://localhost:8000/pay",
			MerchantData: []byte(""),
		})
		payReq, _ := proto.Marshal(&PaymentRequest{SerializedPaymentDetails: serializedPaymentDetails})

		w.Write(payReq)
	})
	server := httptest.NewServer(mux)
	defer server.Close()

	url := server.URL + "/payment-request/"
	paymentURI, err := DoPaymentRequestCall(url, Testnet())
	if err != nil {
		t.Fatal(err)
	}

	expected := &MuunPaymentURI{
		Address:      "mqdofsXHpePPGBFXuwwypAqCcXi48Xhb2f",
		Message:      "Hello World",
		Amount:       "0.000025",
		Bip70Url:     url,
		CreationTime: "100000",
		ExpiresTime:  "102000",
	}
	if !reflect.DeepEqual(paymentURI, expected) {
		t.Fatalf("decoded URI struct does not match expected, %+v != %+v", paymentURI, expected)
	}
}
