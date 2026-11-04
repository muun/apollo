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
	"github.com/muun/libwallet/base/domain/cryptography/musig"
	"github.com/muun/libwallet/platform/test/testbtcscript"
)

const fixtureHTLCTimeoutHeight = int64(1_000)

func TestToServerHTLCWithdrawalOutput_Address(t *testing.T) {
	const (
		// Fixed vectors shared with the backend transaction scheme cross tests.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"
		htlcTimeout        = int64(1_000)
		timelock           = int64(10)

		// Public key of the 32-byte private key 0x04 repeated.
		revocationPubKeyHex = "03462779ad4aad39514614751a71085f2f10e1c7a593e4e030efb5b8721ce55b0b"

		withdrawalAddress = "bcrt1p33z6flztrsg9ldzkxfwmy3j68ctzd6l8yzcymnf9fevmfjjzp3lq7syc9r"
	)

	rawRevocationPubKey, err := hex.DecodeString(revocationPubKeyHex)
	require.NoError(t, err)
	clientRevocationPubKey, err := btcec.ParsePubKey(rawRevocationPubKey)
	require.NoError(t, err)
	paymentHash := sha256.Sum256([]byte("preimage"))
	setSpendDelaysForTest(t, timelock)

	output, err := NewToServerHTLCWithdrawalOutput(
		publicKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey),
		addressPath,
		clientRevocationPubKey,
		paymentHash[:],
		htlcTimeout,
	)
	require.NoError(t, err)

	got, err := output.Address()
	require.NoError(t, err)

	require.Equal(t, withdrawalAddress, got.Address())
	require.Equal(t, ToServerHTLCWithdrawal, got.Version())
	require.Equal(t, addressPath, got.DerivationPath())
}

func TestToServerHTLCWithdrawalOutput_RejectsUncoveredDerivationPath(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	_, err := NewToServerHTLCWithdrawalOutput(
		fixtureBaseKeyTriple(t),
		"m/schema:1'/recovery:1'/contacts:2/0",
		testbtcscript.PrivKeyFromByte(0x04).PubKey(),
		paymentHash[:],
		int64(500_000),
	)
	require.Error(t, err)
}

// TestToServerHTLCWithdrawalOutput_MetadataOutputData pins the output_data
// committed to in the channel state metadata, which counterparties and
// watchtowers must reproduce: the tap branch hash of the (preimage,
// revocation) pair followed by HTLCTimeout as 4 little-endian bytes.
func TestToServerHTLCWithdrawalOutput_MetadataOutputData(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	output := newHTLCWithdrawalOutputForTest(t, newWithdrawalKeysForTest(t), paymentHash[:])

	outputData, err := output.MetadataOutputData()
	require.NoError(t, err)

	require.Equal(
		t,
		"7b016af8993a4ecf4d99d9e541fc309b733c660cc916659d80231e102c34d233e8030000",
		hex.EncodeToString(outputData),
	)
}

// withdrawalKeysTriple returns the participants' public keys as a triple.
func withdrawalKeysTriple(t *testing.T, keys withdrawalKeysForTest) *hd_keys.PublicKeyTriple {
	t.Helper()

	return hd_keys.NewPublicKeyTriple(
		keys.user.PublicKey(), keys.cosigner.PublicKey(), keys.lightningServer.PublicKey(),
	)
}

// newHTLCWithdrawalOutputForTest builds a ToServerHTLCWithdrawalOutput over
// keys committing to paymentHash, claimable after the fixture timeout height
// and timelock.
func newHTLCWithdrawalOutputForTest(
	t *testing.T,
	keys withdrawalKeysForTest,
	paymentHash []byte,
) *ToServerHTLCWithdrawalOutput {
	t.Helper()

	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToServerHTLCWithdrawalOutput(
		withdrawalKeysTriple(t, keys),
		fixturePath,
		keys.revocation.PubKey(),
		paymentHash,
		fixtureHTLCTimeoutHeight,
	)
	require.NoError(t, err)
	return output
}

