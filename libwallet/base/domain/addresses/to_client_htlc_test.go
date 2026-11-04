package addresses

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/platform/test/testbtcscript"
)

func TestToClientHTLCOutput_Address(t *testing.T) {
	const (
		// Fixed vectors shared with the backend transaction scheme cross tests.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"
		timelock           = int64(10)

		// Public key of the 32-byte private key 0x04 repeated.
		revocationPubKeyHex = "03462779ad4aad39514614751a71085f2f10e1c7a593e4e030efb5b8721ce55b0b"

		toClientHTLCAddress = "bcrt1pdcg0y3ws2e9pmcpszqwc8534669q543knvjz9hl4v5wdvwd5tzqqxq9dlt"
	)

	rawRevocationPubKey, err := hex.DecodeString(revocationPubKeyHex)
	require.NoError(t, err)
	serverRevocationPubKey, err := btcec.ParsePubKey(rawRevocationPubKey)
	require.NoError(t, err)
	firstPaymentHash := sha256.Sum256([]byte("preimage-1"))
	secondPaymentHash := sha256.Sum256([]byte("preimage-2"))
	setSpendDelaysForTest(t, timelock)

	output, err := NewToClientHTLCOutput(
		publicKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey),
		addressPath,
		serverRevocationPubKey,
		[][]byte{firstPaymentHash[:], secondPaymentHash[:]},
	)
	require.NoError(t, err)

	got, err := output.Address()
	require.NoError(t, err)

	require.Equal(t, toClientHTLCAddress, got.Address())
	require.Equal(t, ToClientHTLC, got.Version())
	require.Equal(t, addressPath, got.DerivationPath())
}

func TestToClientHTLCOutput_RejectsUncoveredDerivationPath(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	_, err := NewToClientHTLCOutput(
		fixtureBaseKeyTriple(t),
		"m/schema:1'/recovery:1'/contacts:2/0",
		testbtcscript.PrivKeyFromByte(0x04).PubKey(),
		[][]byte{paymentHash[:]},
	)
	require.Error(t, err)
}

func TestToClientHTLCOutput_RejectsMorePaymentHashesThanMaxPayments(t *testing.T) {
	paymentHashes := make([][]byte, MaxPayments+1)
	for i := range paymentHashes {
		paymentHashes[i] = make([]byte, sha256.Size)
	}
	const wantPanic = "at most 255 payment hashes are allowed, got 256"

	require.PanicsWithError(t, wantPanic, func() {
		_, _ = NewToClientHTLCOutput(
			fixtureBaseKeyTriple(t),
			fixturePath,
			testbtcscript.PrivKeyFromByte(0x04).PubKey(),
			paymentHashes,
		)
	})
	require.PanicsWithError(t, wantPanic, func() {
		_, _ = LongestToClientHTLCPreimageWitness(MaxPayments + 1)
	})
}

// TestToClientHTLCOutput_MetadataOutputData pins the output_data committed to
// in the channel state metadata, which counterparties and watchtowers must
// reproduce: the tap branch hash of the (preimage, revocation) pair.
func TestToClientHTLCOutput_MetadataOutputData(t *testing.T) {
	firstPaymentHash := sha256.Sum256([]byte("preimage-1"))
	secondPaymentHash := sha256.Sum256([]byte("preimage-2"))
	output := newToClientHTLCOutputForTest(
		t,
		newTestHTLCKeys(t),
		[][]byte{firstPaymentHash[:], secondPaymentHash[:]},
	)

	outputData, err := output.MetadataOutputData()
	require.NoError(t, err)

	require.Equal(
		t,
		"24433a8b06ebcdcb6c61f3c128289b43e66dff4275dc53468d2b3d7576b1326a",
		hex.EncodeToString(outputData),
	)
}

// newToClientHTLCOutputForTest builds a ToClientHTLCOutput over keys
// committing to paymentHashes, expiring after the fixture timelock.
func newToClientHTLCOutputForTest(
	t *testing.T,
	keys testHTLCKeys,
	paymentHashes [][]byte,
) *ToClientHTLCOutput {
	t.Helper()

	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToClientHTLCOutput(
		htlcKeysTriple(t, keys), fixturePath, keys.revocation.PubKey(), paymentHashes,
	)
	require.NoError(t, err)
	return output
}

