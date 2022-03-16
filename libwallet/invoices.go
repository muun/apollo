package libwallet

import (
	"bytes"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"math/rand"
	"path"
	"time"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
	"github.com/lightningnetwork/lnd/lnwire"
	"github.com/lightningnetwork/lnd/netann"
	"github.com/lightningnetwork/lnd/zpay32"

	"github.com/muun/libwallet/hdpath"
	"github.com/muun/libwallet/walletdb"
)

const MaxUnusedSecrets = 5

const (
	identityKeyChildIndex          = 0
	htlcKeyChildIndex              = 1
	encryptedMetadataKeyChildIndex = 3
)

// InvoiceSecrets represents a bundle of secrets required to generate invoices
// from the client. These secrets must be registered with the remote server
// and persisted in the client database before use.
type InvoiceSecrets struct {
	preimage      []byte
	paymentSecret []byte
	keyPath       string
	PaymentHash   []byte
	IdentityKey   *HDPublicKey
	UserHtlcKey   *HDPublicKey
	MuunHtlcKey   *HDPublicKey
	ShortChanId   int64
}

// RouteHints is a struct returned by the remote server containing the data
// necessary for constructing an invoice locally.
type RouteHints struct {
	Pubkey                    string
	FeeBaseMsat               int64
	FeeProportionalMillionths int64
	CltvExpiryDelta           int32
}

type OperationMetadata struct {
	Invoice     string `json:"invoice,omitempty"`
	LnurlSender string `json:"lnurlSender,omitempty"`
}

// InvoiceOptions defines additional options that can be configured when
// creating a new invoice.
type InvoiceOptions struct {
	Description string
	AmountSat   int64 // deprecated
	AmountMSat  int64
	Metadata    *OperationMetadata
}

// InvoiceSecretsList is a wrapper around an InvoiceSecrets slice to be
// able to pass through the gomobile bridge.
type InvoiceSecretsList struct {
	secrets []*InvoiceSecrets
}

// Length returns the number of secrets in the list.
func (l *InvoiceSecretsList) Length() int {
	return len(l.secrets)
}

// Get returns the secret at the given index.
func (l *InvoiceSecretsList) Get(i int) *InvoiceSecrets {
	return l.secrets[i]
}

// GenerateInvoiceSecrets returns a slice of new secrets to register with
// the remote server. Once registered, those invoices should be stored with
// the PersistInvoiceSecrets method.
func GenerateInvoiceSecrets(userKey, muunKey *HDPublicKey) (*InvoiceSecretsList, error) {

	var secrets []*InvoiceSecrets

	db, err := openDB()
	if err != nil {
		return nil, err
	}
	defer db.Close()

	unused, err := db.CountUnusedInvoices()
	if err != nil {
		return nil, err
	}

	if unused >= MaxUnusedSecrets {
		return &InvoiceSecretsList{make([]*InvoiceSecrets, 0)}, nil
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

		keyPath := hdpath.MustParse("m/schema:1'/recovery:1'/invoices:4").Child(l1).Child(l2)

		identityKeyPath := keyPath.Child(identityKeyChildIndex)

		identityKey, err := userKey.DeriveTo(identityKeyPath.String())
		if err != nil {
			return nil, err
		}

		htlcKeyPath := keyPath.Child(htlcKeyChildIndex)

		userHtlcKey, err := userKey.DeriveTo(htlcKeyPath.String())
		if err != nil {
			return nil, err
		}
		muunHtlcKey, err := muunKey.DeriveTo(htlcKeyPath.String())
		if err != nil {
			return nil, err
		}

		shortChanId := binary.LittleEndian.Uint64(randomBytes(8)) | (1 << 63)

		secrets = append(secrets, &InvoiceSecrets{
			preimage:      preimage,
			paymentSecret: paymentSecret,
			keyPath:       keyPath.String(),
			PaymentHash:   paymentHash,
			IdentityKey:   identityKey,
			UserHtlcKey:   userHtlcKey,
			MuunHtlcKey:   muunHtlcKey,
			ShortChanId:   int64(shortChanId),
		})
	}

	// TODO: cleanup used secrets

	return &InvoiceSecretsList{secrets}, nil
}

// PersistInvoiceSecrets stores secrets registered with the remote server
// in the device local database. These secrets can be used to craft new
// Lightning invoices.
func PersistInvoiceSecrets(list *InvoiceSecretsList) error {
	db, err := openDB()
	if err != nil {
		return err
	}
	defer db.Close()

	for _, s := range list.secrets {
		db.CreateInvoice(&walletdb.Invoice{
			Preimage:      s.preimage,
			PaymentHash:   s.PaymentHash,
			PaymentSecret: s.paymentSecret,
			KeyPath:       s.keyPath,
			ShortChanId:   uint64(s.ShortChanId),
			State:         walletdb.InvoiceStateRegistered,
		})
	}
	return nil
}

type InvoiceBuilder struct {
	net         *Network
	userKey     *HDPrivateKey
	routeHints  []*RouteHints
	description string
	amountMSat  lnwire.MilliSatoshi
	metadata    *OperationMetadata
}

func (i *InvoiceBuilder) Description(description string) *InvoiceBuilder {
	i.description = description
	return i
}

func (i *InvoiceBuilder) AmountMSat(amountMSat int64) *InvoiceBuilder {
	i.amountMSat = lnwire.MilliSatoshi(amountMSat)
	return i
}

func (i *InvoiceBuilder) AmountSat(amountSat int64) *InvoiceBuilder {
	i.amountMSat = lnwire.NewMSatFromSatoshis(btcutil.Amount(amountSat))
	return i
}

func (i *InvoiceBuilder) Metadata(metadata *OperationMetadata) *InvoiceBuilder {
	i.metadata = metadata
	return i
}

