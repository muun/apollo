package walletdb

import (
	"bytes"
	"crypto/rand"
	"io/ioutil"
	"math"
	"path"
	"testing"
)

func TestOpen(t *testing.T) {
	dir, err := ioutil.TempDir("", "libwallet")
	if err != nil {
		panic(err)
	}

	db, err := Open(path.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
}

func TestInvoices(t *testing.T) {
	dir, err := ioutil.TempDir("", "libwallet")
	if err != nil {
		panic(err)
	}

	db, err := Open(path.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	shortChanId := uint64((math.MaxInt64 - 5) | (1 << 63))
	paymentHash := randomBytes(32)

	err = db.CreateInvoice(&Invoice{
		Preimage:      randomBytes(32),
		PaymentHash:   paymentHash,
		PaymentSecret: randomBytes(32),
		KeyPath:       "34/56",
		ShortChanId:   shortChanId,
		State:         InvoiceStateRegistered,
	})
	if err != nil {
		t.Fatal(err)
	}

	count, err := db.CountUnusedInvoices()
	if err != nil {
		t.Fatal(err)
	}
	if count != 1 {
		t.Fatalf("expected to find 1 unused invoice, got %d", count)
	}

	inv, err := db.FindByPaymentHash(paymentHash)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(inv.PaymentHash, paymentHash) {
		t.Fatal("expected invoice payment hash does not match")
	}

	inv, err = db.FindFirstUnusedInvoice()
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(inv.PaymentHash, paymentHash) {
		t.Fatal("expected invoice payment hash does not match")
	}
	if inv.ShortChanId != shortChanId {
		t.Fatal("expected invoice short channel id does not match")
	}

	err = db.SaveInvoice(inv)
	if err != nil {
		t.Fatal(err)
	}
	if inv.ShortChanId != shortChanId {
		t.Fatal("expected invoice short channel id does not match")
	}
}

func randomBytes(count int) []byte {
	buf := make([]byte, count)
	_, err := rand.Read(buf)
	if err != nil {
		panic(err)
	}
	return buf
}
