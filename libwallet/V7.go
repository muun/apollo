package libwallet

import (
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/addresses"
)

func CreateAddressV7(
	userKey, muunKey, lightningPeerKey *HDPublicKey,
	blocksForExpiration int64,
) (MuunAddress, error) {
	return addresses.CreateAddressV7(
		&userKey.key,
		&muunKey.key,
		&lightningPeerKey.key,
		blocksForExpiration,
		userKey.Path,
		userKey.Network.network,
	)
}

// coinV7 signs a V7 (M3) P2SH-P2WSH input.
type coinV7 struct {
	Network             *chaincfg.Params
	OutPoint            wire.OutPoint
	KeyPath             string
	Amount              btcutil.Amount
	BlocksForExpiration int64
	LightningPeerKey    *HDPublicKey
	MuunSignature       []byte
	PeerSignature       []byte
}

// SignInput adds the user signature and assembles the collaborative (3-of-3) witness. The muun and
// peer signatures must already be present.
func (c *coinV7) SignInput(
	index int,
	tx *wire.MsgTx,
	userKey *HDPrivateKey,
	muunKey *HDPublicKey,
) error {
	derivedUserKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return err
	}

	derivedMuunKey, err := muunKey.DeriveTo(c.KeyPath)
	if err != nil {
		return err
	}

	derivedLightningPeerKey, err := c.LightningPeerKey.DeriveTo(c.KeyPath)
	if err != nil {
		return err
	}

	if len(c.MuunSignature) == 0 {
		return errors.New("muun signature must be present")
	}
	if len(c.PeerSignature) == 0 {
		return errors.New("lightning peer signature must be present")
	}

	userPubKey, err := derivedUserKey.PublicKey().ECPubKey()
	if err != nil {
		return err
	}
	muunPubKey, err := derivedMuunKey.ECPubKey()
	if err != nil {
		return err
	}
	lightningPeerPubKey, err := derivedLightningPeerKey.ECPubKey()
	if err != nil {
		return err
	}

	witnessScript, err := addresses.CreateWitnessScriptV7(
		userPubKey,
		muunPubKey,
		lightningPeerPubKey,
		c.BlocksForExpiration,
	)
	if err != nil {
		return err
	}

	userSignature, err := c.signature(
		index,
		tx,
		derivedUserKey,
		witnessScript,
	)
	if err != nil {
		return err
	}

	// Stack top -> bottom: witnessScript, userSig, muunSig, peerSig.
	tx.TxIn[index].Witness = wire.TxWitness{
		c.PeerSignature,
		c.MuunSignature,
		userSignature,
		witnessScript,
	}

	return nil
}

// FullySignInput signs the non-collaborative (2-of-2 + timelock) path with the user and muun
// private keys, for recovery contexts. The caller must build a version-2 tx whose input nSequence
// encodes the relative timelock, since nSequence is committed to by the signature.
func (c *coinV7) FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error {
	derivedUserKey, err := userKey.DeriveTo(c.KeyPath)
	if err != nil {
		return err
	}

	derivedMuunKey, err := muunKey.DeriveTo(c.KeyPath)
	if err != nil {
		return err
	}

	derivedLightningPeerKey, err := c.LightningPeerKey.DeriveTo(c.KeyPath)
	if err != nil {
		return err
	}

	userPubKey, err := derivedUserKey.PublicKey().ECPubKey()
	if err != nil {
		return err
	}
	muunPubKey, err := derivedMuunKey.PublicKey().ECPubKey()
	if err != nil {
		return err
	}
	lightningPeerPubKey, err := derivedLightningPeerKey.ECPubKey()
	if err != nil {
		return err
	}

	witnessScript, err := addresses.CreateWitnessScriptV7(
		userPubKey,
		muunPubKey,
		lightningPeerPubKey,
		c.BlocksForExpiration,
	)
	if err != nil {
		return err
	}

	userSignature, err := c.signature(
		index,
		tx,
		derivedUserKey,
		witnessScript,
	)
	if err != nil {
		return err
	}

	muunSignature, err := c.signature(
		index,
		tx,
		derivedMuunKey,
		witnessScript,
	)
	if err != nil {
		return err
	}

	// Stack top -> bottom: witnessScript, userSig, muunSig, <empty>.
	tx.TxIn[index].Witness = wire.TxWitness{
		[]byte{},
		muunSignature,
		userSignature,
		witnessScript,
	}

	return nil
}

func (c *coinV7) signature(
	index int,
	tx *wire.MsgTx,
	signingKey *HDPrivateKey,
	witnessScript []byte,
) ([]byte, error) {

	redeemScript, err := addresses.CreateRedeemScriptV7(witnessScript)
	if err != nil {
		return nil, err
	}

	return signNonNativeSegwitInputV0(
		index, tx, signingKey, redeemScript, witnessScript, c.Amount)
}
