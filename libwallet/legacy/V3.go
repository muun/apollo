package libwallet

import (
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/legacy/addresses"
)

func CreateAddressV3(userKey, cosignerKey *HDPublicKey) (MuunAddress, error) {
	return addresses.CreateAddressV3(
		&userKey.key,
		&cosignerKey.key,
		userKey.Path,
		userKey.Network.network,
	)
}

type coinV3 struct {
	Network           *chaincfg.Params
	OutPoint          wire.OutPoint
	KeyPath           string
	Amount            btcutil.Amount
	CosignerSignature []byte
}

func (c *coinV3) SignInput(
	index int,
	tx *wire.MsgTx,
	userKey *HDPrivateKey,
	cosignerKey *HDPublicKey,
) error {

	userKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Errorf("failed to derive user key: %w", err)
	}

	cosignerKey, err = cosignerKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Errorf("failed to derive cosigner key: %w", err)
	}

	if len(c.CosignerSignature) == 0 {
		return errors.New("cosigner signature must be present")
	}

	witnessScript, err := createWitnessScriptV3(userKey.PublicKey(), cosignerKey)
	if err != nil {
		return err
	}

	sig, err := c.signature(index, tx, userKey.PublicKey(), cosignerKey, userKey)
	if err != nil {
		return err
	}

	zeroByteArray := []byte{}

	txInput := tx.TxIn[index]
	txInput.Witness = wire.TxWitness{zeroByteArray, sig, c.CosignerSignature, witnessScript}

	return nil
}

func (c *coinV3) FullySignInput(
	index int,
	tx *wire.MsgTx,
	userKey, cosignerKey *HDPrivateKey,
) error {

	derivedUserKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Errorf("failed to derive user key: %w", err)
	}

	derivedCosignerKey, err := cosignerKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Errorf("failed to derive cosigner key: %w", err)
	}

	cosignerSignature, err := c.signature(
		index,
		tx,
		derivedUserKey.PublicKey(),
		derivedCosignerKey.PublicKey(),
		derivedCosignerKey,
	)
	if err != nil {
		return err
	}
	c.CosignerSignature = cosignerSignature
	return c.SignInput(index, tx, userKey, cosignerKey.PublicKey())
}

func createRedeemScriptV3(userKey, cosignerKey *HDPublicKey) ([]byte, error) {
	return addresses.CreateRedeemScriptV3(&userKey.key, &cosignerKey.key, userKey.Network.network)
}

func createWitnessScriptV3(userKey, cosignerKey *HDPublicKey) ([]byte, error) {
	return addresses.CreateWitnessScriptV3(&userKey.key, &cosignerKey.key, userKey.Network.network)
}

func (c *coinV3) signature(
	index int,
	tx *wire.MsgTx,
	userKey *HDPublicKey,
	cosignerKey *HDPublicKey,
	signingKey *HDPrivateKey,
) ([]byte, error) {

	witnessScript, err := createWitnessScriptV3(userKey, cosignerKey)
	if err != nil {
		return nil, err
	}

	redeemScript, err := createRedeemScriptV3(userKey, cosignerKey)
	if err != nil {
		return nil, errors.Errorf("failed to build reedem script for signing: %w", err)
	}

	return signNonNativeSegwitInputV0(
		index, tx, signingKey, redeemScript, witnessScript, c.Amount)
}
