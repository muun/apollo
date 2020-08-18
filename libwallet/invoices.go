package libwallet

import (
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"path"
	"time"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcutil"
	"github.com/lightningnetwork/lnd/lnwire"
	"github.com/lightningnetwork/lnd/netann"
	"github.com/lightningnetwork/lnd/zpay32"
	"github.com/muun/libwallet/walletdb"
)

const MaxUnusedSecrets = 5

const (
	identityKeyChildIndex = 0
	htlcKeyChildIndex     = 1
)

// InvoiceSecrets represents a bundle of secrets required to generate invoices
// from the client. These secrets must be registered with the remote server
// and persisted in the client database before use.
type InvoiceSecrets struct {
	preimage      []byte
	paymentSecret []byte
	PaymentHash   []byte
	KeyPath       string
	IdentityKey   string
	HtlcKeyPair   *KeyPair
	ShortChanId   uint64
}

// KeyPair defines a pair of cosigning keys. One for the user and the other for
// Muun.
type KeyPair struct {
	UserKey string
	MuunKey string
}

// RouteHints is a struct returned by the remote server containing the data
// necessary for constructing an invoice locally.
type RouteHints struct {
	Pubkey                    string
	FeeBaseMsat               uint64
	FeeProportionalMillionths uint64
	CltvExpiryDelta           uint32
}

// InvoiceOptions defines additional options that can be configured when
// creating a new invoice.
type InvoiceOptions struct {
	Description string
	AmountSat   uint64
}

// GenerateInvoiceSecrets returns a slice of new secrets to register with
// the remote server. Once registered, those invoices should be stored with
// the PersistInvoiceSecrets method.
func GenerateInvoiceSecrets(userKey, muunKey *HDPublicKey) ([]*InvoiceSecrets, error) {

	var secrets []*InvoiceSecrets

	db, err := openDB()
	if err != nil {
		return nil, err
	}

	unused, err := db.CountUnusedInvoices()
	if err != nil {
		return nil, err
	}

	if unused >= MaxUnusedSecrets {
		return make([]*InvoiceSecrets, 0), nil
	}

	num := MaxUnusedSecrets - unused

	for i := 0; i < num; i++ {
		preimage := randomBytes(32)
		paymentSecret := randomBytes(32)
		paymentHashArray := sha256.Sum256(preimage)
		paymentHash := paymentHashArray[:]

		levels := randomBytes(8)
		l1 := binary.LittleEndian.Uint32(levels[:4]) & 0x7FFFFFFF
		l2 := binary.LittleEndian.Uint32(levels[4:]) & 0x7FFFFFFF

		keyPath := fmt.Sprintf("m/0'/0'/invoices:4/%d/%d", l1, l2)

		identityKeyPath := fmt.Sprintf("%s/%d", keyPath, identityKeyChildIndex)

		identityKey, err := userKey.DeriveTo(identityKeyPath)
		if err != nil {
			return nil, err
		}

		htlcKeyPath := fmt.Sprintf("%s/%d", keyPath, htlcKeyChildIndex)

		userHtlcKey, err := userKey.DeriveTo(htlcKeyPath)
		if err != nil {
			return nil, err
		}
		muunHtlcKey, err := muunKey.DeriveTo(htlcKeyPath)
		if err != nil {
			return nil, err
		}
		htlcKeyPair := &KeyPair{
			UserKey: hex.EncodeToString(userHtlcKey.Raw()),
			MuunKey: hex.EncodeToString(muunHtlcKey.Raw()),
		}

		shortChanId := binary.LittleEndian.Uint64(randomBytes(8)) | (1 << 63)

		secrets = append(secrets, &InvoiceSecrets{
			preimage:      preimage,
			paymentSecret: paymentSecret,
			PaymentHash:   paymentHash,
			KeyPath:       keyPath,
			IdentityKey:   hex.EncodeToString(identityKey.Raw()),
			HtlcKeyPair:   htlcKeyPair,
			ShortChanId:   shortChanId,
		})
	}

	// TODO: cleanup used secrets

	return secrets, nil
}

