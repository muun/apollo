package addresses

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
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

func TestToServerHTLCOutput_Address(t *testing.T) {
	const (
		// Fixed vectors shared with the backend transaction scheme cross tests.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"
		timelock           = int64(10)

		// Public key of the 32-byte private key 0x04 repeated.
		revocationPubKeyHex = "03462779ad4aad39514614751a71085f2f10e1c7a593e4e030efb5b8721ce55b0b"

		toServerHTLCAddress = "bcrt1p9pxdfu7pufse94xpd2ajtuwn8lpqs78jxt9vzk3l9sj3ecrchqtscwphay"
	)

	rawRevocationPubKey, err := hex.DecodeString(revocationPubKeyHex)
	require.NoError(t, err)
	serverRevocationPubKey, err := btcec.ParsePubKey(rawRevocationPubKey)
	require.NoError(t, err)
	paymentHash := sha256.Sum256([]byte("preimage"))
	setSpendDelaysForTest(t, timelock)

	output, err := NewToServerHTLCOutput(
		publicKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey),
		addressPath,
		serverRevocationPubKey,
		paymentHash[:],
	)
	require.NoError(t, err)

	got, err := output.Address()
	require.NoError(t, err)

	require.Equal(t, toServerHTLCAddress, got.Address())
	require.Equal(t, ToServerHTLC, got.Version())
	require.Equal(t, addressPath, got.DerivationPath())
}

func TestToServerHTLCOutput_RejectsUncoveredDerivationPath(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	_, err := NewToServerHTLCOutput(
		fixtureBaseKeyTriple(t),
		"m/schema:1'/recovery:1'/contacts:2/0",
		testbtcscript.PrivKeyFromByte(0x04).PubKey(),
		paymentHash[:],
	)
	require.Error(t, err)
}

// TestToServerHTLCOutput_MetadataOutputData pins the output_data committed to
// in the channel state metadata, which counterparties and watchtowers must
// reproduce: the tap branch hash of the (preimage, revocation) pair.
func TestToServerHTLCOutput_MetadataOutputData(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	output := newHTLCOutputForTest(t, newTestHTLCKeys(t), paymentHash[:])

	outputData, err := output.MetadataOutputData()
	require.NoError(t, err)

	require.Equal(
		t,
		"2c8585f4b30accaea8988b6e9910ebbd208c65c04ede0c3f53fc9030ef3996cb",
		hex.EncodeToString(outputData),
	)
}

// testHTLCKeys holds the participant private keys of one to_server_htlc test
// output: the wallet keys at the schema base path and the revocation key.
type testHTLCKeys struct {
	user            *hd_keys.HDPrivateKey
	cosigner        *hd_keys.HDPrivateKey
	lightningServer *hd_keys.HDPrivateKey
	revocation      *btcec.PrivateKey
}

// newTestHTLCKeys builds the participants from deterministic keys.
func newTestHTLCKeys(t *testing.T) testHTLCKeys {
	t.Helper()

	return testHTLCKeys{
		user:            basePrivKeyForTest(t, 0x01),
		cosigner:        basePrivKeyForTest(t, 0x02),
		lightningServer: basePrivKeyForTest(t, 0x03),
		revocation:      testbtcscript.PrivKeyFromByte(0x04),
	}
}

// basePrivKeyForTest returns the private key of the fill-based master at the
// schema base path.
func basePrivKeyForTest(t *testing.T, fill byte) *hd_keys.HDPrivateKey {
	t.Helper()

	master := hd_keys.NewMasterHDPrivateKeyFromBytes(
		bytes.Repeat([]byte{fill}, 32), make([]byte, 32), &chaincfg.RegressionNetParams,
	)
	derived, err := master.DeriveTo(fixtureBasePath)
	require.NoError(t, err)
	return derived
}

// htlcKeysTriple returns the participants' public keys as a triple.
func htlcKeysTriple(t *testing.T, keys testHTLCKeys) *hd_keys.PublicKeyTriple {
	t.Helper()

	return hd_keys.NewPublicKeyTriple(
		keys.user.PublicKey(), keys.cosigner.PublicKey(), keys.lightningServer.PublicKey(),
	)
}

// newHTLCOutputForTest builds a ToServerHTLCOutput over keys committing to
// paymentHash, expiring after the fixture timelock.
func newHTLCOutputForTest(t *testing.T, keys testHTLCKeys, paymentHash []byte) *ToServerHTLCOutput {
	t.Helper()

	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToServerHTLCOutput(
		htlcKeysTriple(t, keys), fixturePath, keys.revocation.PubKey(), paymentHash,
	)
	require.NoError(t, err)
	return output
}

