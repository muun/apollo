package addresses

import (
	"crypto/sha256"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/go-errors/errors"

	walletaddresses "github.com/muun/libwallet/base/domain/addresses"
)

func CreateAddressV3(
	userKey, cosignerKey *hdkeychain.ExtendedKey,
	path string,
	network *chaincfg.Params,
) (*walletaddresses.WalletAddress, error) {

	redeemScript, err := CreateRedeemScriptV3(userKey, cosignerKey, network)
	if err != nil {
		return nil, err
	}

	address, err := btcutil.NewAddressScriptHash(redeemScript, network)
	if err != nil {
		return nil, err
	}

	return walletaddresses.New(V3, path, address.EncodeAddress()), nil
}

func CreateRedeemScriptV3(
	userKey, cosignerKey *hdkeychain.ExtendedKey,
	network *chaincfg.Params,
) ([]byte, error) {
	witnessScript, err := CreateWitnessScriptV3(userKey, cosignerKey, network)
	if err != nil {
		return nil, errors.Errorf("failed to generate redeem script v3: %w", err)
	}

	return createNonNativeSegwitRedeemScript(witnessScript)
}

func CreateWitnessScriptV3(
	userKey, cosignerKey *hdkeychain.ExtendedKey,
	network *chaincfg.Params,
) ([]byte, error) {
	// createMultisigRedeemScript creates a valid script for both V2 and V3 schemes
	return createMultisigRedeemScript(userKey, cosignerKey, network)
}

func createNonNativeSegwitRedeemScript(witnessScript []byte) ([]byte, error) {
	witnessScriptHash := sha256.Sum256(witnessScript)

	builder := txscript.NewScriptBuilder()
	builder.AddInt64(0)
	builder.AddData(witnessScriptHash[:])

	return builder.Script()
}
