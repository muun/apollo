package libwallet

import (
	"encoding/hex"
	"fmt"

	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/btcsuitew/txscriptw"
	"github.com/muun/libwallet/musig"

	"github.com/btcsuite/btcd/btcec"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btcutil"
)

// CreateAddressV5 returns a P2TR MuunAddress using Musig with the signing and cosigning keys.
func CreateAddressV5(userKey, muunKey *HDPublicKey) (MuunAddress, error) {
	return addresses.CreateAddressV5(&userKey.key, &muunKey.key, userKey.Path, userKey.Network.network)
}

type coinV5 struct {
	Network        *chaincfg.Params
	OutPoint       wire.OutPoint
	KeyPath        string
	Amount         btcutil.Amount
	UserSessionId  [32]byte
	MuunPubNonce   [66]byte
	MuunPartialSig [32]byte
	SigHashes      *txscriptw.TaprootSigHashes
}

func (c *coinV5) SignInput(index int, tx *wire.MsgTx, userKey *HDPrivateKey, muunKey *HDPublicKey) error {
	derivedUserKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive user private key: %w", err)
	}

	derivedMuunKey, err := muunKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive muun public key: %w", err)
	}

	userEcPriv, err := derivedUserKey.key.ECPrivKey()
	if err != nil {
		return fmt.Errorf("failed to obtain ECPrivKey from derivedUserKey") // TODO: necessary handling?
	}

	muunEcPub, err := derivedMuunKey.key.ECPubKey()
	if err != nil {
		return fmt.Errorf("failed to obtain ECPubKey from derivedMuunKey") // TODO: necessary handling?
	}

	sigHash, err := txscriptw.CalcTaprootSigHash(tx, c.SigHashes, index, txscript.SigHashAll)
	if err != nil {
		return fmt.Errorf("failed to create sigHash: %w", err)
	}
	var toSign [32]byte
	copy(toSign[:], sigHash)

	return c.signSecondWith(index, tx, userEcPriv, muunEcPub, c.UserSessionId, toSign)
}

func (c *coinV5) FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error {
	derivedUserKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive user private key: %w", err)
	}

	derivedMuunKey, err := muunKey.DeriveTo(c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to derive muun private key: %w", err)
	}

	userEcPriv, err := derivedUserKey.key.ECPrivKey()
	if err != nil {
		return fmt.Errorf("failed to obtain ECPrivKey from derivedUserKey") // TODO: necessary handling?
	}

	muunEcPriv, err := derivedMuunKey.key.ECPrivKey()
	if err != nil {
		return fmt.Errorf("failed to obtain ECPrivKey from derivedMuunKey") // TODO: necessary handling?
	}

	sigHash, err := txscriptw.CalcTaprootSigHash(tx, c.SigHashes, index, txscript.SigHashAll)
	if err != nil {
		return fmt.Errorf("failed to create sigHash: %w", err)
	}
	var toSign [32]byte
	copy(toSign[:], sigHash)

	userPubNonce := musig.GeneratePubNonce(c.UserSessionId)

	err = c.signFirstWith(index, tx, userEcPriv.PubKey(), muunEcPriv, userPubNonce, toSign)
	if err != nil {
		return err
	}

	return c.signSecondWith(index, tx, userEcPriv, muunEcPriv.PubKey(), c.UserSessionId, toSign)
}

func (c *coinV5) signFirstWith(
	index int,
	tx *wire.MsgTx,
	userPub *btcec.PublicKey,
	muunPriv *btcec.PrivateKey,
	userPubNonce [66]byte,
	toSign [32]byte,
) error {

	// NOTE:
	// This will only be called in a recovery context, where both private keys are provided by the
	// user. We call the variables below "muunSessionId" and "muunPubNonce" to follow convention,
	// but Muun servers play no role in this code path and both are locally generated.
	muunSessionId := musig.RandomSessionId()
	muunPubNonce := musig.GeneratePubNonce(muunSessionId)

	muunPartialSig, err := musig.ComputeMuunPartialSignature(
		toSign,
		userPub,
		muunPriv,
		userPubNonce,
		muunSessionId,
		nil,
	)
	if err != nil {
		return fmt.Errorf("failed to add first signature: %w", err)
	}

	c.MuunPubNonce = muunPubNonce
	c.MuunPartialSig = muunPartialSig

	return nil
}

func (c *coinV5) signSecondWith(
	index int,
	tx *wire.MsgTx,
	userPriv *btcec.PrivateKey,
	muunPub *btcec.PublicKey,
	userSessionId [32]byte,
	toSign [32]byte,
) error {

	rawCombinedSig, err := musig.AddUserSignatureAndCombine(
		toSign,
		userPriv,
		muunPub,
		c.MuunPartialSig,
		c.MuunPubNonce,
		userSessionId,
		nil,
	)
	if err != nil {
		return fmt.Errorf("failed to add second signature and combine: %w", err)
	}

	sig := append(rawCombinedSig[:], byte(txscript.SigHashAll))

	tx.TxIn[index].Witness = wire.TxWitness{sig}
	return nil
}

type MusigNonces struct {
	sessionIds [][32]byte
	publicNonces [][66]byte
}

func (m *MusigNonces) GetPubnonceHex(index int) string {
	return hex.EncodeToString(m.publicNonces[index][:])
}

func GenerateMusigNonces(count int) *MusigNonces {
	sessionIds := make([][32]byte, 0)
	publicNonces := make([][66]byte, 0)

	for i := 0; i < count; i += 1 {
		sessionIds = append(sessionIds, musig.RandomSessionId())
		publicNonces = append(publicNonces, musig.GeneratePubNonce(sessionIds[i]))
	}

	return &MusigNonces{
		sessionIds,
		publicNonces,
	}
}