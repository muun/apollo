package libwallet

import (
	"encoding/hex"
	"reflect"
	"testing"
)

const (
	address        = "2NDhvuRPCYXq4fB8SprminieZ2a1i3JFXyS"
	amountURI      = address + "?amount=1.2"
	completeURI    = amountURI + "&label=hola&message=mensaje%20con%20espacios"
	uriWithSlashes = "bitcoin://" + amountURI

	invalidAddress = "2NDhvuRPCYXq4fB8SprminieZ2a1i3JFXya"

	bip70URL                   = "https://bitpay.com/i/KXCEAtJQssR9vG2BxdjFwx"
	bip70NonRetroCompatAddress = bitcoinScheme + "?r=" + bip70URL
	bip70RetroCompatAddress    = bitcoinScheme + address + "?r=" + bip70URL

)

func TestGetPaymentURI(t *testing.T) {

	const (
		invoice               = "lnbcrt1pwtpd4xpp55meuklpslk5jtxytyh7u2q490c2xhm68dm3a94486zntsg7ad4vsdqqcqzys763w70h39ze44ngzhdt2mag84wlkefqkphuy7ssg4la5gt9vcpmqts00fnapf8frs928mc5ujfutzyu8apkezhrfvydx82l40w0fckqqmerzjc"
		invoiceHashHex        = "a6f3cb7c30fda925988b25fdc502a57e146bef476ee3d2d6a7d0a6b823dd6d59"
		invoiceDestinationHex = "028cfad4e092191a41f081bedfbe5a6e8f441603c78bf9001b8fb62ac0858f20edasd"
	)

	invoiceDestination, _ := hex.DecodeString(invoiceDestinationHex)
	invoicePaymentHash := [32]byte{}
	hex.Decode(invoicePaymentHash[:], []byte(invoiceHashHex))

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
				URI:     bitcoinScheme + address,
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
				URI:     bitcoinScheme + amountURI,
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
				URI:     bitcoinScheme + completeURI,
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
			name: "BIP70NonRetroCompatAddress",
			args: args{
				address: bip70NonRetroCompatAddress,
				network: *Regtest(),
			},
			want: &MuunPaymentURI{
				URI:      bip70NonRetroCompatAddress,
				BIP70Url: bip70URL,
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
				URI:      bip70RetroCompatAddress,
				BIP70Url: bip70URL,
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
				URI:     uriWithSlashes,
				Amount:  "1.2",
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
			name: "network mismatch",
			args: args{
				address: amountURI,
				network: *Mainnet(),
			},
			wantErr: true,
		},
		{
			name: "BIP with lightning",
			args: args{
				address: "bitcoin:123123?lightning=" + invoice,
				network: *network,
			},
			want: &MuunPaymentURI{Invoice: &Invoice{
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
				t.Errorf("GetPaymentURI() = %+v, want %+v", got, tt.want)
			}
		})
	}
}

func Test_normalizeAddress(t *testing.T) {
	type args struct {
		rawAddress string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{
			name: "normalAddress",
			args: args{
				rawAddress: address,
			},
			want: bitcoinScheme + address,
		},
		{
			name: "bitcoinAddress",
			args: args{
				rawAddress: bitcoinScheme + address,
			},
			want: bitcoinScheme + address,
		},
		{
			name: "muunAddress",
			args: args{
				rawAddress: muunScheme + address,
			},
			want: bitcoinScheme + address,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := normalizeAddress(tt.args.rawAddress); got != tt.want {
				t.Errorf("normalizeAddress() = %v, want %v", got, tt.want)
			}
		})
	}
}