// A pre-signed sweep input stays valid when the watchtower adds another input to
// the sweep, so the client signs each revocable output once instead of one sweep
// per combination of them, which is why we use SIGHASH_ANYONECANPAY.
func TestToClientHTLCOutput_RevocationSurvivesExtraInput(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newToClientHTLCOutputForTest(t, keys, [][]byte{paymentHash[:]})

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
	longestWitness, err := LongestToClientHTLCRevocationWitness()
	require.NoError(t, err)
	require.LessOrEqual(t, sweepTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// ComputeRevocationFullSignature refuses a revocation private key that doesn't
// match the output's revocation public key.
func TestToClientHTLCOutput_RevocationSpendRejectsMismatchedKey(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newToClientHTLCOutputForTest(t, keys, [][]byte{paymentHash[:]})

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

func TestToClientHTLCOutput_RevocationSpend(t *testing.T) {
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
			output := newToClientHTLCOutputForTest(t, keys, [][]byte{paymentHash[:]})

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
			claimedOutput := newToClientHTLCOutputForTest(t,
				claimedKeys, [][]byte{paymentHash[:]},
			)

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
			longestWitness, err := LongestToClientHTLCRevocationWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				penaltyTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

func TestToClientHTLCOutput_Preimage(t *testing.T) {
	firstPreimage := bytes.Repeat([]byte{0xaa}, 32)
	firstPaymentHash := sha256.Sum256(firstPreimage)
	secondPreimage := bytes.Repeat([]byte{0xbb}, 32)
	secondPaymentHash := sha256.Sum256(secondPreimage)
	wrongPreimage := bytes.Repeat([]byte{0xcc}, 32)

	// A 31-byte preimage whose hash the output commits to, isolating the
	// OP_SIZE check: the SHA256 comparison alone would pass.
	shortPreimage := firstPreimage[:31]
	shortPreimageHash := sha256.Sum256(shortPreimage)

	maxPreimages := make([][]byte, MaxPayments)
	maxPaymentHashes := make([][]byte, MaxPayments)
	for i := range maxPreimages {
		maxPreimages[i] = bytes.Repeat([]byte{byte(i)}, 32)
		paymentHash := sha256.Sum256(maxPreimages[i])
		maxPaymentHashes[i] = paymentHash[:]
	}

	tests := []struct {
		name             string
		paymentHashes    [][]byte
		witnessPreimages [][]byte
		wantErr          bool
	}{
		{
			name:             "claim with the payment preimage",
			paymentHashes:    [][]byte{firstPaymentHash[:]},
			witnessPreimages: [][]byte{firstPreimage},
			wantErr:          false,
		},
		{
			name:             "claim with two payment preimages",
			paymentHashes:    [][]byte{firstPaymentHash[:], secondPaymentHash[:]},
			witnessPreimages: [][]byte{firstPreimage, secondPreimage},
			wantErr:          false,
		},
		{
			name:             "claim with MaxPayments payment preimages",
			paymentHashes:    maxPaymentHashes,
			witnessPreimages: maxPreimages,
			wantErr:          false,
		},
		{
			name:             "claim with swapped preimages fails",
			paymentHashes:    [][]byte{firstPaymentHash[:], secondPaymentHash[:]},
			witnessPreimages: [][]byte{secondPreimage, firstPreimage},
			wantErr:          true,
		},
		{
			name:             "claim with a wrong preimage fails",
			paymentHashes:    [][]byte{firstPaymentHash[:]},
			witnessPreimages: [][]byte{wrongPreimage},
			wantErr:          true,
		},
		{
			name:             "claim with a short preimage fails",
			paymentHashes:    [][]byte{shortPreimageHash[:]},
			witnessPreimages: [][]byte{shortPreimage},
			wantErr:          true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			keys := newTestHTLCKeys(t)
			output := newToClientHTLCOutputForTest(t, keys, tt.paymentHashes)

			prevTx := fundingTx(t, output, fixtureAmount)

			// The preimage spend has no relative timelock.
			claimTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			SignToClientHTLCPreimageForTest(
				t,
				0,
				claimTx,
				testbtcscript.SigHashesFor(claimTx, prevTx.TxOut[0]),
				output,
				keys.user,
				keys.cosigner,
				keys.lightningServer,
				tt.witnessPreimages,
			)

			vmErr := testbtcscript.VerifyInput(t, claimTx, prevTx.TxOut[0], 0)
			if tt.wantErr {
				require.Error(t, vmErr)
			} else {
				require.NoError(t, vmErr)
			}

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToClientHTLCPreimageWitness(
				len(tt.paymentHashes),
			)
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				claimTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// The user verifies the counterparties' partial signatures for the preimage
// claim before combining them, and rejects a tampered one.
func TestToClientHTLCOutput_VerifyPreimagePartialSignature(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newToClientHTLCOutputForTest(t, keys, [][]byte{paymentHash[:]})

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

func TestToClientHTLCOutput_CollaborativeSpend(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newToClientHTLCOutputForTest(t, keys, [][]byte{paymentHash[:]})

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

	SignToClientHTLCCollaborativeSpendForTest(
		t,
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0]),
		output,
		keys.user,
		keys.cosigner,
		keys.lightningServer,
	)

	require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

	// Fee estimation sizes spends with the longest witness, so a real witness
	// must never exceed it.
	longestWitness, err := LongestToClientHTLCCollaborativeWitness()
	require.NoError(t, err)
	require.LessOrEqual(t, spendTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// SignToClientHTLCPreimageForTest signs a preimage leaf spend the way the
// user completes it: the counterparty partials are computed and verified, then
// combined with the user's and placed in the witness with the preimages.
func SignToClientHTLCPreimageForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToClientHTLCOutput,
	userPrivKey *hd_keys.HDPrivateKey,
	cosignerPrivKey *hd_keys.HDPrivateKey,
	lightningServerPrivKey *hd_keys.HDPrivateKey,
	preimages [][]byte,
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

	// The user verifies the stored counterparty partials before combining.
	err = output.VerifyCosignerPreimagePartialSignature(
		index, tx, sigHashes, cosignerPartialSignature,
		cosignerNonce, userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyLightningServerPreimagePartialSignature(
		index, tx, sigHashes, lightningServerPartialSignature,
		lightningServerNonce, userNonce, cosignerNonce,
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

	witness, err := output.AssemblePreimageWitness(finalSignature, preimages)
	require.NoError(t, err)
	tx.TxIn[index].Witness = witness
}

// SignToClientHTLCCollaborativeSpendForTest signs the collaborative key path
// spend: every participant contributes its partial through the model and the
// lightning server verifies them, combines them and assembles the witness.
func SignToClientHTLCCollaborativeSpendForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToClientHTLCOutput,
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

	// The lightning server verifies the partials before combining them.
	err = output.VerifyUserCollaborativePartialSignature(
		index, tx, sigHashes, userPartialSignature,
		userNonce, cosignerNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyCosignerCollaborativePartialSignature(
		index, tx, sigHashes, cosignerPartialSignature,
		cosignerNonce, userNonce, lightningServerNonce,
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

// Partial signatures for the collaborative spend only verify against the participant
// that produced them, and a tampered one is rejected.
func TestToClientHTLCOutput_VerifyCollaborativePartialSignature(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newTestHTLCKeys(t)
	output := newToClientHTLCOutputForTest(t, keys, [][]byte{paymentHash[:]})

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
	err = output.VerifyCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes, cosignerPartialSignature,
		cosignerNonce, userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyLightningServerCollaborativePartialSignature(
		0, spendTx, sigHashes, lightningServerPartialSignature,
		lightningServerNonce, userNonce, cosignerNonce,
	)
	require.NoError(t, err)

	tamperedPartialSignature := append([]byte{}, cosignerPartialSignature...)
	tamperedPartialSignature[1] ^= 0x01
	err = output.VerifyCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes, tamperedPartialSignature,
		cosignerNonce, userNonce, lightningServerNonce,
	)
	require.ErrorContains(t, err, "the partial signature is invalid")

	err = output.VerifyLightningServerCollaborativePartialSignature(
		0, spendTx, sigHashes, cosignerPartialSignature,
		lightningServerNonce, userNonce, cosignerNonce,
	)
	require.ErrorContains(t, err, "the partial signature is invalid")
}

// Signing a spend through the non-collaborative leaf panics before the
// expiration; the spend is accepted after it.
func TestToClientHTLCOutput_NonCollaborativeSpend(t *testing.T) {
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
			output := newToClientHTLCOutputForTest(t, keys, [][]byte{paymentHash[:]})

			prevTx := fundingTx(t, output, fixtureAmount)
			spendTx := spendingTx(prevTx, tt.sequence)

			compute := func() ([]byte, error) {
				return ComputeToClientHTLCNonCollaborativeFullSignature(
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

			witness, err := AssembleToClientHTLCNonCollaborativeWitness(
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
			longestWitness, err := LongestToClientHTLCNonCollaborativeWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// TestToClientHTLCOutput_CollaborativeCrossCheck pins the fully signed
// collaborative (3-of-3 key path) spending tx, which is fully deterministic:
// keys, transaction and session ids are all fixed. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this exact
// transaction from the same inputs: any update to the pinned values here (keys,
// fixture parameters, session ids, signedTxHex) must be replicated there.
//
// The non-collaborative and revocation spends have no cross check: both sides
// run their MuSig2 session internally, so their signatures are not reproducible.
func TestToClientHTLCOutput_CollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "02000000000101f0d003a1b89c998b6cfff33e198db0ffeb67ba1628652a2af4446fa8f94126bc0000000000ffffffff01b8820100000000002251201367383103ed478810f51f9b410c89242d2e9151ff98f87df990b431b2c4dd15014043968e5fefa27375b04f2a61484121ae54ec3668532b77fcdaeabe55a176bd5cee4b1d16b2925f9aaa56787b25c881ca2ec0fab1a6562c453a91f2fb9d8836a300000000" //nolint:lll

	firstPaymentHash := sha256.Sum256(bytes.Repeat([]byte{0xaa}, 32))
	secondPaymentHash := sha256.Sum256(bytes.Repeat([]byte{0xbb}, 32))
	keys := newTestHTLCKeys(t)
	output := newToClientHTLCOutputForTest(
		t, keys, [][]byte{firstPaymentHash[:], secondPaymentHash[:]},
	)

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

// TestToClientHTLCOutput_PreimageCrossCheck pins the fully signed preimage
// claim over two payment hashes, deterministic for the same reasons as the
// collaborative spend. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this exact
// transaction from the same inputs: any update to the pinned values here (keys,
// fixture parameters, session ids, preimages, signedTxHex) must be replicated
// there.
func TestToClientHTLCOutput_PreimageCrossCheck(t *testing.T) {
	const signedTxHex = "02000000000101f0d003a1b89c998b6cfff33e198db0ffeb67ba1628652a2af4446fa8f94126bc0000000000ffffffff01b8820100000000002251201367383103ed478810f51f9b410c89242d2e9151ff98f87df990b431b2c4dd150520bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb20aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa4044d07ab4a0b95cd13d7b6c6c9ee0d64c5f4c4e1d695aa5aa19404ff8455990e1cdd7200d47d7e43e65482e014ee0026867b3ebc780f0e74bdbe0100f21268760702086a4daa3b2f898e25068339eed0d2f669f95c5722e3513bab3c6fc9b732ba91bad82012088a820e0e77a507412b120f6ede61f62295b1a7b2ff19d3dcc8f7253e51663470c888e8882012088a8204ca14526b2751b640d549ce7caf8ac39438592211a0ec370064d57666a682ad68761c086a4daa3b2f898e25068339eed0d2f669f95c5722e3513bab3c6fc9b732ba91ba2c6f306d5256446782ce9a341f9e1d7d01af0caaaeb8aa23134368b17643f9501c69a79b9424c877ff0d749ed1f47075c65b190fb4b7103a7d294686d5131fc00000000" //nolint:lll

	firstPreimage := bytes.Repeat([]byte{0xaa}, 32)
	firstPaymentHash := sha256.Sum256(firstPreimage)
	secondPreimage := bytes.Repeat([]byte{0xbb}, 32)
	secondPaymentHash := sha256.Sum256(secondPreimage)
	keys := newTestHTLCKeys(t)
	output := newToClientHTLCOutputForTest(
		t, keys, [][]byte{firstPaymentHash[:], secondPaymentHash[:]},
	)

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

	spendTx.TxIn[0].Witness, err = output.AssemblePreimageWitness(
		finalSignature, [][]byte{firstPreimage, secondPreimage},
	)
	require.NoError(t, err)

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}