// PersistInvoiceSecrets stores secrets registered with the remote server
// in the device local database. These secrets can be used to craft new
// Lightning invoices.
func PersistInvoiceSecrets(invoiceSecrets []*InvoiceSecrets) error {
	db, err := openDB()
	if err != nil {
		return err
	}
	defer db.Close()

	for _, s := range invoiceSecrets {
		db.CreateInvoice(&walletdb.Invoice{
			Preimage:      s.preimage,
			PaymentHash:   s.PaymentHash,
			PaymentSecret: s.paymentSecret,
			KeyPath:       s.KeyPath,
			ShortChanId:   s.ShortChanId,
			State:         walletdb.InvoiceStateRegistered,
		})
	}
	return nil
}

// CreateInvoice returns a new lightning invoice string for the given network.
// Amount and description can be configured optionally.
func CreateInvoice(net *Network, userKey *HDPrivateKey, routeHints *RouteHints, opts *InvoiceOptions) (string, error) {
	// obtain first unused secret from db
	db, err := openDB()
	if err != nil {
		return "", err
	}
	defer db.Close()

	dbInvoice, err := db.FindFirstUnusedInvoice()
	if err != nil {
		return "", err
	}

	var paymentHash [32]byte
	copy(paymentHash[:], dbInvoice.PaymentHash)

	nodeID, err := parsePubKey(routeHints.Pubkey)
	if err != nil {
		return "", fmt.Errorf("can't parse route hint pubkey: %w", err)
	}

	var iopts []func(*zpay32.Invoice)
	iopts = append(iopts, zpay32.RouteHint([]zpay32.HopHint{
		zpay32.HopHint{
			NodeID:                    nodeID,
			ChannelID:                 dbInvoice.ShortChanId,
			FeeBaseMSat:               uint32(routeHints.FeeBaseMsat),
			FeeProportionalMillionths: uint32(routeHints.FeeProportionalMillionths),
			CLTVExpiryDelta:           uint16(routeHints.CltvExpiryDelta),
		},
	}))

	features := lnwire.EmptyFeatureVector()
	features.RawFeatureVector.Set(lnwire.TLVOnionPayloadOptional)
	features.RawFeatureVector.Set(lnwire.PaymentAddrOptional)

	iopts = append(iopts, zpay32.Features(features))
	iopts = append(iopts, zpay32.CLTVExpiry(144)) // ~1 day
	iopts = append(iopts, zpay32.Expiry(1*time.Hour))

	if opts.Description != "" {
		iopts = append(iopts, zpay32.Description(opts.Description))
	} else {
		// description or description hash must be non-empty, adding a placeholder for now
		iopts = append(iopts, zpay32.Description(""))
	}
	if opts.AmountSat != 0 {
		msat := lnwire.NewMSatFromSatoshis(btcutil.Amount(opts.AmountSat))
		iopts = append(iopts, zpay32.Amount(msat))
	}

	// create the invoice
	invoice, err := zpay32.NewInvoice(
		net.network, paymentHash, time.Now(), iopts...,
	)
	if err != nil {
		return "", err
	}

	// recreate the client identity privkey
	identityKeyPath := fmt.Sprintf("%s/%d", dbInvoice.KeyPath, identityKeyChildIndex)
	identityHDKey, err := userKey.DeriveTo(identityKeyPath)
	if err != nil {
		return "", err
	}
	identityKey, err := identityHDKey.key.ECPrivKey()
	if err != nil {
		return "", fmt.Errorf("can't obtain identity privkey: %w", err)
	}

	// sign the invoice with the identity pubkey
	signer := netann.NewNodeSigner(identityKey)
	bech32, err := invoice.Encode(zpay32.MessageSigner{
		SignCompact: signer.SignDigestCompact,
	})
	if err != nil {
		return "", err
	}

	now := time.Now()
	dbInvoice.State = walletdb.InvoiceStateUsed
	dbInvoice.UsedAt = &now

	err = db.SaveInvoice(dbInvoice)
	if err != nil {
		return "", err
	}

	return bech32, nil
}

func openDB() (*walletdb.DB, error) {
	return walletdb.Open(path.Join(cfg.DataDir, "wallet.db"))
}

func parsePubKey(s string) (*btcec.PublicKey, error) {
	bytes, err := hex.DecodeString(s)
	if err != nil {
		return nil, err
	}
	return btcec.ParsePubKey(bytes, btcec.S256())
}
