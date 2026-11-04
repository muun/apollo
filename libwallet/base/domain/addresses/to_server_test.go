package addresses

import (
	"bytes"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/base/domain/cryptography/musig"
	"github.com/muun/libwallet/platform/test/testbtcscript"
)

func TestToServerOutput_Address(t *testing.T) {
	const (
		// Fixed vectors shared with the backend transaction scheme cross tests.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"
		timelock           = int64(10)

		// Public key of the 32-byte private key 0x04 repeated.
		revocationPubKeyHex = "03462779ad4aad39514614751a71085f2f10e1c7a593e4e030efb5b8721ce55b0b"

		toServerAddress = "bcrt1phrkrdy98zwkn639cemvcpm2jtd3sarwara8n2m3vmk7rd57aexps2u6d00"
	)

	rawRevocationPubKey, err := hex.DecodeString(revocationPubKeyHex)
	require.NoError(t, err)
	serverRevocationPubKey, err := btcec.ParsePubKey(rawRevocationPubKey)
	require.NoError(t, err)
	setSpendDelaysForTest(t, timelock)

	output, err := NewToServerOutput(
		publicKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey),
		addressPath,
		serverRevocationPubKey,
	)
	require.NoError(t, err)

	got, err := output.Address()
	require.NoError(t, err)

	require.Equal(t, toServerAddress, got.Address())
	require.Equal(t, ToServer, got.Version())
	require.Equal(t, addressPath, got.DerivationPath())
}

func TestToServerOutput_RejectsUncoveredDerivationPath(t *testing.T) {
	_, err := NewToServerOutput(
		fixtureBaseKeyTriple(t),
		"m/schema:1'/recovery:1'/contacts:2/0",
		testbtcscript.PrivKeyFromByte(0x04).PubKey(),
	)
	require.Error(t, err)
}

// fixtureUserPrivKey returns the user private key at the schema base path.
func fixtureUserPrivKey(t *testing.T) *hd_keys.HDPrivateKey {
	t.Helper()

	master := hd_keys.NewMasterHDPrivateKeyFromBytes(
		bytes.Repeat([]byte{0x01}, 32), make([]byte, 32), &chaincfg.RegressionNetParams,
	)
	derived, err := master.DeriveTo(fixtureBasePath)
	require.NoError(t, err)
	return derived
}

// newFixtureToServerOutput builds a ToServerOutput over the fixture keys,
// with the given revocation key.
func newFixtureToServerOutput(
	t *testing.T,
	serverRevocationPubKey *btcec.PublicKey,
) *ToServerOutput {
	t.Helper()

	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToServerOutput(
		fixtureBaseKeyTriple(t), fixturePath, serverRevocationPubKey,
	)
	require.NoError(t, err)
	return output
}

