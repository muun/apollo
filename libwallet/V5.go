package libwallet

import (
	"fmt"

	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/btcsuitew/txscriptw"
	"github.com/muun/libwallet/musig"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
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
		return fmt.Errorf("failed to obtain ECPrivKey from derivedUserKey: %w", err)
	}

	muunEcPub, err := derivedMuunKey.key.ECPubKey()
	if err != nil {
		return fmt.Errorf("failed to obtain ECPubKey from derivedMuunKey: %w", err)
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
		return fmt.Errorf("failed to obtain ECPrivKey from derivedUserKey: %w", err)
	}

	muunEcPriv, err := derivedMuunKey.key.ECPrivKey()
	if err != nil {
		return fmt.Errorf("failed to obtain ECPrivKey from derivedMuunKey: %w", err)
	}

	sigHash, err := txscriptw.CalcTaprootSigHash(tx, c.SigHashes, index, txscript.SigHashAll)
	if err != nil {
		return fmt.Errorf("failed to create sigHash: %w", err)
	}
	var toSign [32]byte
	copy(toSign[:], sigHash)

	userPubNonce, err := musig.MuSig2GenerateNonce(
		musig.Musig2v040Muun,
		c.UserSessionId[:],
		nil,
	)
	if err != nil {
		return err
	}

	err = c.signFirstWith(index, tx, userEcPriv.PubKey(), muunEcPriv, userPubNonce.PubNonce, toSign)
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
	muunPubNonce, err := musig.MuSig2GenerateNonce(
		musig.Musig2v040Muun,
		muunSessionId[:],
		muunPriv.PubKey().SerializeCompressed(),
	)
	if err != nil {
		return fmt.Errorf("failed to generate nonce: %w", err)
	}

	muunPartialSig, err := musig.ComputeMuunPartialSignature(
		musig.Musig2v040Muun,
		toSign[:],
		userPub.SerializeCompressed(),
		muunPriv.Serialize(),
		userPubNonce[:],
		muunSessionId[:],
		musig.KeySpendOnlyTweak(),
	)
	if err != nil {
		return fmt.Errorf("failed to add first signature: %w", err)
	}

	copy(c.MuunPubNonce[:], muunPubNonce.PubNonce[0:66])
	copy(c.MuunPartialSig[:], muunPartialSig[0:32])

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

	rawCombinedSig, err := musig.ComputeUserPartialSignature(
		musig.Musig2v040Muun,
		toSign[:],
		userPriv.Serialize(),
		muunPub.SerializeCompressed(),
		c.MuunPartialSig[:],
		c.MuunPubNonce[:],
		userSessionId[:],
		musig.KeySpendOnlyTweak(),
	)
	if err != nil {
		return fmt.Errorf("failed to add second signature and combine: %w", err)
	}

	sig := append(rawCombinedSig[:], byte(txscript.SigHashAll))

	tx.TxIn[index].Witness = wire.TxWitness{sig}
	return nil
}
