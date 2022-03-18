package libwallet

import (
	"bytes"
	"encoding/hex"
	"encoding/json"
	"testing"

	"github.com/btcsuite/btcutil"
	"github.com/lightningnetwork/lnd/lnwire"
	"github.com/lightningnetwork/lnd/zpay32"
)

func TestInvoiceSecrets(t *testing.T) {
	setup()

	network := Regtest()

	userKey, _ := NewHDPrivateKey(randomBytes(32), network)
	userKey.Path = "m/schema:1'/recovery:1'"
	muunKey, _ := NewHDPrivateKey(randomBytes(32), network)
	muunKey.Path = "m/schema:1'/recovery:1'"

	routeHints := &RouteHints{
		Pubkey:                    "03c48d1ff96fa32e2776f71bba02102ffc2a1b91e2136586418607d32e762869fd",
		FeeBaseMsat:               1000,
		FeeProportionalMillionths: 1000,
		CltvExpiryDelta:           8,
	}

	secrets, err := GenerateInvoiceSecrets(userKey.PublicKey(), muunKey.PublicKey())
	if err != nil {
		t.Fatal(err)
	}
	if secrets.Length() != 5 {
		t.Fatalf("expected 5 new secrets, got %d", secrets.Length())
	}

	err = PersistInvoiceSecrets(secrets)
	if err != nil {
		t.Fatal(err)
	}

	t.Run("generating more invoices", func(t *testing.T) {
		// Make sure the secrets list is already topped up
		_, err := GenerateInvoiceSecrets(userKey.PublicKey(), muunKey.PublicKey())
		if err != nil {
			t.Fatal(err)
		}

		// try to generate more secrets
		moreSecrets, err := GenerateInvoiceSecrets(userKey.PublicKey(), muunKey.PublicKey())
		if err != nil {
			t.Fatal(err)
		}
		if moreSecrets.Length() != 0 {
			t.Fatal("expected no new secrets to be created")
		}
	})

	t.Run("create an invoice", func(t *testing.T) {
		builder := &InvoiceBuilder{}
		builder.Network(network)
		builder.UserKey(userKey)
		builder.AddRouteHints(routeHints)
		builder.AmountSat(1000)
		builder.Description("hello world")
		invoice, err := builder.Build()
		if err != nil {
			t.Fatal(err)
		}
		if invoice == "" {
			t.Fatal("expected non-empty invoice string")
		}

		payreq, err := zpay32.Decode(invoice, network.network)
		if err != nil {
			t.Fatal(err)
		}
		if !payreq.Features.HasFeature(lnwire.TLVOnionPayloadOptional) {
			t.Fatal("expected invoice to have var onion optin feature")
		}
		if !payreq.Features.HasFeature(lnwire.PaymentAddrOptional) {
			t.Fatal("expected invoice to have payment secret feature")
		}
		if payreq.MilliSat.ToSatoshis() != btcutil.Amount(1000) {
			t.Fatalf("expected invoice amount to be 1000 sats, got %v", payreq.MilliSat)
		}
		if payreq.Description == nil || *payreq.Description != "hello world" {
			t.Fatalf("expected payment description to match, got %v", payreq.Description)
		}
		if payreq.MinFinalCLTVExpiry() != 72 {
			t.Fatalf("expected min final CLTV expiry to be 72, got %v", payreq.MinFinalCLTVExpiry())
		}
		if payreq.PaymentAddr == nil {
			t.Fatalf("expected payment addr to be non-nil")
		}
		if len(payreq.RouteHints) == 0 {
			t.Fatalf("expected invoice to contain route hints")
		}
		hopHints := payreq.RouteHints[0]
		if len(hopHints) != 1 {
			t.Fatalf("expected invoice route hints to contain exactly 1 hop hint")
		}
		if hopHints[0].ChannelID&(1<<63) == 0 {
			t.Fatal("invalid short channel id in hophints")
		}
		if hopHints[0].FeeBaseMSat != 1000 {
			t.Fatalf("expected fee base to be 1000 msat, got %v instead", hopHints[0].FeeBaseMSat)
		}
		if hopHints[0].FeeProportionalMillionths != 1000 {
			t.Fatalf("expected fee proportional millionths to be 1000, got %v instead", hopHints[0].FeeProportionalMillionths)
		}
		if hopHints[0].CLTVExpiryDelta != 8 {
			t.Fatalf("expected CLTV expiry delta to be 8, got %v instead", hopHints[0].CLTVExpiryDelta)
		}
		metadata, err := GetInvoiceMetadata(payreq.PaymentHash[:])
		if err != nil {
			t.Fatalf("expected invoice to contain metadata, got error: %v", err)
		}
		decryptedMetadata, err := userKey.Decrypter().Decrypt(metadata)
		if err != nil {
			t.Fatalf("expected metadata to decrypt correctly, got error: %v", err)
		}
		var jsonMetadata OperationMetadata
		err = json.NewDecoder(bytes.NewReader(decryptedMetadata)).Decode(&jsonMetadata)
		if err != nil {
			t.Fatal("expected metadata to parse correctly")
		}
		if jsonMetadata.Invoice == "" {
			t.Fatal("expected metadata to contain a non-empty invoice")
		}
	})

	t.Run("creating a 2nd invoice returns a different payment hash", func(t *testing.T) {

		builder := &InvoiceBuilder{}
		builder.Network(network)
		builder.UserKey(userKey)
		builder.AddRouteHints(routeHints)
		invoice1, err := builder.Build()
		if err != nil {
			t.Fatal(err)
		}

		payreq1, err := zpay32.Decode(invoice1, network.network)
		if err != nil {
			t.Fatal(err)
		}

		invoice2, err := builder.Build()
		if err != nil {
			t.Fatal(err)
		}

		payreq2, err := zpay32.Decode(invoice2, network.network)
		if err != nil {
			t.Fatal(err)
		}

		if payreq1.PaymentHash == payreq2.PaymentHash {
			t.Fatal("successive invoice payment hashes should be different")
		}

	})

	t.Run("amountMsat gets stored", func(t *testing.T) {
		builder := &InvoiceBuilder{}
		builder.Network(network)
		builder.UserKey(userKey)
		builder.AddRouteHints(routeHints)
		builder.AmountMSat(1001)
		invoice3, err := builder.Build()
		if err != nil {
			t.Fatal(err)
		}

		payreq3, err := zpay32.Decode(invoice3, network.network)
		if err != nil {
			t.Fatal(err)
		}

		db, err := openDB()
		if err != nil {
			t.Fatal(err)
		}
		defer db.Close()
		invoiceMetadata, err := db.FindByPaymentHash(payreq3.PaymentHash[:])
		if err != nil {
			t.Fatal(err)
		}

		// Note that we sent 1001 msats
		if invoiceMetadata.AmountSat != 1 {
			t.Fatalf("Expected persisted amount to 1 found %v", invoiceMetadata.AmountSat)
		}
	})

	t.Run("two route hints are encoded", func(t *testing.T) {
		builder := &InvoiceBuilder{}
		invoice, err := builder.
			Network(network).
			UserKey(userKey).
			AddRouteHints(routeHints).
			AddRouteHints(&RouteHints{
				Pubkey:                    "03c48d1ff96fa32e2776f71bba02102ffc2a1b91e2136586418607d32e762869ff",
				FeeBaseMsat:               123,
				FeeProportionalMillionths: 1,
				CltvExpiryDelta:           23,
			}).
			Build()
		if err != nil {
			t.Fatal(err)
		}

		payreq, err := zpay32.Decode(invoice, network.network)
		if err != nil {
			t.Fatal(err)
		}

		if len(payreq.RouteHints) != 2 {
			t.Fatalf("Expected there to be 2 route hints, found %v", len(payreq.RouteHints))
		}

		for i, hops := range payreq.RouteHints {
			if len(hops) != 1 {
				t.Fatalf(
					"Expected hops for hint %v to be 1, found %v",
					i,
					len(hops),
				)
			}
			hint := hops[0]

			var expectedFeeBase, expectedProportional uint32
			var expectedPubKey string
			if hint.CLTVExpiryDelta == 23 {
				// Second hint
				expectedFeeBase = 123
				expectedProportional = 1
				expectedPubKey = "03c48d1ff96fa32e2776f71bba02102ffc2a1b91e2136586418607d32e762869ff"
			} else if hint.CLTVExpiryDelta == uint16(routeHints.CltvExpiryDelta) {
				// First hint
				expectedFeeBase = uint32(routeHints.FeeBaseMsat)
				expectedProportional = uint32(routeHints.FeeProportionalMillionths)
				expectedPubKey = routeHints.Pubkey
			} else {
				t.Fatalf("Failed to match route hint %v: %v", i, hops)
			}

			if hint.ChannelID&(1<<63) == 0 {
				t.Fatal("invalid short channel id in hophints")
			}
			if hint.FeeProportionalMillionths != expectedProportional {
				t.Fatalf("Route hint %v proportional fee %v != %v", i, hint.FeeProportionalMillionths, expectedProportional)
			}
			if hint.FeeBaseMSat != expectedFeeBase {
				t.Fatalf("Route hint %v base fee %v != %v", i, hint.FeeBaseMSat, expectedFeeBase)
			}
			pubKey := hex.EncodeToString(hint.NodeID.SerializeCompressed())
			if pubKey != expectedPubKey {
				t.Fatalf("Route hint %v pub key %v != %v", i, pubKey, expectedPubKey)
			}
		}
	})

}

func TestGetInvoiceMetadataMissingHash(t *testing.T) {
	setup()

	_, err := GetInvoiceMetadata(randomBytes(32))
	if err == nil {
		t.Fatal("expected GetInvoiceMetadata to fail")
	}
}
