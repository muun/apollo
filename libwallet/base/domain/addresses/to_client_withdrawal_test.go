package addresses

import (
	"bytes"
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

func TestToClientWithdrawalOutput_Address(t *testing.T) {
	const (
		// Fixed vectors shared with the backend transaction scheme cross tests.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"
		timelock           = int64(10)

		// Public key of the 32-byte private key 0x04 repeated.
		revocationPubKeyHex = "03462779ad4aad39514614751a71085f2f10e1c7a593e4e030efb5b8721ce55b0b"

		withdrawalAddress = "bcrt1pqxl5m6nr0ugq5runmvu2u48ehs7pt22slw8tqr2py6ljmk3lpnws5y4ur9"
	)

	rawRevocationPubKey, err := hex.DecodeString(revocationPubKeyHex)
	require.NoError(t, err)
	clientRevocationPubKey, err := btcec.ParsePubKey(rawRevocationPubKey)
	require.NoError(t, err)
	setSpendDelaysForTest(t, timelock)

	output, err := NewToClientWithdrawalOutput(
		publicKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey),
		addressPath,
		clientRevocationPubKey,
	)
	require.NoError(t, err)

	got, err := output.Address()
	require.NoError(t, err)

	require.Equal(t, withdrawalAddress, got.Address())
	require.Equal(t, ToClientWithdrawal, got.Version())
	require.Equal(t, addressPath, got.DerivationPath())
}

func TestToClientWithdrawalOutput_RejectsUncoveredDerivationPath(t *testing.T) {
	_, err := NewToClientWithdrawalOutput(
		fixtureBaseKeyTriple(t),
		"m/schema:1'/recovery:1'/contacts:2/0",
		testbtcscript.PrivKeyFromByte(0x04).PubKey(),
	)
	require.Error(t, err)
}

// withdrawalKeysForTest holds the participant private keys of one withdrawal
// test output: the wallet keys at the schema base path and the revocation key.
type withdrawalKeysForTest struct {
	user            *hd_keys.HDPrivateKey
	cosigner        *hd_keys.HDPrivateKey
	lightningServer *hd_keys.HDPrivateKey
	revocation      *btcec.PrivateKey
}

// newWithdrawalKeysForTest builds the participants from deterministic keys.
func newWithdrawalKeysForTest(t *testing.T) withdrawalKeysForTest {
	t.Helper()

	return withdrawalKeysForTest{
		user:            basePrivKeyForTest(t, 0x01),
		cosigner:        basePrivKeyForTest(t, 0x02),
		lightningServer: basePrivKeyForTest(t, 0x03),
		revocation:      testbtcscript.PrivKeyFromByte(0x04),
	}
}

// newWithdrawalOutputForTest builds a ToClientWithdrawalOutput over keys,
// claimable after the fixture timelock.
func newWithdrawalOutputForTest(
	t *testing.T,
	keys withdrawalKeysForTest,
) *ToClientWithdrawalOutput {
	t.Helper()

	triple := hd_keys.NewPublicKeyTriple(
		keys.user.PublicKey(), keys.cosigner.PublicKey(), keys.lightningServer.PublicKey(),
	)
	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToClientWithdrawalOutput(
		triple, fixturePath, keys.revocation.PubKey(),
	)
	require.NoError(t, err)
	return output
}

