package libwallet

import (
	"crypto/sha256"

	"github.com/btcsuite/btcutil"

	"github.com/pkg/errors"

	"github.com/btcsuite/btcd/wire"
)

func CreateAddressV4(userKey, muunKey *HDPublicKey) (MuunAddress, error) {

	witnessScript, err := createWitnessScriptV4(userKey, muunKey)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate witness script v4")
	}
	witnessScript256 := sha256.Sum256(witnessScript)

	address, err := btcutil.NewAddressWitnessScriptHash(witnessScript256[:], userKey.Network.network)
	if err != nil {
		return nil, err
	}

	return &muunAddress{
		address:        address.EncodeAddress(),
		version:        addressV4,
		derivationPath: userKey.Path,
	}, nil
}

func createWitnessScriptV4(userKey, muunKey *HDPublicKey) ([]byte, error) {
	// createRedeemScriptV2 creates a valid script for V2, V3 and V4 schemes
	return createRedeemScriptV2(userKey, muunKey)
}

func addUserSignatureInputV4(input Input, index int, tx *wire.MsgTx, privateKey *HDPrivateKey, muunKey *HDPublicKey) (*wire.TxIn, error) {

	if len(input.MuunSignature()) == 0 {
		return nil, errors.Errorf("muun signature must be present")
	}

	witnessScript, err := createWitnessScriptV4(privateKey.PublicKey(), muunKey)
	if err != nil {
		return nil, err
	}

	sig, err := signInputV4(input, index, tx, privateKey.PublicKey(), muunKey, privateKey)
	if err != nil {
		return nil, err
	}

	zeroByteArray := []byte{}

	txInput := tx.TxIn[index]
	txInput.Witness = wire.TxWitness{zeroByteArray, sig, input.MuunSignature(), witnessScript}

	return txInput, nil
}

func signInputV4(input Input, index int, tx *wire.MsgTx, userKey *HDPublicKey, muunKey *HDPublicKey,
	signingKey *HDPrivateKey) ([]byte, error) {

	witnessScript, err := createWitnessScriptV4(userKey, muunKey)
	if err != nil {
		return nil, err
	}

	return signNativeSegwitInput(input, index, tx, signingKey, witnessScript)
}
