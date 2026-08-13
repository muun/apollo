package libwallet

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/addresses"
	"github.com/muun/libwallet/musig"
)

func CreateAddressV9(
	userKey, muunKey, lightningPeerKey *HDPublicKey,
	blocksForExpiration int64,
) (MuunAddress, error) {
	return addresses.CreateAddressV9(
		&userKey.key,
		&muunKey.key,
		&lightningPeerKey.key,
		blocksForExpiration,
		userKey.Path,
		userKey.Network.network,
	)
}

// coinV9 signs a V9 (M3) native taproot (P2TR) input.
type coinV9 struct {
	KeyPath                 string
	BlocksForExpiration     int64
	LightningPeerKey        *HDPublicKey
	UserSessionID           [32]byte
	MuunPubNonce            [66]byte
	MuunPartialSig          [32]byte
	LightningPeerPubNonce   [66]byte
	LightningPeerPartialSig [32]byte
	SigHashes               *txscript.TxSigHashes
}

// SignInput adds the user signature and does the final MuSig2 aggregation of the 3-of-3 signature
// user + muun + lightningPeer for the key spending path.
// The muun and lightning-peer public nonces and partial signatures must already be present.
func (c *coinV9) SignInput(
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

	if c.UserSessionID == [32]byte{} {
		return errors.New("UserSessionID must be non empty")
	}
	if c.MuunPubNonce == ([66]byte{}) {
		return errors.New("muun public nonce must be present")
	}
	if c.MuunPartialSig == ([32]byte{}) {
		return errors.New("muun partial signature must be present")
	}
	if c.LightningPeerPubNonce == ([66]byte{}) {
		return errors.New("lightning peer public nonce must be present")
	}
	if c.LightningPeerPartialSig == ([32]byte{}) {
		return errors.New("lightning peer partial signature must be present")
	}

	derivedLightningPeerKey, err := c.LightningPeerKey.DeriveTo(c.KeyPath)
	if err != nil {
		return err
	}

	userEcPriv, err := derivedUserKey.key.ECPrivKey()
	if err != nil {
		return err
	}
	muunEcPub, err := derivedMuunKey.ECPubKey()
	if err != nil {
		return err
	}
	lightningPeerPubKey, err := derivedLightningPeerKey.ECPubKey()
	if err != nil {
		return err
	}
	userEcPub := userEcPriv.PubKey()

	nonCollaborativeLeaf, err := c.nonCollaborativeLeaf(userEcPub, muunEcPub)
	if err != nil {
		return err
	}

	// The output key commits to the script tree, so the key-path signature is over the internal key
	// tweaked by the script root.
	scriptRootHash := nonCollaborativeLeaf.TapHash()

	// Passing nil prevOutFetcher as it's only used on SIGHASH_ANYONECANPAY path
	sigHash, err := txscript.CalcTaprootSignatureHash(
		c.SigHashes,
		txscript.SigHashDefault,
		tx,
		index,
		nil,
	)
	if err != nil {
		return err
	}

	aggregatedSignature, err := musig.ComputeFinalSignature3Of3(
		sigHash,
		userEcPriv.Serialize(),
		muunEcPub.SerializeCompressed(),
		lightningPeerPubKey.SerializeCompressed(),
		c.MuunPubNonce[:],
		c.LightningPeerPubNonce[:],
		c.MuunPartialSig[:],
		c.LightningPeerPartialSig[:],
		c.UserSessionID[:],
		musig.TapScriptTweak(scriptRootHash[:]),
	)
	if err != nil {
		return err
	}

	tx.TxIn[index].Witness = wire.TxWitness{aggregatedSignature}
	return nil
}

// FullySignInput signs the non-collaborative (2-of-2 + timelock) script path with the user and muun
// private keys creating an aggregated signature with musig2.
// The caller must build a version-2 tx whose input nSequence encodes the relative timelock,
// since nSequence is committed to by the signature.
func (c *coinV9) FullySignInput(index int, tx *wire.MsgTx, userKey, muunKey *HDPrivateKey) error {
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

	userEcPriv, err := derivedUserKey.key.ECPrivKey()
	if err != nil {
		return err
	}
	muunEcPriv, err := derivedMuunKey.key.ECPrivKey()
	if err != nil {
		return err
	}
	lightningPeerPubKey, err := derivedLightningPeerKey.ECPubKey()
	if err != nil {
		return err
	}
	userEcPub, muunEcPub := userEcPriv.PubKey(), muunEcPriv.PubKey()

	nonCollaborativeLeaf, err := c.nonCollaborativeLeaf(userEcPub, muunEcPub)
	if err != nil {
		return err
	}

	// Passing nil prevOutFetcher as it's only used on SIGHASH_ANYONECANPAY path
	sigHash, err := txscript.CalcTapscriptSignaturehash(
		c.SigHashes,
		txscript.SigHashDefault,
		tx,
		index,
		nil,
		nonCollaborativeLeaf,
	)
	if err != nil {
		return err
	}

	aggregatedSignature, err := signNonCollaborativeV9(sigHash, userEcPriv, muunEcPriv)
	if err != nil {
		return err
	}

	controlBlock, err := c.nonCollaborativeControlBlock(
		userEcPub,
		muunEcPub,
		lightningPeerPubKey,
		nonCollaborativeLeaf,
	)
	if err != nil {
		return err
	}

	// Witness stack bottom -> top: musig(user, muun), leafScript, controlBlock.
	tx.TxIn[index].Witness = wire.TxWitness{
		aggregatedSignature,
		nonCollaborativeLeaf.Script,
		controlBlock,
	}
	return nil
}

// signNonCollaborativeV9 runs a full MuSig2 2-of-2 session over the script-path sighash.
func signNonCollaborativeV9(
	sigHash []byte,
	userEcPriv, muunEcPriv *btcec.PrivateKey,
) ([]byte, error) {
	return musig.ComputeFullSignature2Of2(sigHash, muunEcPriv, userEcPriv, musig.NoopTweak())
}

// nonCollaborativeLeaf builds the single tapleaf carrying the non-collaborative spending policy.
func (c *coinV9) nonCollaborativeLeaf(
	userPubKey, muunPubKey *btcec.PublicKey,
) (txscript.TapLeaf, error) {
	script, err := addresses.CreateNonCollaborativeScriptV9(
		userPubKey, muunPubKey, c.BlocksForExpiration,
	)
	if err != nil {
		return txscript.TapLeaf{}, err
	}
	return txscript.NewBaseTapLeaf(script), nil
}

// nonCollaborativeControlBlock builds the taproot control block proving the non-collaborative leaf
// belongs to the output's script tree, given the 3-of-3 internal key. The tree has a single leaf,
// so the merkle proof is empty.
func (c *coinV9) nonCollaborativeControlBlock(
	userPubKey, muunPubKey, lightningPeerPubKey *btcec.PublicKey,
	leaf txscript.TapLeaf,
) ([]byte, error) {
	internalKey, err := addresses.CreateInternalKeyV9(userPubKey, muunPubKey, lightningPeerPubKey)
	if err != nil {
		return nil, err
	}

	tapTree := txscript.AssembleTaprootScriptTree(leaf)
	controlBlock := tapTree.LeafMerkleProofs[0].ToControlBlock(internalKey)
	return controlBlock.ToBytes()
}