func TestToClientWithdrawalOutput_CollaborativeSpend(t *testing.T) {
	keys := newWithdrawalKeysForTest(t)
	output := newWithdrawalOutputForTest(t, keys)

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
	longestWitness, err := LongestToClientWithdrawalCollaborativeWitness()
	require.NoError(t, err)
	require.LessOrEqual(t, spendTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// SignToClientWithdrawalCollaborativeSpendForTest signs the collaborative key
// path spend the way the user completes it: the counterparty partials are
// verified, then combined with the user's and placed in the witness.
func SignToClientWithdrawalCollaborativeSpendForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToClientWithdrawalOutput,
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

// The lightning server's revocation spend only verifies with the revocation key
// the user revealed when revoking the withdrawal.
func TestToClientWithdrawalOutput_RevocationSpend(t *testing.T) {
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
			keys := newWithdrawalKeysForTest(t)
			output := newWithdrawalOutputForTest(t, keys)

			prevTx := fundingTx(t, output, fixtureAmount)

			// The revocation spend has no relative timelock.
			penaltyTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			SignToClientWithdrawalRevocationForTest(
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
			longestWitness, err := LongestToClientWithdrawalRevocationWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				penaltyTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// SignToClientWithdrawalRevocationForTest assembles the witness for a spend
// through the revocation leaf, which the lightning server signs with the
// revealed revocation key. Both partial signatures of the 2-of-2 MuSig2 are
// computed locally.
func SignToClientWithdrawalRevocationForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToClientWithdrawalOutput,
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
		tapScriptTree, toClientWithdrawalRevocationLeafIndex, index, tx, sigHashes,
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
		tapScriptTree, toClientWithdrawalRevocationLeafIndex,
	)
	require.NoError(t, err)

	tx.TxIn[index].Witness = wire.TxWitness{
		signature,
		tapScriptTree.LeafMerkleProofs[toClientWithdrawalRevocationLeafIndex].Script,
		controlBlock,
	}
}

// Signing a spend through the non-collaborative leaf panics before the delay;
// the spend is accepted after it.
func TestToClientWithdrawalOutput_NonCollaborativeSpend(t *testing.T) {
	tests := []struct {
		name      string
		sequence  uint32
		wantPanic string
	}{
		{
			name:      "non-collaborative spend after the delay",
			sequence:  uint32(fixtureTimelock),
			wantPanic: "",
		},
		{
			name:     "signing before the delay panics",
			sequence: uint32(fixtureTimelock) - 1,
			wantPanic: fmt.Sprintf(
				"input sequence must encode at least %d blocks, got %d",
				fixtureTimelock, fixtureTimelock-1,
			),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			keys := newWithdrawalKeysForTest(t)
			output := newWithdrawalOutputForTest(t, keys)

			prevTx := fundingTx(t, output, fixtureAmount)
			spendTx := spendingTx(prevTx, tt.sequence)

			compute := func() ([]byte, error) {
				return output.ComputeNonCollaborativeFullSignature(
					0,
					spendTx,
					testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0]),
					keys.user,
					keys.cosigner,
				)
			}
			if tt.wantPanic != "" {
				require.PanicsWithError(t, tt.wantPanic, func() { _, _ = compute() })
				return
			}
			finalSignature, err := compute()
			require.NoError(t, err)
			witness, err := output.AssembleNonCollaborativeWitness(finalSignature)
			require.NoError(t, err)
			spendTx.TxIn[0].Witness = witness

			require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToClientWithdrawalNonCollaborativeWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// TestToClientWithdrawalOutput_CollaborativeCrossCheck pins the fully signed
// collaborative (3-of-3 key path) spending tx, which is fully deterministic:
// keys, transaction and session ids are all fixed. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this exact
// transaction from the same inputs: any update to the pinned values here (keys,
// fixture parameters, session ids, signedTxHex) must be replicated there.
//
// The non-collaborative and revocation spends have no cross check: both sides
// run their MuSig2 session internally, so their signatures are not reproducible.
func TestToClientWithdrawalOutput_CollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "020000000001017f6aea83f8097a4f14602377ff264d7390c82b47964c61185ab8247e3af0bc960000000000ffffffff01b88201000000000022512033f865409f33b518c38f9ee418a0c09a64dd86754ff6d020a40fb0d93824a2ef01402cfc47286bceda5256fa2571022a01552375bb96fdf7c344f2982ad3528d70de21d8e4b1888954b2610150926ff4a772341e5d83099a3d7e2b4e92a606bb2f0d00000000" //nolint:lll

	keys := newWithdrawalKeysForTest(t)
	output := newWithdrawalOutputForTest(t, keys)

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
