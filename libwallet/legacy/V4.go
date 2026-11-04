package libwallet

import (
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/legacy/addresses"
)

// CreateAddressV4 returns a P2WSH MuunAddress from a user HD-pubkey and a cosigner HD-pubkey.
func CreateAddressV4(userKey, cosignerKey *HDPublicKey) (MuunAddress, error) {
	return addresses.CreateAddressV4(
		&userKey.key,
		&cosignerKey.key,
		userKey.Path,
		userKey.Network.network,
	)
}

type coinV4 struct {
	Network           *chaincfg.Params
	OutPoint          wire.OutPoint
	KeyPath           string
	Amount            btcutil.Amount
	CosignerSignature []byte
}

func (c *coinV4) SignInput(
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

	witnessScript, err := createWitnessScriptV4(userKey.PublicKey(), cosignerKey)
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

func (c *coinV4) FullySignInput(
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

func (c *coinV4) signature(
	index int,
	tx *wire.MsgTx,
	userKey *HDPublicKey,
	cosignerKey *HDPublicKey,
	signingKey *HDPrivateKey,
) ([]byte, error) {

	witnessScript, err := createWitnessScriptV4(userKey, cosignerKey)
	if err != nil {
		return nil, err
	}

	return signNativeSegwitInputV0(
		index, tx, signingKey, witnessScript, c.Amount)
}

func createWitnessScriptV4(userKey, cosignerKey *HDPublicKey) ([]byte, error) {
	return addresses.CreateWitnessScriptV4(&userKey.key, &cosignerKey.key, userKey.Network.network)
}