// A pre-signed sweep input stays valid when the watchtower adds another input to
// the sweep, so the client signs each revocable output once instead of one sweep
// per combination of them, which is why we use SIGHASH_ANYONECANPAY.
func TestToServerHTLCOutput_RevocationSurvivesExtraInput(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newHTLCOutputForTest(t, keys, paymentHash[:])

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
		keys.user,
		keys.revocation,
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
	longestWitness, err := LongestToServerHTLCRevocationWitness()
	require.NoError(t, err)
	require.LessOrEqual(t, sweepTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// ComputeRevocationFullSignature refuses a revocation private key that doesn't
// match the output's revocation public key.
func TestToServerHTLCOutput_RevocationSpendRejectsMismatchedKey(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newHTLCOutputForTest(t, keys, paymentHash[:])

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
		keys.user,
		testbtcscript.PrivKeyFromByte(0x05),
	)
	require.ErrorContains(t, err, "the private key does not match the revocation public key")
}

func TestToServerHTLCOutput_RevocationSpend(t *testing.T) {
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
			paymentHash := sha256.Sum256([]byte("preimage"))
			keys := newTestHTLCKeys(t)
			output := newHTLCOutputForTest(t, keys, paymentHash[:])

			prevTx := fundingTx(t, output, fixtureAmount)

			// The revocation spend has no relative timelock.
			penaltyTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			prevOuts := txscript.NewMultiPrevOutFetcher(map[wire.OutPoint]*wire.TxOut{
				penaltyTx.TxIn[0].PreviousOutPoint: prevTx.TxOut[0],
			})

			// The user signs with the revocation key they claim was revealed,
			// over an output committing to it.
			claimedKeys := keys
			claimedKeys.revocation = testbtcscript.PrivKeyFromByte(
				tt.claimedRevocationPrivKeyFill,
			)
			claimedOutput := newHTLCOutputForTest(t, claimedKeys, paymentHash[:])

			finalSignature, err := claimedOutput.ComputeRevocationFullSignature(
				0,
				penaltyTx,
				txscript.NewTxSigHashes(penaltyTx, prevOuts),
				prevOuts,
				keys.user,
				claimedKeys.revocation,
			)
			require.NoError(t, err)
			witness, err := claimedOutput.AssembleRevocationWitness(finalSignature)
			require.NoError(t, err)
			penaltyTx.TxIn[0].Witness = witness

			vmErr := testbtcscript.VerifyInputWithFetcher(t, penaltyTx, prevOuts, 0)
			if tt.wantErr {
				require.Error(t, vmErr)
			} else {
				require.NoError(t, vmErr)
			}

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToServerHTLCRevocationWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				penaltyTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

func TestToServerHTLCOutput_CollaborativeSpend(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newHTLCOutputForTest(t, keys, paymentHash[:])

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])

	// Distinct deterministic session ids for each signer.
	userSessionID := bytes.Repeat([]byte{0x10}, 32)
	cosignerSessionID := bytes.Repeat([]byte{0x20}, 32)
	lightningServerSessionID := bytes.Repeat([]byte{0x30}, 32)

	userNonce := nonceForTest(t, userSessionID, output.UserPubKey)
	cosignerNonce := nonceForTest(t, cosignerSessionID, output.CosignerPubKey)
	lightningServerNonce := nonceForTest(
		t, lightningServerSessionID, output.LightningServerPubKey,
	)
	allNonces := [][]byte{userNonce, cosignerNonce, lightningServerNonce}

	userPartialSignature, err := output.ComputeUserCollaborativePartialSignature(
		0, spendTx, sigHashes,
		keys.user,
		userSessionID,
		cosignerNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	cosignerPartialSignature, err := output.ComputeCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		keys.cosigner,
		cosignerSessionID,
		userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerCollaborativePartialSignature(
			0, spendTx, sigHashes,
			keys.lightningServer,
			lightningServerSessionID,
			userNonce, cosignerNonce,
		)
	require.NoError(t, err)

	// A partial signature only verifies against the participant that produced it.
	err = output.VerifyUserCollaborativePartialSignature(
		0, spendTx, sigHashes,
		userPartialSignature, userNonce,
		cosignerNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		cosignerPartialSignature, cosignerNonce,
		userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyLightningServerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		lightningServerPartialSignature, lightningServerNonce,
		userNonce, cosignerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyUserCollaborativePartialSignature(
		0, spendTx, sigHashes,
		cosignerPartialSignature, userNonce,
		cosignerNonce, lightningServerNonce,
	)
	require.ErrorContains(t, err, "the partial signature is invalid")

	finalSignature, err := output.CombineCollaborativeSignatures(
		0, spendTx, sigHashes,
		allNonces,
		[][]byte{
			userPartialSignature,
			cosignerPartialSignature,
			lightningServerPartialSignature,
		},
	)
	require.NoError(t, err)

	spendTx.TxIn[0].Witness, err = output.AssembleCollaborativeWitness(finalSignature)
	require.NoError(t, err)
	require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

	// Fee estimation sizes spends with the longest witness, so a real witness
	// must never exceed it.
	longestWitness, err := LongestToServerHTLCCollaborativeWitness()
	require.NoError(t, err)
	require.LessOrEqual(t, spendTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

func TestToServerHTLCOutput_Preimage(t *testing.T) {
	preimage := bytes.Repeat([]byte{0xaa}, 32)
	paymentHash := sha256.Sum256(preimage)
	wrongPreimage := bytes.Repeat([]byte{0xbb}, 32)

	// A 31-byte preimage whose hash the output commits to, isolating the
	// OP_SIZE check: the SHA256 comparison alone would pass.
	shortPreimage := preimage[:31]
	shortPreimageHash := sha256.Sum256(shortPreimage)

	tests := []struct {
		name            string
		paymentHash     []byte
		witnessPreimage []byte
		wantErr         bool
	}{
		{
			name:            "claim with the payment preimage",
			paymentHash:     paymentHash[:],
			witnessPreimage: preimage,
			wantErr:         false,
		},
		{
			name:            "claim with a wrong preimage fails",
			paymentHash:     paymentHash[:],
			witnessPreimage: wrongPreimage,
			wantErr:         true,
		},
		{
			name:            "claim with a short preimage fails",
			paymentHash:     shortPreimageHash[:],
			witnessPreimage: shortPreimage,
			wantErr:         true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			keys := newTestHTLCKeys(t)
			output := newHTLCOutputForTest(t, keys, tt.paymentHash)

			prevTx := fundingTx(t, output, fixtureAmount)

			// The preimage spend has no relative timelock.
			claimTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			SignToServerHTLCPreimageForTest(
				t,
				0,
				claimTx,
				testbtcscript.SigHashesFor(claimTx, prevTx.TxOut[0]),
				output,
				keys.user,
				keys.cosigner,
				keys.lightningServer,
				tt.witnessPreimage,
			)

			vmErr := testbtcscript.VerifyInput(t, claimTx, prevTx.TxOut[0], 0)
			if tt.wantErr {
				require.Error(t, vmErr)
			} else {
				require.NoError(t, vmErr)
			}

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToServerHTLCPreimageWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				claimTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// nonceForTest derives a signer's public nonce from a session id.
func nonceForTest(t *testing.T, sessionID []byte, signerPubKey *btcec.PublicKey) []byte {
	t.Helper()

	nonce, err := musig.MuSig2GenerateNonce(
		musig.Musig2v100, sessionID, signerPubKey.SerializeCompressed(),
	)
	require.NoError(t, err)
	return nonce.PubNonce[:]
}

// SignToServerHTLCPreimageForTest signs a preimage leaf spend the way the lightning
// server completes it: every participant contributes its partial through the
// model, the server verifies them, combines them and assembles the witness
// once it has the preimage.
func SignToServerHTLCPreimageForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToServerHTLCOutput,
	userPrivKey *hd_keys.HDPrivateKey,
	cosignerPrivKey *hd_keys.HDPrivateKey,
	lightningServerPrivKey *hd_keys.HDPrivateKey,
	preimage []byte,
) {
	t.Helper()

	userSessionID := bytes.Repeat([]byte{0x40}, 32)
	cosignerSessionID := bytes.Repeat([]byte{0x50}, 32)
	lightningServerSessionID := bytes.Repeat([]byte{0x60}, 32)

	userNonce := nonceForTest(t, userSessionID, output.UserPubKey)
	cosignerNonce := nonceForTest(t, cosignerSessionID, output.CosignerPubKey)
	lightningServerNonce := nonceForTest(
		t, lightningServerSessionID, output.LightningServerPubKey,
	)

	userPartialSignature, err := output.ComputeUserPreimagePartialSignature(
		index, tx, sigHashes,
		userPrivKey,
		userSessionID,
		cosignerNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	cosignerPartialSignature, err := output.ComputeCosignerPreimagePartialSignature(
		index, tx, sigHashes,
		cosignerPrivKey,
		cosignerSessionID,
		userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerPreimagePartialSignature(
			index, tx, sigHashes,
			lightningServerPrivKey,
			lightningServerSessionID,
			userNonce, cosignerNonce,
		)
	require.NoError(t, err)

	// The lightning server verifies the stored partials before combining them.
	err = output.VerifyUserPreimagePartialSignature(
		index, tx, sigHashes, userPartialSignature,
		userNonce, cosignerNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyCosignerPreimagePartialSignature(
		index, tx, sigHashes, cosignerPartialSignature,
		cosignerNonce, userNonce, lightningServerNonce,
	)
	require.NoError(t, err)

	finalSignature, err := output.CombinePreimageSignatures(
		index, tx, sigHashes,
		[][]byte{userNonce, cosignerNonce, lightningServerNonce},
		[][]byte{
			userPartialSignature,
			cosignerPartialSignature,
			lightningServerPartialSignature,
		},
	)
	require.NoError(t, err)

	witness, err := output.AssemblePreimageWitness(finalSignature, preimage)
	require.NoError(t, err)
	tx.TxIn[index].Witness = witness
}

// Partial signatures for the preimage claim only verify against the participant
// that produced them, and a tampered one is rejected.
func TestToServerHTLCOutput_VerifyPreimagePartialSignature(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newHTLCOutputForTest(t, keys, paymentHash[:])

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])

	userSessionID := bytes.Repeat([]byte{0x10}, 32)
	cosignerSessionID := bytes.Repeat([]byte{0x20}, 32)
	lightningServerSessionID := bytes.Repeat([]byte{0x30}, 32)

	userNonce := nonceForTest(t, userSessionID, output.UserPubKey)
	cosignerNonce := nonceForTest(t, cosignerSessionID, output.CosignerPubKey)
	lightningServerNonce := nonceForTest(
		t, lightningServerSessionID, output.LightningServerPubKey,
	)

	cosignerPartialSignature, err := output.ComputeCosignerPreimagePartialSignature(
		0, spendTx, sigHashes,
		keys.cosigner,
		cosignerSessionID,
		userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerPreimagePartialSignature(
			0, spendTx, sigHashes,
			keys.lightningServer,
			lightningServerSessionID,
			userNonce, cosignerNonce,
		)
	require.NoError(t, err)

	// A partial signature only verifies against the participant that produced it.
	err = output.VerifyCosignerPreimagePartialSignature(
		0, spendTx, sigHashes, cosignerPartialSignature,
		cosignerNonce, userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyLightningServerPreimagePartialSignature(
		0, spendTx, sigHashes, lightningServerPartialSignature,
		lightningServerNonce, userNonce, cosignerNonce,
	)
	require.NoError(t, err)

	tamperedPartialSignature := append([]byte{}, cosignerPartialSignature...)
	tamperedPartialSignature[1] ^= 0x01
	err = output.VerifyCosignerPreimagePartialSignature(
		0, spendTx, sigHashes, tamperedPartialSignature,
		cosignerNonce, userNonce, lightningServerNonce,
	)
	require.ErrorContains(t, err, "the partial signature is invalid")

	err = output.VerifyLightningServerPreimagePartialSignature(
		0, spendTx, sigHashes, cosignerPartialSignature,
		lightningServerNonce, userNonce, cosignerNonce,
	)
	require.ErrorContains(t, err, "the partial signature is invalid")
}

// SignToServerHTLCCollaborativeSpendForTest fully signs the collaborative key path spend
// with all three private keys locally.
func SignToServerHTLCCollaborativeSpendForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToServerHTLCOutput,
	userPrivKey *hd_keys.HDPrivateKey,
	cosignerPrivKey *hd_keys.HDPrivateKey,
	lightningServerPrivKey *hd_keys.HDPrivateKey,
) {
	t.Helper()

	userSessionID := bytes.Repeat([]byte{0x70}, 32)
	cosignerSessionID := bytes.Repeat([]byte{0x80}, 32)
	lightningServerSessionID := bytes.Repeat([]byte{0x90}, 32)

	userNonce := nonceForTest(t, userSessionID, output.UserPubKey)
	cosignerNonce := nonceForTest(t, cosignerSessionID, output.CosignerPubKey)
	lightningServerNonce := nonceForTest(
		t, lightningServerSessionID, output.LightningServerPubKey,
	)

	userPartialSignature, err := output.ComputeUserCollaborativePartialSignature(
		index, tx, sigHashes,
		userPrivKey,
		userSessionID,
		cosignerNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	cosignerPartialSignature, err := output.ComputeCosignerCollaborativePartialSignature(
		index, tx, sigHashes,
		cosignerPrivKey,
		cosignerSessionID,
		userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerCollaborativePartialSignature(
			index, tx, sigHashes,
			lightningServerPrivKey,
			lightningServerSessionID,
			userNonce, cosignerNonce,
		)
	require.NoError(t, err)

	finalSignature, err := output.CombineCollaborativeSignatures(
		index, tx, sigHashes,
		[][]byte{userNonce, cosignerNonce, lightningServerNonce},
		[][]byte{
			userPartialSignature,
			cosignerPartialSignature,
			lightningServerPartialSignature,
		},
	)
	require.NoError(t, err)

	tx.TxIn[index].Witness, err = output.AssembleCollaborativeWitness(finalSignature)
	require.NoError(t, err)
}

// Signing a spend through the non-collaborative leaf panics before the
// expiration; the spend is accepted after it.
func TestToServerHTLCOutput_NonCollaborativeSpend(t *testing.T) {
	tests := []struct {
		name      string
		sequence  uint32
		wantPanic string
	}{
		{
			name:      "non-collaborative spend after the expiration",
			sequence:  uint32(fixtureTimelock),
			wantPanic: "",
		},
		{
			name:     "signing before the expiration panics",
			sequence: uint32(fixtureTimelock) - 1,
			wantPanic: fmt.Sprintf(
				"input sequence must encode at least %d blocks, got %d",
				fixtureTimelock, fixtureTimelock-1,
			),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			paymentHash := sha256.Sum256([]byte("preimage"))
			keys := newTestHTLCKeys(t)
			output := newHTLCOutputForTest(t, keys, paymentHash[:])

			prevTx := fundingTx(t, output, fixtureAmount)
			spendTx := spendingTx(prevTx, tt.sequence)

			compute := func() ([]byte, error) {
				return ComputeToServerHTLCNonCollaborativeFullSignature(
					0,
					spendTx,
					testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0]),
					keys.user,
					keys.cosigner,
					htlcKeysTriple(t, keys),
					fixturePath,
				)
			}
			if tt.wantPanic != "" {
				require.PanicsWithError(t, tt.wantPanic, func() { _, _ = compute() })
				return
			}
			finalSignature, err := compute()
			require.NoError(t, err)

			// The spender learns the branch hash from the state metadata.
			branchHash, err := output.MetadataOutputData()
			require.NoError(t, err)

			witness, err := AssembleToServerHTLCNonCollaborativeWitness(
				finalSignature,
				htlcKeysTriple(t, keys),
				fixturePath,
				branchHash,
			)
			require.NoError(t, err)
			spendTx.TxIn[0].Witness = witness

			require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToServerHTLCNonCollaborativeWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// TestToServerHTLCOutput_CollaborativeCrossCheck pins the fully signed
// collaborative (3-of-3 key path) spending tx, which is fully deterministic:
// keys, transaction and session ids are all fixed. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this exact
// transaction from the same inputs: any update to the pinned values here (keys,
// fixture parameters, session ids, signedTxHex) must be replicated there.
//
// The non-collaborative and revocation spends have no cross check: both sides
// run their MuSig2 session internally, so their signatures are not reproducible.
func TestToServerHTLCOutput_CollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "02000000000101950892abfa8d5961b9e9724179f5e7a63812d17aed13c090ce90b1eb5d00b9200000000000ffffffff01b882010000000000225120fa067759652beac99510b074dd30b0da46356741c246bac8adaf5118a14436100140b6ab27b585dce5c7f382aaf77f10ae1869e5952bdabf49b911e575e1e32b34178de5484a99d3a18b6c405a865eff8a5911d025ed75af8ff80c7900667c41c60600000000" //nolint:lll

	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newHTLCOutputForTest(t, keys, paymentHash[:])

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])

	userSessionID := bytes.Repeat([]byte{0x10}, 32)
	cosignerSessionID := bytes.Repeat([]byte{0x20}, 32)
	lightningServerSessionID := bytes.Repeat([]byte{0x30}, 32)

	userNonce := nonceForTest(t, userSessionID, output.UserPubKey)
	cosignerNonce := nonceForTest(t, cosignerSessionID, output.CosignerPubKey)
	lightningServerNonce := nonceForTest(
		t, lightningServerSessionID, output.LightningServerPubKey,
	)

	userPartialSignature, err := output.ComputeUserCollaborativePartialSignature(
		0, spendTx, sigHashes,
		keys.user,
		userSessionID,
		cosignerNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	cosignerPartialSignature, err := output.ComputeCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		keys.cosigner,
		cosignerSessionID,
		userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerCollaborativePartialSignature(
			0, spendTx, sigHashes,
			keys.lightningServer,
			lightningServerSessionID,
			userNonce, cosignerNonce,
		)
	require.NoError(t, err)

	finalSignature, err := output.CombineCollaborativeSignatures(
		0, spendTx, sigHashes,
		[][]byte{userNonce, cosignerNonce, lightningServerNonce},
		[][]byte{
			userPartialSignature,
			cosignerPartialSignature,
			lightningServerPartialSignature,
		},
	)
	require.NoError(t, err)

	spendTx.TxIn[0].Witness, err = output.AssembleCollaborativeWitness(finalSignature)
	require.NoError(t, err)

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}

// TestToServerHTLCOutput_PreimageCrossCheck pins the fully signed preimage
// claim, deterministic for the same reasons as the collaborative spend. The
// backend's TransactionSchemeSignedTransactionLibwalletCrossTest must produce
// this exact transaction from the same inputs: any update to the pinned values
// here (keys, fixture parameters, session ids, preimage, signedTxHex) must be
// replicated there.
func TestToServerHTLCOutput_PreimageCrossCheck(t *testing.T) {
	const signedTxHex = "0200000000010186c321d0c920449b74fe72495076c6a10fe185bccb64c6197811be19212d0ba50000000000ffffffff01b882010000000000225120c3baf6367b55f8981472085bac56e44c4f3144b222373db54aedba8a37b1a6450420aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa40d10910e0266904ec67bdee1747ba6289e5196476a33a2135314aee71f7ab88096a02ef5a5d26c35c2feef2ccba9fa81cc6b73df9221920250bbabd1a3db7db84492086a4daa3b2f898e25068339eed0d2f669f95c5722e3513bab3c6fc9b732ba91bad82012088a820e0e77a507412b120f6ede61f62295b1a7b2ff19d3dcc8f7253e51663470c888e8761c186a4daa3b2f898e25068339eed0d2f669f95c5722e3513bab3c6fc9b732ba91ba2c6f306d5256446782ce9a341f9e1d7d01af0caaaeb8aa23134368b17643f9501c69a79b9424c877ff0d749ed1f47075c65b190fb4b7103a7d294686d5131fc00000000" //nolint:lll

	preimage := bytes.Repeat([]byte{0xaa}, 32)
	paymentHash := sha256.Sum256(preimage)
	keys := newTestHTLCKeys(t)
	output := newHTLCOutputForTest(t, keys, paymentHash[:])

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])

	userSessionID := bytes.Repeat([]byte{0x10}, 32)
	cosignerSessionID := bytes.Repeat([]byte{0x20}, 32)
	lightningServerSessionID := bytes.Repeat([]byte{0x30}, 32)

	userNonce := nonceForTest(t, userSessionID, output.UserPubKey)
	cosignerNonce := nonceForTest(t, cosignerSessionID, output.CosignerPubKey)
	lightningServerNonce := nonceForTest(
		t, lightningServerSessionID, output.LightningServerPubKey,
	)

	userPartialSignature, err := output.ComputeUserPreimagePartialSignature(
		0, spendTx, sigHashes,
		keys.user,
		userSessionID,
		cosignerNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	cosignerPartialSignature, err := output.ComputeCosignerPreimagePartialSignature(
		0, spendTx, sigHashes,
		keys.cosigner,
		cosignerSessionID,
		userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerPreimagePartialSignature(
			0, spendTx, sigHashes,
			keys.lightningServer,
			lightningServerSessionID,
			userNonce, cosignerNonce,
		)
	require.NoError(t, err)

	finalSignature, err := output.CombinePreimageSignatures(
		0, spendTx, sigHashes,
		[][]byte{userNonce, cosignerNonce, lightningServerNonce},
		[][]byte{
			userPartialSignature,
			cosignerPartialSignature,
			lightningServerPartialSignature,
		},
	)
	require.NoError(t, err)

	spendTx.TxIn[0].Witness, err = output.AssemblePreimageWitness(finalSignature, preimage)
	require.NoError(t, err)

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}
