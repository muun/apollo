package libwallet

import (
	"encoding/hex"
	"fmt"
	"reflect"
	"testing"
)

func TestGenerateBip21Uri(t *testing.T) {

	invoiceDestination, _ := hex.DecodeString(invoiceDestinationHex)
	invoicePaymentHash := make([]byte, 32)
	hex.Decode(invoicePaymentHash[:], []byte(invoiceHashHex))

	invoiceWithAmountDestination, _ := hex.DecodeString(invoice100SatDestinationHex)
	invoiceWithAmountPaymentHash := make([]byte, 32)
	hex.Decode(invoiceWithAmountPaymentHash[:], []byte(invoice100SatHashHex))

	invoiceWithTinyAmountDestination, _ := hex.DecodeString(invoice19SatDestinationHex)
	invoiceWithTinyAmountPaymentHash := make([]byte, 32)
	hex.Decode(invoiceWithTinyAmountPaymentHash[:], []byte(invoice19SatHashHex))

	type args struct {
		uri *MuunPaymentURI
	}

	tests := []struct {
		name    string
		args    args
		want    string
		wantErr bool
	}{
		{
			name: "amountValidAddress",
			args: args{&MuunPaymentURI{
				Address: address,
				Amount:  "1.2",
				Uri:     bitcoinScheme + amountURI,
			}},
			want: bitcoinScheme + amountURI,
		},
		// We do not support description/message in our receive bitcoin uris, yet ;)
		//{
		//	name: "completeValidAddress",
		//	args: args{&MuunPaymentURI{
		//		Address: address,
		//		Amount:  "1.2",
		//		Label:   "hola",
		//		Message: "mensaje con espacios",
		//		Uri:     bitcoinScheme + completeURI,
		//	}},
		//	want: bitcoinScheme + completeURI,
		//},
		{
			name: "BIP21 with lightning",
			args: args{&MuunPaymentURI{
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
			want: bip21UnifiedQr,
		},
		{
			name: "BIP21 with lightning with amount",
			args: args{&MuunPaymentURI{
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
				},
			}},
			want: bip21UnifiedQrWithAmount,
		},
		{
			name: "BIP21 with lightning with amount mismatch error",
			args: args{&MuunPaymentURI{
				Address: address,
				Amount:  "0.000001",
				Uri:     bip21UnifiedQrWithAmount,

				Invoice: &Invoice{
					RawInvoice:      invoice100Sat,
					FallbackAddress: nil,
					Network:         network,
					MilliSat:        "200000",
					Sats:            200,
					Destination:     invoiceWithAmountDestination,
					PaymentHash:     invoiceWithAmountPaymentHash,
					Description:     "",
				},
			}},
			wantErr: true,
		},
		{
			name: "BIP21 with lightning with tiny amount",
			args: args{&MuunPaymentURI{
				Address: address,
				Amount:  "0.00000019",
				Uri:     bitcoinScheme + address + "?amount=0.00000019&" + lightningParam + invoice19Sat,

				Invoice: &Invoice{
					RawInvoice:      invoice19Sat,
					FallbackAddress: nil,
					Network:         network,
					MilliSat:        "19000",
					Sats:            19,
					Destination:     invoiceWithTinyAmountDestination,
					PaymentHash:     invoiceWithTinyAmountPaymentHash,
					Description:     "",
				},
			}},
			want: bitcoinScheme + address + "?amount=0.00000019&" + lightningParam + invoice19Sat,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {

			got, err := GenerateBip21Uri(tt.args.uri)
			if (err != nil) != tt.wantErr {
				t.Errorf("GenerateBip21Uri() error = %v, wantErr %v", err, tt.wantErr)
				return
			}

			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("GenerateBip21Uri() = %+v, want %+v", got, tt.want)
			}

			if !tt.wantErr {
				uri, err := GetPaymentURI(got, Regtest())

				if err != nil {
					t.Errorf("GenerateBip21Uri() error while parsing output = %v", err)
					return
				}

				if uri != nil && uri.Invoice != nil {
					// expiry is relative to now, so ignore it
					uri.Invoice.Expiry = 0
				}

				if !reflect.DeepEqual(uri, tt.args.uri) {

					var invoiceDiff = ""
					if !reflect.DeepEqual(uri.Invoice, tt.args.uri.Invoice) {
						invoiceDiff = fmt.Sprintf("Invoice = %+v, want %+v", uri.Invoice, tt.args.uri.Invoice)
					}

					t.Errorf("GenerateBip21Uri() gen + parse = %+v, want %+v. %v", uri, tt.args.uri, invoiceDiff)

				}
			}

		})
	}
}