// A pre-signed sweep input stays valid when the watchtower adds another input to
// the sweep, so the client signs each revocable output once instead of one sweep
// per combination of them, which is why we use SIGHASH_ANYONECANPAY.
func TestToServerOutput_RevocationSurvivesExtraInput(t *testing.T) {
	userPrivKey := fixtureUserPrivKey(t)
	revocationPrivKey := testbtcscript.PrivKeyFromByte(0x04)
	output := newFixtureToServerOutput(t, revocationPrivKey.PubKey())

	prevTx := fundingTx(t, output, fixtureAmount)

	// The sweep has no relative timelock: the revocation path is not delayed.
	sweepTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

	prevOuts := txscript.NewMultiPrevOutFetcher(map[wire.OutPoint]*wire.TxOut{
		sweepTx.TxIn[0].PreviousOutPoint: prevTx.TxOut[0],
	})

	finalSignature, err := output.ComputeRevocationFullSignature(
		0,
		sweepTx,
		txscript.NewTxSigHashes(sweepTx, prevOuts),
		prevOuts,
		userPrivKey,
		revocationPrivKey,
	)
	require.NoError(t, err)
	sweepTx.TxIn[0].Witness, err = output.AssembleRevocationWitness(finalSignature)
	require.NoError(t, err)

	require.NoError(t, testbtcscript.VerifyInputWithFetcher(t, sweepTx, prevOuts, 0))

	// The watchtower appends another revocable output it found on chain. The
	// signature above committed to its own input only, so it still verifies.
	extraFundingTx := fundingTx(t, output, fixtureAmount-1)
	sweepTx.AddTxIn(&wire.TxIn{
		PreviousOutPoint: wire.OutPoint{Hash: extraFundingTx.TxHash(), Index: 0},
		Sequence:         wire.MaxTxInSequenceNum,
	})
	prevOuts.AddPrevOut(sweepTx.TxIn[1].PreviousOutPoint, extraFundingTx.TxOut[0])

	require.NoError(t, testbtcscript.VerifyInputWithFetcher(t, sweepTx, prevOuts, 0))

	// Fee estimation sizes spends with the longest witness, so a real witness
	// must never exceed it.
	longestWitness, err := LongestToServerRevocationWitness()
	require.NoError(t, err)
	require.LessOrEqual(t, sweepTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// ComputeRevocationFullSignature refuses a revocation private key that doesn't
// match the output's revocation public key.
func TestToServerOutput_RevocationSpendRejectsMismatchedKey(t *testing.T) {
	userPrivKey := fixtureUserPrivKey(t)
	output := newFixtureToServerOutput(t, testbtcscript.PrivKeyFromByte(0x04).PubKey())

	prevTx := fundingTx(t, output, fixtureAmount)
	penaltyTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	prevOuts := txscript.NewMultiPrevOutFetcher(map[wire.OutPoint]*wire.TxOut{
		penaltyTx.TxIn[0].PreviousOutPoint: prevTx.TxOut[0],
	})

	_, err := output.ComputeRevocationFullSignature(
		0,
		penaltyTx,
		txscript.NewTxSigHashes(penaltyTx, prevOuts),
		prevOuts,
		userPrivKey,
		testbtcscript.PrivKeyFromByte(0x05),
	)
	require.ErrorContains(t, err, "the private key does not match the revocation public key")
}

func TestToServerOutput_RevocationSpend(t *testing.T) {
	tests := []struct {
		name string
		// claimedRevocationPrivKeyFill builds the revocation private key the
		// user claims was revealed: only the one matching the funded output
		// verifies.
		claimedRevocationPrivKeyFill byte
		wantErr                      bool
	}{
		{
			name:                         "penalty with the revealed revocation key",
			claimedRevocationPrivKeyFill: 0x04,
			wantErr:                      false,
		},
		{
			name:                         "penalty with a wrong revocation key fails",
			claimedRevocationPrivKeyFill: 0x05,
			wantErr:                      true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			userPrivKey := fixtureUserPrivKey(t)
			revocationPrivKey := testbtcscript.PrivKeyFromByte(0x04)
			output := newFixtureToServerOutput(t, revocationPrivKey.PubKey())

			prevTx := fundingTx(t, output, fixtureAmount)

			// The revocation spend has no relative timelock.
			penaltyTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			prevOuts := txscript.NewMultiPrevOutFetcher(map[wire.OutPoint]*wire.TxOut{
				penaltyTx.TxIn[0].PreviousOutPoint: prevTx.TxOut[0],
			})

			// The user signs with the revocation key they claim was revealed,
			// over an output committing to it.
			claimedRevocationPrivKey := testbtcscript.PrivKeyFromByte(
				tt.claimedRevocationPrivKeyFill,
			)
			claimedOutput := newFixtureToServerOutput(t, claimedRevocationPrivKey.PubKey())

			finalSignature, err := claimedOutput.ComputeRevocationFullSignature(
				0,
				penaltyTx,
				txscript.NewTxSigHashes(penaltyTx, prevOuts),
				prevOuts,
				userPrivKey,
				claimedRevocationPrivKey,
			)
			require.NoError(t, err)
			penaltyTx.TxIn[0].Witness, err = claimedOutput.AssembleRevocationWitness(
				finalSignature,
			)
			require.NoError(t, err)

			vmErr := testbtcscript.VerifyInputWithFetcher(t, penaltyTx, prevOuts, 0)
			if tt.wantErr {
				require.Error(t, vmErr)
			} else {
				require.NoError(t, vmErr)
			}

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToServerRevocationWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				penaltyTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// TestToServerOutput_NonCollaborativeCrossCheck pins the fully signed
// non-collaborative (delayed lightning server claim) spending tx, which is
// fully deterministic: keys and transaction are fixed and the Schnorr nonce is
// derived from the private key and the message alone. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this exact
// transaction from the same inputs: any update to the pinned values here
// (keys, fixture parameters, signedTxHex) must be replicated there.
//
// The revocation spend has no cross check: both sides run its MuSig2 session
// internally, so its signature is not reproducible.
func TestToServerOutput_NonCollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "020000000001013c10f6fbf33d479349ebe4bce165d00258db11110c0f07e674a6c3ca7b6545c000000000009000000001b882010000000000225120ca297df315904f6ac49f6752c88be5c773f7f11a54ccd53f36ed88a8fc297f1803409660eb7f3f094c012e0ef8e65907d1ba2af00aa1d7eabaf22214f6c97a02c2c7cb1547d6c71a130927762e981256b01e3e9d65428d68fc53aed531b0a767c74e262026c608c1e23df7d0006f0d0f1fc0928a7b235fc7321ddafb12b70710e6da48f3ad029000b221c021f55580ba271c35d5ff20ad00f10ca95f6ab8585bee4d2d5618d55af5e5c41600000000" //nolint:lll

	output := newFixtureToServerOutput(t, testbtcscript.PrivKeyFromByte(0x04).PubKey())
	lightningServerPrivKey, err := deriveSignerECKey(
		fixtureBasePrivateKey(t, 0x03), fixturePath, output.LightningServerPubKey,
	)
	require.NoError(t, err)

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, uint32(fixtureTimelock))
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])

	SignNonCollaborativeForTest(t, 0, spendTx, sigHashes, lightningServerPrivKey, output)

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}

// SignNonCollaborativeForTest assembles the witness for a spend through the delayed
// claim leaf, which the lightning server signs on its own.
func SignNonCollaborativeForTest(
	t *testing.T,
	index int,
	claimTx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	lightningServerPrivKey *btcec.PrivateKey,
	output *ToServerOutput,
) {
	t.Helper()

	tapScriptTree, err := output.tapScriptTree()
	require.NoError(t, err)

	leafProof := tapScriptTree.LeafMerkleProofs[toServerNonCollaborativeLeafIndex]
	nonCollaborativeLeaf := leafProof.TapLeaf
	sigHash, err := txscript.CalcTapscriptSignaturehash(
		sigHashes, txscript.SigHashDefault, claimTx, index, nil, nonCollaborativeLeaf,
	)
	require.NoError(t, err)

	lightningServerSignature, err := musig.SignSchnorr(lightningServerPrivKey.Serialize(), sigHash)
	require.NoError(t, err)

	controlBlock, err := output.leafControlBlock(tapScriptTree, toServerNonCollaborativeLeafIndex)
	require.NoError(t, err)

	claimTx.TxIn[index].Witness = wire.TxWitness{
		lightningServerSignature,
		nonCollaborativeLeaf.Script,
		controlBlock,
	}
}

// publicKeyTripleForTest derives the base58 master private keys to the schema
// base path and returns them as a public key triple.
func publicKeyTripleForTest(
	t *testing.T,
	userKey, cosignerKey, lightningServerKey string,
) *hd_keys.PublicKeyTriple {
	t.Helper()

	publicKeys := make([]*hd_keys.HDPublicKey, 3)
	for i, base58 := range []string{userKey, cosignerKey, lightningServerKey} {
		privKey, err := hd_keys.NewHDPrivateKeyFromString(
			base58, "m", &chaincfg.RegressionNetParams,
		)
		require.NoError(t, err)
		derived, err := privKey.DeriveTo(fixtureBasePath)
		require.NoError(t, err)
		publicKeys[i] = derived.PublicKey()
	}
	return hd_keys.NewPublicKeyTriple(publicKeys[0], publicKeys[1], publicKeys[2])
}
