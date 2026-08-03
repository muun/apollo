package addresses

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/go-errors/errors"
)

// CreateAddressV7 returns a wrapped-segwit (P2SH-P2WSH) address for `to_client`
// (user AND muun AND lightningPeer)                       // collaborative spend
// OR (user AND muun AND older(blocksForExpiration))       // non-collaborative spend
func CreateAddressV7(
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

	witnessScript, err := CreateWitnessScriptV7(
		userEcPubKey,
		muunEcPubKey,
		lightningPeerEcPubKey,
		blocksForExpiration,
	)
	if err != nil {
		return nil, err
	}

	redeemScript, err := CreateRedeemScriptV7(witnessScript)
	if err != nil {
		return nil, err
	}

	address, err := btcutil.NewAddressScriptHash(redeemScript, network)
	if err != nil {
		return nil, err
	}

	return &WalletAddress{
		address:        address.EncodeAddress(),
		version:        V7,
		derivationPath: path,
	}, nil
}

// CreateRedeemScriptV7 builds the P2SH redeem script (the P2WSH program) from the witness script.
func CreateRedeemScriptV7(witnessScript []byte) ([]byte, error) {
	// Wrap the P2WSH program (OP_0 <sha256(witnessScript)>) in P2SH for legacy compatibility.
	return createNonNativeSegwitRedeemScript(witnessScript)
}

// CreateWitnessScriptV7 builds the P2WSH witness script for the M3 policy from the public keys.
//
// Two spending paths:
//  1. collaborative path: user + muun + lightningPeer
//  2. non-collaborative path: user + muun + a RELATIVE timelock of blocksForExpiration blocks
func CreateWitnessScriptV7(
	userPubKey, muunPubKey, lightningPeerPubKey *btcec.PublicKey,
	blocksForExpiration int64,
) ([]byte, error) {
	builder := txscript.NewScriptBuilder()

	// User key, required in BOTH paths.
	builder.AddData(userPubKey.SerializeCompressed()).
		AddOp(txscript.OP_CHECKSIGVERIFY)

	// Muun key, required in BOTH paths.
	builder.AddData(muunPubKey.SerializeCompressed()).
		AddOp(txscript.OP_CHECKSIGVERIFY)

	// The lightning peer's signature doubles as the branch selector:
	//   - collaborative path: a valid peer signature leaves true and skips the NOTIF branch.
	//   - non-collaborative path: an EMPTY peer signature makes the timelock branch run.
	builder.AddData(lightningPeerPubKey.SerializeCompressed()).
		AddOp(txscript.OP_CHECKSIG).
		AddOp(txscript.OP_IFDUP).
		AddOp(txscript.OP_NOTIF).
		// CHECKSEQUENCEVERIFY leaves its argument (blocksForExpiration, truthy) on the stack, so
		// no OP_DROP or explicit truthy push is needed.
		AddInt64(blocksForExpiration).
		AddOp(txscript.OP_CHECKSEQUENCEVERIFY).
		AddOp(txscript.OP_ENDIF)

	return builder.Script()
}
