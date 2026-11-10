package libwallet

import (
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/musig"
	"github.com/muun/libwallet/legacy/addresses"
	"github.com/muun/libwallet/legacy/btcsuitew/txscriptw"
	"github.com/muun/libwallet/platform/secret"
)

// CreateAddressV5 returns a P2TR MuunAddress using Musig with the signing and cosigning keys.
func CreateAddressV5(userKey, cosignerKey *HDPublicKey) (MuunAddress, error) {
	return addresses.CreateAddressV5(
		&userKey.key,
		&cosignerKey.key,
		userKey.Path,
		userKey.Network.network,
	)
}

type coinV5 struct {
	Network            *chaincfg.Params
	OutPoint           wire.OutPoint
	KeyPath            string
	Amount             btcutil.Amount
	UserSessionID      secret.Bytes[[32]byte]
	CosignerPubNonce   [66]byte
	CosignerPartialSig [32]byte
	SigHashes          *txscriptw.TaprootSigHashes
}

func (c *coinV5) SignInput(
	index int,
	tx *wire.MsgTx,
	userKey *HDPrivateKey,
	cosignerKey *HDPublicKey,
) error {
	derivedUserKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Errorf("failed to derive user private key: %w", err)
	}

	derivedCosignerKey, err := cosignerKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Errorf("failed to derive cosigner public key: %w", err)
	}

	userEcPriv, err := derivedUserKey.key.ECPrivKey()
	if err != nil {
		return errors.Errorf("failed to obtain ECPrivKey from derivedUserKey: %w", err)
	}

	cosignerEcPub, err := derivedCosignerKey.key.ECPubKey()
	if err != nil {
		return errors.Errorf("failed to obtain ECPubKey from derivedCosignerKey: %w", err)
	}

	sigHash, err := txscriptw.CalcTaprootSigHash(tx, c.SigHashes, index, txscript.SigHashAll)
	if err != nil {
		return errors.Errorf("failed to create sigHash: %w", err)
	}
	var toSign [32]byte
	copy(toSign[:], sigHash)

	return c.signSecondWith(index, tx, userEcPriv, cosignerEcPub, c.UserSessionID.Reveal(), toSign)
}

func (c *coinV5) FullySignInput(
	index int,
	tx *wire.MsgTx,
	userKey, cosignerKey *HDPrivateKey,
) error {
	derivedUserKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Errorf("failed to derive user private key: %w", err)
	}

	derivedCosignerKey, err := cosignerKey.DeriveTo(c.KeyPath)
	if err != nil {
		return errors.Errorf("failed to derive cosigner private key: %w", err)
	}

	userEcPriv, err := derivedUserKey.key.ECPrivKey()
	if err != nil {
		return errors.Errorf("failed to obtain ECPrivKey from derivedUserKey: %w", err)
	}

	cosignerEcPriv, err := derivedCosignerKey.key.ECPrivKey()
	if err != nil {
		return errors.Errorf("failed to obtain ECPrivKey from derivedCosignerKey: %w", err)
	}

	sigHash, err := txscriptw.CalcTaprootSigHash(tx, c.SigHashes, index, txscript.SigHashAll)
	if err != nil {
		return errors.Errorf("failed to create sigHash: %w", err)
	}
	var toSign [32]byte
	copy(toSign[:], sigHash)

	userSessionID := c.UserSessionID.Reveal()
	userPubNonce, err := musig.MuSig2GenerateNonce(
		musig.Musig2v040Muun,
		userSessionID[:],
		nil,
	)
	if err != nil {
		return err
	}

	err = c.signFirstWith(
		userEcPriv.PubKey(),
		cosignerEcPriv,
		userPubNonce.PubNonce,
		toSign,
	)
	if err != nil {
		return err
	}

	return c.signSecondWith(index, tx, userEcPriv, cosignerEcPriv.PubKey(), userSessionID, toSign)
}

// Format prints LogValue for every verb.
// Format and LogValue take value receivers, unlike the other methods, so values print
// redacted too, not only pointers.
func (c coinV5) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, c.LogValue().String())
}

// LogValue returns the coin without the MuSig2 session ID.
func (c coinV5) LogValue() slog.Value {
	return slog.GroupValue(
		slog.String("out_point", c.OutPoint.String()),
		slog.String("key_path", c.KeyPath),
		slog.Int64("amount_sat", int64(c.Amount)),
		slog.String("cosigner_pub_nonce", hex.EncodeToString(c.CosignerPubNonce[:])),
		slog.String("cosigner_partial_sig", hex.EncodeToString(c.CosignerPartialSig[:])),
	)
}

func (c *coinV5) signFirstWith(
	userPub *btcec.PublicKey,
	cosignerPriv *btcec.PrivateKey,
	userPubNonce [66]byte,
	toSign [32]byte,
) error {

	// NOTE:
	// This will only be called in a recovery context, where both private keys are provided by the
	// user. We call the variables below "cosignerSessionID" and "cosignerPubNonce" to follow convention,
	// but Muun servers play no role in this code path and both are locally generated.
	cosignerSessionID := musig.RandomSessionID()
	cosignerPubNonce, err := musig.MuSig2GenerateNonce(
		musig.Musig2v040Muun,
		cosignerSessionID[:],
		cosignerPriv.PubKey().SerializeCompressed(),
	)
	if err != nil {
		return errors.Errorf("failed to generate nonce: %w", err)
	}

	cosignerPartialSig, err := musig.ComputeCosignerPartialSignature( //nolint:staticcheck // V5 keeps the deprecated flow
		musig.Musig2v040Muun,
		toSign[:],
		userPub.SerializeCompressed(),
		cosignerPriv.Serialize(),
		userPubNonce[:],
		cosignerSessionID[:],
		musig.KeySpendOnlyTweak(),
	)
	if err != nil {
		return errors.Errorf("failed to add first signature: %w", err)
	}

	copy(c.CosignerPubNonce[:], cosignerPubNonce.PubNonce[0:66])
	copy(c.CosignerPartialSig[:], cosignerPartialSig[0:32])

	return nil
}

func (c *coinV5) signSecondWith(
	index int,
	tx *wire.MsgTx,
	userPriv *btcec.PrivateKey,
	cosignerPub *btcec.PublicKey,
	userSessionID [32]byte,
	toSign [32]byte,
) error {

	rawCombinedSig, err := musig.ComputeUserPartialSignature( //nolint:staticcheck // V5 keeps the deprecated flow
		musig.Musig2v040Muun,
		toSign[:],
		userPriv.Serialize(),
		cosignerPub.SerializeCompressed(),
		c.CosignerPartialSig[:],
		c.CosignerPubNonce[:],
		userSessionID[:],
		musig.KeySpendOnlyTweak(),
	)
	if err != nil {
		return errors.Errorf("failed to add second signature and combine: %w", err)
	}

	sig := append(rawCombinedSig[:], byte(txscript.SigHashAll))

	tx.TxIn[index].Witness = wire.TxWitness{sig}
	return nil
}
