package swaps

import (
	"crypto/sha256"
	"fmt"
	"io"
	"log/slog"

	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/go-errors/errors"
	hash "golang.org/x/crypto/ripemd160" //lint:ignore SA1019 using deprecated hash function for compatibility

	walletaddresses "github.com/muun/libwallet/base/domain/addresses"
	"github.com/muun/libwallet/base/domain/cryptography/hdpath"
	"github.com/muun/libwallet/legacy/addresses"
)

type SubmarineSwap struct {
	Invoice       string
	Receiver      SubmarineSwapReceiver
	FundingOutput SubmarineSwapFundingOutput
	PreimageInHex string
}

// Format prints LogValue for every verb.
// Format and LogValue take value receivers, unlike the other methods, so values print
// redacted too, not only pointers.
func (swap SubmarineSwap) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, swap.LogValue().String())
}

// LogValue returns the swap without the preimage. The funding output's refund address and
// extended public keys are left out too.
func (swap SubmarineSwap) LogValue() slog.Value {
	output := swap.FundingOutput
	return slog.GroupValue(
		slog.String("invoice", swap.Invoice),
		slog.String("receiver_alias", swap.Receiver.Alias),
		slog.String("receiver_public_key", swap.Receiver.PublicKey),
		slog.Int64("script_version", output.ScriptVersion),
		slog.String("output_address", output.OutputAddress),
		slog.Int64("output_amount", output.OutputAmount),
		slog.Int("confirmations_needed", output.ConfirmationsNeeded),
		slog.String("server_payment_hash", output.ServerPaymentHashInHex),
		slog.String("server_public_key", output.ServerPublicKeyInHex),
		slog.Int64("user_lock_time", output.UserLockTime),
		slog.Int64("expiration_in_blocks", output.ExpirationInBlocks),
		slog.String("key_path", output.KeyPath),
	)
}

type SubmarineSwapFundingOutput struct {
	ScriptVersion          int64
	OutputAddress          string
	OutputAmount           int64
	ConfirmationsNeeded    int
	ServerPaymentHashInHex string
	ServerPublicKeyInHex   string

	UserLockTime int64 // TODO: not checked in v2?

	// v1 only
	UserRefundAddress *walletaddresses.WalletAddress

	// v2 only
	ExpirationInBlocks int64
	UserPublicKey      *hdkeychain.ExtendedKey
	CosignerPublicKey  *hdkeychain.ExtendedKey
	KeyPath            string
}

type SubmarineSwapReceiver struct {
	Alias     string
	PublicKey string
}

type KeyDescriptor struct {
	Key  *hdkeychain.ExtendedKey
	Path string
}

func (d *KeyDescriptor) DeriveTo(path string) (*hdkeychain.ExtendedKey, error) {
	key := d.Key

	currentPath, err := hdpath.Parse(d.Path)
	if err != nil {
		return nil, errors.Errorf("invalid current key path: %w", err)
	}
	targetPath, err := hdpath.Parse(path)
	if err != nil {
		return nil, errors.Errorf("invalid target key path: %w", err)
	}
	indexes := targetPath.IndexesFrom(currentPath)
	for _, index := range indexes {
		var err error
		var modifier uint32
		if index.Hardened {
			modifier = hdkeychain.HardenedKeyStart
		}
		key, err = key.Derive(index.Index | modifier)
		if err != nil {
			return nil, err
		}
	}
	return key, nil
}

func (swap *SubmarineSwap) Validate(
	rawInvoice string,
	userPublicKey *KeyDescriptor,
	cosignerPublicKey *KeyDescriptor,
	originalExpirationInBlocks int64,
	network *chaincfg.Params,
) error {

	version := swap.FundingOutput.ScriptVersion
	switch version {
	case addresses.SubmarineSwapV1:
		return swap.validateV1(rawInvoice, userPublicKey, cosignerPublicKey, network)
	case addresses.SubmarineSwapV2:
		return swap.validateV2(
			rawInvoice,
			userPublicKey,
			cosignerPublicKey,
			originalExpirationInBlocks,
			network,
		)
	default:
		return errors.Errorf("unknown swap version %v", version)
	}
}

func createNonNativeSegwitRedeemScript(witnessScript []byte) ([]byte, error) {
	witnessScriptHash := sha256.Sum256(witnessScript)

	builder := txscript.NewScriptBuilder()
	builder.AddInt64(0)
	builder.AddData(witnessScriptHash[:])

	return builder.Script()
}

func ripemd160(data []byte) []byte {
	hasher := hash.New()
	_, err := hasher.Write(data)
	if err != nil {
		panic("failed to hash")
	}

	return hasher.Sum([]byte{})
}
