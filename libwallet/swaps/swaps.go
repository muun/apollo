package swaps

import (
	"crypto/sha256"
	"fmt"

	"github.com/btcsuite/btcd/chaincfg"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcutil/hdkeychain"
	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/hdpath"
	hash "golang.org/x/crypto/ripemd160" //lint:ignore SA1019 using deprecated hash function for compatibility
)

type SubmarineSwap struct {
	Invoice       string
	Receiver      SubmarineSwapReceiver
	FundingOutput SubmarineSwapFundingOutput
	PreimageInHex string
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
	UserRefundAddress *addresses.WalletAddress

	// v2 only
	ExpirationInBlocks int64
	UserPublicKey      *hdkeychain.ExtendedKey
	MuunPublicKey      *hdkeychain.ExtendedKey
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
	indexes := hdpath.MustParse(path).IndexesFrom(hdpath.MustParse(d.Path))
	for _, index := range indexes {
		var err error
		var modifier uint32
		if index.Hardened {
			modifier = hdkeychain.HardenedKeyStart
		}
		key, err = key.Child(index.Index | modifier)
		if err != nil {
			return nil, err
		}
	}
	return key, nil
}

func (swap *SubmarineSwap) Validate(
	rawInvoice string,
	userPublicKey *KeyDescriptor,
	muunPublicKey *KeyDescriptor,
	originalExpirationInBlocks int64,
	network *chaincfg.Params,
) error {

	version := swap.FundingOutput.ScriptVersion
	switch version {
	case addresses.SubmarineSwapV1:
		return swap.validateV1(rawInvoice, userPublicKey, muunPublicKey, network)
	case addresses.SubmarineSwapV2:
		return swap.validateV2(rawInvoice, userPublicKey, muunPublicKey, originalExpirationInBlocks, network)
	default:
		return fmt.Errorf("unknown swap version %v", version)
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