func (i *InvoiceBuilder) Network(net *Network) *InvoiceBuilder {
	i.net = net
	return i
}

func (i *InvoiceBuilder) UserKey(userKey *HDPrivateKey) *InvoiceBuilder {
	i.userKey = userKey
	return i
}

func (i *InvoiceBuilder) AddRouteHints(routeHints *RouteHints) *InvoiceBuilder {
	i.routeHints = append(i.routeHints, routeHints)
	return i
}

func (i *InvoiceBuilder) Build() (string, error) {
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
	if dbInvoice == nil {
		return "", nil
	}

	var paymentHash [32]byte
	copy(paymentHash[:], dbInvoice.PaymentHash)

	var iopts []func(*zpay32.Invoice)
	for _, hint := range i.routeHints {

		nodeID, err := parsePubKey(hint.Pubkey)
		if err != nil {
			return "", fmt.Errorf("can't parse route hint pubkey: %w", err)
		}

		iopts = append(iopts, zpay32.RouteHint([]zpay32.HopHint{
			{
				NodeID:                    nodeID,
				ChannelID:                 dbInvoice.ShortChanId,
				FeeBaseMSat:               uint32(hint.FeeBaseMsat),
				FeeProportionalMillionths: uint32(hint.FeeProportionalMillionths),
				CLTVExpiryDelta:           uint16(hint.CltvExpiryDelta),
			},
		}))
	}

	features := lnwire.EmptyFeatureVector()
	features.RawFeatureVector.Set(lnwire.TLVOnionPayloadOptional)
	features.RawFeatureVector.Set(lnwire.PaymentAddrOptional)

	iopts = append(iopts, zpay32.Features(features))
	iopts = append(iopts, zpay32.CLTVExpiry(72)) // ~1/2 day
	iopts = append(iopts, zpay32.Expiry(24*time.Hour))

	var paymentAddr [32]byte
	copy(paymentAddr[:], dbInvoice.PaymentSecret)
	iopts = append(iopts, zpay32.PaymentAddr(paymentAddr))

	if i.description != "" {
		iopts = append(iopts, zpay32.Description(i.description))
	} else {
		// description or description hash must be non-empty, adding a placeholder for now
		iopts = append(iopts, zpay32.Description(""))
	}
	if i.amountMSat != 0 {
		iopts = append(iopts, zpay32.Amount(i.amountMSat))
	}

	// create the invoice
	invoice, err := zpay32.NewInvoice(
		i.net.network, paymentHash, time.Now(), iopts...,
	)
	if err != nil {
		return "", err
	}

	// recreate the client identity privkey
	parentKeyPath, err := hdpath.Parse(dbInvoice.KeyPath)
	if err != nil {
		return "", err
	}
	identityKeyPath := parentKeyPath.Child(identityKeyChildIndex)
	identityHDKey, err := i.userKey.DeriveTo(identityKeyPath.String())
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

	// This is rounding down. Invoices with amount accept any amount larger
	// but none smaller. So if we have non-integer sats amount, rounding down
	// might accept a few msats less. But, rounding up would always fail the
	// payment.
	if invoice.MilliSat != nil {
		dbInvoice.AmountSat = int64(invoice.MilliSat.ToSatoshis())
	} else {
		dbInvoice.AmountSat = 0
	}
	dbInvoice.State = walletdb.InvoiceStateUsed
	dbInvoice.UsedAt = &now

	var metadata *OperationMetadata
	if i.metadata != nil {
		metadata = i.metadata
		metadata.Invoice = bech32
	} else if i.description != "" {
		metadata = &OperationMetadata{Invoice: bech32}
	}

	if metadata != nil {
		var buf bytes.Buffer
		err := json.NewEncoder(&buf).Encode(metadata)
		if err != nil {
			return "", fmt.Errorf("failed to encode metadata json: %w", err)
		}
		// encryption key is derived at 3/x/y with x and y random indexes
		key, err := deriveMetadataEncryptionKey(i.userKey)
		if err != nil {
			return "", fmt.Errorf("failed to derive encryption key: %w", err)
		}
		encryptedMetadata, err := key.Encrypter().Encrypt(buf.Bytes())
		if err != nil {
			return "", fmt.Errorf("failed to encrypt metadata: %w", err)
		}
		dbInvoice.Metadata = encryptedMetadata
	}

	err = db.SaveInvoice(dbInvoice)
	if err != nil {
		return "", err
	}

	return bech32, nil
}

func deriveMetadataEncryptionKey(key *HDPrivateKey) (*HDPrivateKey, error) {
	key, err := key.DerivedAt(encryptedMetadataKeyChildIndex, false)
	if err != nil {
		return nil, err
	}
	key, err = key.DerivedAt(int64(rand.Int()), false)
	if err != nil {
		return nil, err
	}
	return key.DerivedAt(int64(rand.Int()), false)
}

func GetInvoiceMetadata(paymentHash []byte) (string, error) {
	db, err := openDB()
	if err != nil {
		return "", err
	}
	invoice, err := db.FindByPaymentHash(paymentHash)
	if err != nil {
		return "", err
	}
	return invoice.Metadata, nil
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

func verifyTxWitnessSignature(tx *wire.MsgTx, sigHashes *txscript.TxSigHashes, outputIndex int, amount int64, script []byte, sig []byte, signKey *btcec.PublicKey) error {
	sigHash, err := txscript.CalcWitnessSigHash(script, sigHashes, txscript.SigHashAll, tx, outputIndex, amount)
	if err != nil {
		return err
	}
	signature, err := btcec.ParseDERSignature(sig, btcec.S256())
	if err != nil {
		return err
	}
	if !signature.Verify(sigHash, signKey) {
		return errors.New("signature does not verify")
	}
	return nil
}
