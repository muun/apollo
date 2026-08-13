package addresses

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/btcsuitew/btcutilw"
	"github.com/muun/libwallet/musig"
)

// CreateAddressV9 returns a native taproot (P2TR) address for `to_client`
//
//   - collaborative spend: taproot key spend path (MuSig2(user + muun + lightningPeer))
//   - non-collaborative spend: the only script in the Tap Tree consisting of
//     user + muun + older(blocksForExpiration)
func CreateAddressV9(
	userKey, muunKey, lightningPeerKey *hdkeychain.ExtendedKey,
	blocksForExpiration int64,
	path string,
	network *chaincfg.Params,
) (*WalletAddress, error) {
	userEcPubKey, err := userKey.ECPubKey()
	if err != nil {
		return nil, errors.Errorf("get user public key: %w", err)
	}

	muunEcPubKey, err := muunKey.ECPubKey()
	if err != nil {
		return nil, errors.Errorf("get muun public key: %w", err)
	}

	lightningPeerEcPubKey, err := lightningPeerKey.ECPubKey()
	if err != nil {
		return nil, errors.Errorf("get lightning peer public key: %w", err)
	}

	nonCollaborativeScript, err := CreateNonCollaborativeScriptV9(
		userEcPubKey,
		muunEcPubKey,
		blocksForExpiration,
	)
	if err != nil {
		return nil, err
	}

	// Single-leaf script tree: the root hash is the leaf's TapHash.
	scriptRootHash := txscript.NewBaseTapLeaf(nonCollaborativeScript).TapHash()

	outputKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{userEcPubKey, muunEcPubKey, lightningPeerEcPubKey},
		musig.TapScriptTweak(scriptRootHash[:]),
	)
	if err != nil {
		return nil, errors.Errorf("aggregate output key: %w", err)
	}

	address, err := btcutilw.NewAddressTaprootKey(
		schnorr.SerializePubKey(outputKey.FinalKey), network,
	)
	if err != nil {
		return nil, err
	}

	return &WalletAddress{
		address:        address.EncodeAddress(),
		version:        V9,
		derivationPath: path,
	}, nil
}

// CreateInternalKeyV9 returns the taproot public key without the tweak.
func CreateInternalKeyV9(
	userPubKey, muunPubKey, lightningPeerPubKey *btcec.PublicKey,
) (*btcec.PublicKey, error) {
	aggregate, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{userPubKey, muunPubKey, lightningPeerPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return nil, errors.Errorf("aggregate internal key: %w", err)
	}
	return aggregate.PreTweakedKey, nil
}

// CreateNonCollaborativeScriptV9 builds the single tapleaf script for the non-collaborative path:
//
//	<musig(user, muun)> OP_CHECKSIGVERIFY <blocksForExpiration> OP_CHECKSEQUENCEVERIFY
func CreateNonCollaborativeScriptV9(
	userPubKey, muunPubKey *btcec.PublicKey,
	blocksForExpiration int64,
) ([]byte, error) {
	aggregateKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{userPubKey, muunPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return nil, errors.Errorf("aggregate non-collaborative key: %w", err)
	}

	return txscript.NewScriptBuilder().
		AddData(schnorr.SerializePubKey(aggregateKey.PreTweakedKey)).
		AddOp(txscript.OP_CHECKSIGVERIFY).
		AddInt64(blocksForExpiration).
		AddOp(txscript.OP_CHECKSEQUENCEVERIFY).
		Script()
}