func TestToServerHTLCWithdrawalOutput_CollaborativeSpend(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newWithdrawalKeysForTest(t)
	output := newHTLCWithdrawalOutputForTest(t, keys, paymentHash[:])

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
		cosignerNonce,
		lightningServerNonce,
	)
	require.NoError(t, err)
	cosignerPartialSignature, err := output.ComputeCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		keys.cosigner,
		cosignerSessionID,
		userNonce,
		lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerCollaborativePartialSignature(
			0, spendTx, sigHashes,
			keys.lightningServer,
			lightningServerSessionID,
			userNonce,
			cosignerNonce,
		)
	require.NoError(t, err)

	// The user verifies the stored counterparty partials on receipt, and a
	// mismatched one is rejected.
	err = output.VerifyCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		cosignerPartialSignature, cosignerNonce, userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyLightningServerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		lightningServerPartialSignature, lightningServerNonce, userNonce, cosignerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		lightningServerPartialSignature, cosignerNonce, userNonce, lightningServerNonce,
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
	longestWitness, err := LongestToServerHTLCWithdrawalCollaborativeWitness()
	require.NoError(t, err)
	require.LessOrEqual(t, spendTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// SignToServerHTLCWithdrawalCollaborativeSpendForTest signs the collaborative
// key path spend the way the user completes it: the counterparty partials are
// verified, then combined with the user's and placed in the witness.
func SignToServerHTLCWithdrawalCollaborativeSpendForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToServerHTLCWithdrawalOutput,
	userPrivKey *hd_keys.HDPrivateKey,
	cosignerPrivKey *hd_keys.HDPrivateKey,
	lightningServerPrivKey *hd_keys.HDPrivateKey,
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

	userPartialSignature, err := output.ComputeUserCollaborativePartialSignature(
		index, tx, sigHashes,
		userPrivKey,
		userSessionID,
		cosignerNonce,
		lightningServerNonce,
	)
	require.NoError(t, err)
	cosignerPartialSignature, err := output.ComputeCosignerCollaborativePartialSignature(
		index, tx, sigHashes,
		cosignerPrivKey,
		cosignerSessionID,
		userNonce,
		lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerCollaborativePartialSignature(
			index, tx, sigHashes,
			lightningServerPrivKey,
			lightningServerSessionID,
			userNonce,
			cosignerNonce,
		)
	require.NoError(t, err)

	// The user verifies the stored counterparty partials before combining.
	err = output.VerifyCosignerCollaborativePartialSignature(
		index, tx, sigHashes,
		cosignerPartialSignature, cosignerNonce, userNonce, lightningServerNonce,
	)
	require.NoError(t, err)
	err = output.VerifyLightningServerCollaborativePartialSignature(
		index, tx, sigHashes,
		lightningServerPartialSignature, lightningServerNonce, userNonce, cosignerNonce,
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

func TestToServerHTLCWithdrawalOutput_Preimage(t *testing.T) {
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
			keys := newWithdrawalKeysForTest(t)
			output := newHTLCWithdrawalOutputForTest(t, keys, tt.paymentHash)

			prevTx := fundingTx(t, output, fixtureAmount)

			// The preimage spend has no timelocks.
			claimTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			SignToServerHTLCWithdrawalPreimageForTest(
				t,
				0,
				claimTx,
				testbtcscript.SigHashesFor(claimTx, prevTx.TxOut[0]),
				output,
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
			longestWitness, err := LongestToServerHTLCWithdrawalPreimageWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				claimTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// SignToServerHTLCWithdrawalPreimageForTest assembles the witness for a
// spend through the preimage leaf, which the lightning server signs alone.
func SignToServerHTLCWithdrawalPreimageForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToServerHTLCWithdrawalOutput,
	lightningServerPrivKey *hd_keys.HDPrivateKey,
	preimage []byte,
) {
	t.Helper()

	lightningServerECPrivKey, err := deriveSignerECKey(
		lightningServerPrivKey, output.path, output.LightningServerPubKey,
	)
	require.NoError(t, err)

	tapScriptTree, err := output.tapScriptTree()
	require.NoError(t, err)

	sigHash, err := leafInputDataToSign(
		tapScriptTree, toServerHTLCWithdrawalPreimageLeafIndex, index, tx, sigHashes,
	)
	require.NoError(t, err)

	lightningServerSignature, err := musig.SignSchnorr(
		lightningServerECPrivKey.Serialize(), sigHash,
	)
	require.NoError(t, err)

	controlBlock, err := output.leafControlBlock(
		tapScriptTree, toServerHTLCWithdrawalPreimageLeafIndex,
	)
	require.NoError(t, err)

	tx.TxIn[index].Witness = wire.TxWitness{
		preimage,
		lightningServerSignature,
		tapScriptTree.LeafMerkleProofs[toServerHTLCWithdrawalPreimageLeafIndex].Script,
		controlBlock,
	}
}

// The lightning server's revocation spend only verifies with the revocation key
// the user revealed when revoking the withdrawal.
func TestToServerHTLCWithdrawalOutput_RevocationSpend(t *testing.T) {
	tests := []struct {
		name string
		// claimedRevocationPrivKeyFill builds the revocation private key the
		// lightning server claims was revealed: only the one matching the
		// output verifies.
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
			keys := newWithdrawalKeysForTest(t)
			output := newHTLCWithdrawalOutputForTest(t, keys, paymentHash[:])

			prevTx := fundingTx(t, output, fixtureAmount)

			// The revocation spend has no timelocks.
			penaltyTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			SignToServerHTLCWithdrawalRevocationForTest(
				t,
				0,
				penaltyTx,
				testbtcscript.SigHashesFor(penaltyTx, prevTx.TxOut[0]),
				output,
				keys.lightningServer,
				testbtcscript.PrivKeyFromByte(tt.claimedRevocationPrivKeyFill),
			)

			vmErr := testbtcscript.VerifyInput(t, penaltyTx, prevTx.TxOut[0], 0)
			if tt.wantErr {
				require.Error(t, vmErr)
			} else {
				require.NoError(t, vmErr)
			}

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToServerHTLCWithdrawalRevocationWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				penaltyTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// SignToServerHTLCWithdrawalRevocationForTest assembles the witness for a spend
// through the revocation leaf, which the lightning server signs with the
// revealed revocation key. Both partial signatures of the 2-of-2 MuSig2 are
// computed locally.
func SignToServerHTLCWithdrawalRevocationForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToServerHTLCWithdrawalOutput,
	lightningServerPrivKey *hd_keys.HDPrivateKey,
	revocationPrivKey *btcec.PrivateKey,
) {
	t.Helper()

	lightningServerECPrivKey, err := deriveSignerECKey(
		lightningServerPrivKey, output.path, output.LightningServerPubKey,
	)
	require.NoError(t, err)

	tapScriptTree, err := output.tapScriptTree()
	require.NoError(t, err)

	sigHash, err := leafInputDataToSign(
		tapScriptTree, toServerHTLCWithdrawalRevocationLeafIndex, index, tx, sigHashes,
	)
	require.NoError(t, err)

	signature, err := musig.ComputeFullSignature2Of2(
		sigHash,
		lightningServerECPrivKey,
		revocationPrivKey,
		musig.NoopTweak(),
	)
	require.NoError(t, err)

	controlBlock, err := output.leafControlBlock(
		tapScriptTree, toServerHTLCWithdrawalRevocationLeafIndex,
	)
	require.NoError(t, err)

	tx.TxIn[index].Witness = wire.TxWitness{
		signature,
		tapScriptTree.LeafMerkleProofs[toServerHTLCWithdrawalRevocationLeafIndex].Script,
		controlBlock,
	}
}

// Signing a spend through the non-collaborative leaf panics unless the
// transaction commits to both the HTLC timeout height and the relative delay.
func TestToServerHTLCWithdrawalOutput_NonCollaborativeSpend(t *testing.T) {
	tests := []struct {
		name      string
		lockTime  uint32
		sequence  uint32
		wantPanic string
	}{
		{
			name:      "non-collaborative spend after both timelocks",
			lockTime:  uint32(fixtureHTLCTimeoutHeight),
			sequence:  uint32(fixtureTimelock),
			wantPanic: "",
		},
		{
			name:     "signing below the timeout height panics",
			lockTime: uint32(fixtureHTLCTimeoutHeight) - 1,
			sequence: uint32(fixtureTimelock),
			wantPanic: fmt.Sprintf(
				"nLockTime must be a block height in [%d, 500000000), got %d",
				fixtureHTLCTimeoutHeight, fixtureHTLCTimeoutHeight-1,
			),
		},
		{
			name:     "signing before the delay panics",
			lockTime: uint32(fixtureHTLCTimeoutHeight),
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
			keys := newWithdrawalKeysForTest(t)
			output := newHTLCWithdrawalOutputForTest(t, keys, paymentHash[:])

			prevTx := fundingTx(t, output, fixtureAmount)
			spendTx := spendingTx(prevTx, tt.sequence)
			spendTx.LockTime = tt.lockTime

			compute := func() ([]byte, error) {
				return ComputeToServerHTLCWithdrawalNonCollaborativeFullSignature(
					0,
					spendTx,
					testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0]),
					keys.user,
					keys.cosigner,
					withdrawalKeysTriple(t, keys),
					fixturePath,
					fixtureHTLCTimeoutHeight,
				)
			}
			if tt.wantPanic != "" {
				require.PanicsWithError(t, tt.wantPanic, func() { _, _ = compute() })
				return
			}
			finalSignature, err := compute()
			require.NoError(t, err)

			// The spender learns the branch hash from the state metadata.
			tapScriptTree, err := output.tapScriptTree()
			require.NoError(t, err)
			branchHash := tapScriptTree.
				LeafMerkleProofs[toServerHTLCWithdrawalNonCollaborativeLeafIndex].InclusionProof

			witness, err := AssembleToServerHTLCWithdrawalNonCollaborativeWitness(
				finalSignature,
				withdrawalKeysTriple(t, keys),
				fixturePath,
				fixtureHTLCTimeoutHeight,
				branchHash,
			)
			require.NoError(t, err)
			spendTx.TxIn[0].Witness = witness

			require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToServerHTLCWithdrawalNonCollaborativeWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

func TestToServerHTLCWithdrawalOutput_AssemblerRejectsOutOfRangeTimelock(t *testing.T) {
	require.PanicsWithError(
		t,
		"absolute timelock must be a block height in [1, 499999999], got 0",
		func() {
			_, _ = AssembleToServerHTLCWithdrawalNonCollaborativeWitness(
				make([]byte, 64),
				fixtureBaseKeyTriple(t),
				fixturePath,
				0,
				make([]byte, 32),
			)
		},
	)
}

// TestToServerHTLCWithdrawalOutput_CollaborativeCrossCheck pins the fully
// signed collaborative (3-of-3 key path) spending tx, which is fully
// deterministic: keys, transaction and session ids are all fixed. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this exact
// transaction from the same inputs: any update to the pinned values here (keys,
// fixture parameters, session ids, signedTxHex) must be replicated there.
//
// The non-collaborative and revocation spends have no cross check: both sides
// run their MuSig2 session internally, so their signatures are not reproducible.
func TestToServerHTLCWithdrawalOutput_CollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "0200000000010198082ce3cadf30eaeac01164e4ee18b44345381bed71021a9a23803f8b7c7e3b0000000000ffffffff01b8820100000000002251200ad9f456373c79398a56a0d19f19caa4a341d004fc07552a9b27a48f448a798701408d2fad584de1504cfef08b5f02839ae63f4a73e9fa4041d059485861896c5b05cdbbb171133dedcc07fbb70df433121ac7226d17b7e9fe536a9fab0a4d3db5f700000000" //nolint:lll

	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newWithdrawalKeysForTest(t)
	output := newHTLCWithdrawalOutputForTest(t, keys, paymentHash[:])

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

// TestToServerHTLCWithdrawalOutput_PreimageCrossCheck pins the fully signed
// preimage claim, which is fully deterministic: keys and transaction are fixed
// and the Schnorr nonce is derived from the private key and the message alone.
// The backend's TransactionSchemeSignedTransactionLibwalletCrossTest must
// produce this exact transaction from the same inputs: any update to the pinned
// values here (keys, fixture parameters, preimage, signedTxHex) must be
// replicated there.
func TestToServerHTLCWithdrawalOutput_PreimageCrossCheck(t *testing.T) {
	const signedTxHex = "020000000001013217626dc253348c1ca6eb7f5f4638f5a3da2945ac6e9da2853e68948fad14db0000000000ffffffff01b882010000000000225120d862618490d16544e6a6c8d19994592e0933ab4fbf16f37509dceb65e63897450420aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa40fcc559f899273b8dea1c827918bc247dfe1879963d5a884a8a3925473e98e52181b45f870363df041603665ea1366149fd82c995df9f205320a31ef23140d218492026c608c1e23df7d0006f0d0f1fc0928a7b235fc7321ddafb12b70710e6da48f3ad82012088a820e0e77a507412b120f6ede61f62295b1a7b2ff19d3dcc8f7253e51663470c888e8761c186a4daa3b2f898e25068339eed0d2f669f95c5722e3513bab3c6fc9b732ba91b3167b34747d0856e22b165ad39951c78a7588b1f5dbe0a54ad7948c0cc834c8b6ec8c7b80508a23a4105e3730bf3db4be014e1a8fd0be90badfcf4cffac15bbf00000000" //nolint:lll

	preimage := bytes.Repeat([]byte{0xaa}, 32)
	paymentHash := sha256.Sum256(preimage)
	keys := newWithdrawalKeysForTest(t)
	output := newHTLCWithdrawalOutputForTest(t, keys, paymentHash[:])

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])

	SignToServerHTLCWithdrawalPreimageForTest(
		t, 0, spendTx, sigHashes, output, keys.lightningServer, preimage,
	)

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}
