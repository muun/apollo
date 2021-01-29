package addresses

import (
	"crypto/sha256"
	"fmt"

	"github.com/btcsuite/btcd/chaincfg"

	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcutil"
	"github.com/btcsuite/btcutil/hdkeychain"
)

func CreateAddressV3(userKey, muunKey *hdkeychain.ExtendedKey, path string, network *chaincfg.Params) (*WalletAddress, error) {

	redeemScript, err := CreateRedeemScriptV3(userKey, muunKey, network)
	if err != nil {
		return nil, err
	}

	address, err := btcutil.NewAddressScriptHash(redeemScript, network)
	if err != nil {
		return nil, err
	}

	return &WalletAddress{
		address:        address.EncodeAddress(),
		version:        V3,
		derivationPath: path,
	}, nil
}

func CreateRedeemScriptV3(userKey, muunKey *hdkeychain.ExtendedKey, network *chaincfg.Params) ([]byte, error) {
	witnessScript, err := CreateWitnessScriptV3(userKey, muunKey, network)
	if err != nil {
		return nil, fmt.Errorf("failed to generate redeem script v3: %w", err)
	}

	return createNonNativeSegwitRedeemScript(witnessScript)
}

func CreateWitnessScriptV3(userKey, muunKey *hdkeychain.ExtendedKey, network *chaincfg.Params) ([]byte, error) {
	// createMultisigRedeemScript creates a valid script for both V2 and V3 schemes
	return createMultisigRedeemScript(userKey, muunKey, network)
}

func createNonNativeSegwitRedeemScript(witnessScript []byte) ([]byte, error) {
	witnessScriptHash := sha256.Sum256(witnessScript)

	builder := txscript.NewScriptBuilder()
	builder.AddInt64(0)
	builder.AddData(witnessScriptHash[:])

	return builder.Script()
}
