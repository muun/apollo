package libwallet

import (
	"encoding/hex"
	"reflect"
	"testing"
)

func TestParseInvoice(t *testing.T) {

	const (
		invoice                 = "lnbcrt1pwtpd4xpp55meuklpslk5jtxytyh7u2q490c2xhm68dm3a94486zntsg7ad4vsdqqcqzys763w70h39ze44ngzhdt2mag84wlkefqkphuy7ssg4la5gt9vcpmqts00fnapf8frs928mc5ujfutzyu8apkezhrfvydx82l40w0fckqqmerzjc"
		invoiceWithAmount       = "lnbcrt10u1pwtpd4jpp5lh0p9amq02xel0gduna95ta5ve9q5dwyk8tglvpa258yzzvcgynsdqqcqzysrukfteknjzcqpu8kfnm76dhdtnkmyr3j42xrl89axhqxmpgusyqhn28u2uaave3nr8sk3mg5nug6t8hcnj2aw8t2l5wtksh6w0yyntgqjrrgqk"
		invoiceWithDescription  = "lnbcrt1pwtpdh7pp5celcayxvuw9pm9f8420n2dyd3css8ahzlr4nl69uczhf2sf99ydqdqswe5hvcfqwpjhymmwcqzysx7gwcf9a559rxrah9yp0u7dnk4vuvq2ywy6dyqtwzna9c92q058qppmv9p094vq9g6nv46d3sc7jd8faglzjj2h0w7j06wcu2h3e27cqc5zm4d"
		invoiceWithFallbackAdrr = "lnbcrt1pwtpduxpp57xglq4thtrerzzxt8wzg4wresfclewh8pk8xghahwq8kgek3qslqdqqcqzysfppqhv0a0uhrt2crdehgfge8e8e6texw3q4hpmge888yuu6076utcrhgc97wu7vydmudyagkz25ahuyp4fqrc9e945ff248cpa3krn7vvgcqq6spyuqltd245sjvwh23gz220cegadspkn3lx0"

		invoiceHashHex                 = "a6f3cb7c30fda925988b25fdc502a57e146bef476ee3d2d6a7d0a6b823dd6d59"
		invoiceWithAmountHashHex       = "fdde12f7607a8d9fbd0de4fa5a2fb4664a0a35c4b1d68fb03d550e4109984127"
		invoiceWithDescriptionHashHex  = "c67f8e90cce38a1d9527aa9f35348d8e2103f6e2f8eb3fe8bcc0ae954125291a"
		invoiceWithFallbackAddrHashHex = "f191f0557758f23108cb3b848ab8798271fcbae70d8e645fb7700f6466d1043e"

		invoiceDestinationHex = "028cfad4e092191a41f081bedfbe5a6e8f441603c78bf9001b8fb62ac0858f20edasd"
	)

	invoiceDestination, _ := hex.DecodeString(invoiceDestinationHex)

	invoicePaymentHash := make([]byte, 32)
	hex.Decode(invoicePaymentHash[:], []byte(invoiceHashHex))

	invoiceWithAmountPaymentHash := make([]byte, 32)
	hex.Decode(invoiceWithAmountPaymentHash[:], []byte(invoiceWithAmountHashHex))
	invoiceWithDescriptionPaymentHash := make([]byte, 32)
	hex.Decode(invoiceWithDescriptionPaymentHash[:], []byte(invoiceWithDescriptionHashHex))
	invoiceWithFallbackAddrPaymentHash := make([]byte, 32)
	hex.Decode(invoiceWithFallbackAddrPaymentHash[:], []byte(invoiceWithFallbackAddrHashHex))

	fallbackAddr, _ := GetPaymentURI("bcrt1qhv0a0uhrt2crdehgfge8e8e6texw3q4has8jh7", network)

	type args struct {
		invoice string
		network *Network
	}
	tests := []struct {
		name    string
		args    args
		want    *Invoice
		wantErr bool
	}{
		{
			name: "simple invoice",
			args: args{
				invoice: invoice,
				network: network,
			},
			want: &Invoice{
				RawInvoice:      invoice,
				FallbackAddress: nil,
				Network:         network,
				MilliSat:        "",
				Destination:     invoiceDestination,
				PaymentHash:     invoicePaymentHash,
				Description:     "",
			},
		},
		{
			name: "simple invoice with scheme",
			args: args{
				invoice: lightningScheme + invoice,
				network: network,
			},
			want: &Invoice{
				RawInvoice:      invoice,
				FallbackAddress: nil,
				Network:         network,
				MilliSat:        "",
				Destination:     invoiceDestination,
				PaymentHash:     invoicePaymentHash,
				Description:     "",
			},
		},
		{
			name: "simple invoice with uppercased scheme",
			args: args{
				invoice: "LIGHTNING:" + invoice,
				network: network,
			},
			want: &Invoice{
				RawInvoice:      invoice,
				FallbackAddress: nil,
				Network:         network,
				MilliSat:        "",
				Destination:     invoiceDestination,
				PaymentHash:     invoicePaymentHash,
				Description:     "",
			},
		},
		{
			// -amt 1000
			name: "invoice with amount",
			args: args{
				invoice: invoiceWithAmount,
				network: network,
			},
			want: &Invoice{
				RawInvoice:      invoiceWithAmount,
				FallbackAddress: nil,
				Network:         network,
				MilliSat:        "1000000",
				Sats:            1000,
				Destination:     invoiceDestination,
				PaymentHash:     invoiceWithAmountPaymentHash,
				Description:     "",
			},
		},
		{
			// "viva peron"
			name: "invoice with description",
			args: args{
				invoice: invoiceWithDescription,
				network: network,
			},
			want: &Invoice{
				RawInvoice:      invoiceWithDescription,
				FallbackAddress: nil,
				Network:         network,
				MilliSat:        "",
				Destination:     invoiceDestination,
				PaymentHash:     invoiceWithDescriptionPaymentHash,
				Description:     "viva peron",
			},
		},
		{
			// addr bcrt1qhv0a0uhrt2crdehgfge8e8e6texw3q4has8jh7
			name: "invoice with fallback address",
			args: args{
				invoice: invoiceWithFallbackAdrr,
				network: network,
			},
			want: &Invoice{
				RawInvoice:      invoiceWithFallbackAdrr,
				FallbackAddress: fallbackAddr,
				Network:         network,
				MilliSat:        "",
				Destination:     invoiceDestination,
				PaymentHash:     invoiceWithFallbackAddrPaymentHash,
				Description:     "",
			},
		},
		{
			name: "invoice with invalid fallback address",
			args: args{
				invoice: "lnbcrt1pwtpduxpp57xglq4thtrerzzxt8wzg4wresfclewh8pk8xghahwq8kgek3qslqdqqcqzysfppqhv0a0uhrt2crdehgfge8e8e6texw3q4hpmge888yuu6076utcrhgc97wu7vydmudyagkz25ahuyp4fqrc9e945ff248cpa3krn7vvgcqq6spyuqltd245sjvwh23gz220cegadspkn3lx0",
				network: Mainnet(),
			},
			wantErr: true,
		},
		{
			name: "malformed invoice",
			args: args{
				invoice: "asdasd",
				network: network,
			},
			wantErr: true,
		},
		{
			name: "simple invoice with muun scheme",
			args: args{
				invoice: muunScheme + invoice,
				network: network,
			},
			want: &Invoice{
				RawInvoice:      invoice,
				FallbackAddress: nil,
				Network:         network,
				MilliSat:        "",
				Destination:     invoiceDestination,
				PaymentHash:     invoicePaymentHash,
				Description:     "",
			},
		},
		{
			name: "simple invoice with muun:// scheme",
			args: args{
				invoice: muunScheme + "//" + invoice,
				network: network,
			},
			want: &Invoice{
				RawInvoice:      invoice,
				FallbackAddress: nil,
				Network:         network,
				MilliSat:        "",
				Destination:     invoiceDestination,
				PaymentHash:     invoicePaymentHash,
				Description:     "",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParseInvoice(tt.args.invoice, tt.args.network)
			if (err != nil) != tt.wantErr {
				t.Errorf("ParseInvoice() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if got != nil {
				// expiry is relative to now, so ignore it
				got.Expiry = 0
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("ParseInvoice() = %v, want %v", got, tt.want)
			}
		})
	}
}
