package addresses

import (
	"bytes"
	"encoding/hex"
	"fmt"
	"reflect"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/wire"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography/musig"
	"github.com/muun/libwallet/platform/test/testbtcscript"
)

func TestCreateAddressV9(t *testing.T) {
	const (
		// Same keys and timelock as TestToClientDescriptors_Integration, which funds and sweeps
		// the scheme against bitcoind.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"
		timelock           = int64(10)
		v9Address          = "bcrt1p7q7r27dhm47qvtjm49slxep2lln99ch9u7zx25upmede4kc56skqprqm0d"
	)

	baseKeys := baseKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey)
	setSpendDelaysForTest(t, timelock)

	output, err := NewToClientV9(baseKeys, addressPath)
	require.NoError(t, err)

	got, err := output.Address()
	if err != nil {
		t.Fatalf("CreateAddressV9() error = %v", err)
	}

	want := &WalletAddress{address: v9Address, derivationPath: addressPath, version: V9}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("CreateAddressV9() = %v, want %v", got, want)
	}
}

func TestToClientV9_RejectsUncoveredDerivationPath(t *testing.T) {
	_, err := NewToClientV9(fixtureBaseKeyTriple(t), "m/schema:1'/recovery:1'/contacts:2/0")
	require.Error(t, err)
}

// newFixtureToClientV9 builds a ToClientV9 over the fixture keys, expiring
// after fixtureTimelock blocks.
func newFixtureToClientV9(t *testing.T) *ToClientV9 {
	t.Helper()

	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToClientV9(fixtureKeyTriple(t), fixturePath)
	require.NoError(t, err)
	return output
}

// generateFixtureNonce derives a public nonce for one signer from a
// deterministic session id.
func generateFixtureNonce(t *testing.T, signerPubKey *btcec.PublicKey, fill byte) []byte {
	t.Helper()

	nonces, err := musig.MuSig2GenerateNonce(
		musig.Musig2v100,
		bytes.Repeat([]byte{fill}, 32),
		signerPubKey.SerializeCompressed(),
	)
	require.NoError(t, err)
	return nonces.PubNonce[:]
}

func TestToClientV9_CollaborativeSpend(t *testing.T) {
	basePrivKeys := fixtureBasePrivateKeys(t)
	output := newFixtureToClientV9(t)

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])

	// Distinct deterministic session ids for each signer.
	userSessionID := bytes.Repeat([]byte{0x10}, 32)
	cosignerSessionID := bytes.Repeat([]byte{0x20}, 32)
	lightningServerSessionID := bytes.Repeat([]byte{0x30}, 32)

	userNonce := generateFixtureNonce(t, output.UserPubKey, userSessionID[0])
	cosignerNonce := generateFixtureNonce(t, output.CosignerPubKey, cosignerSessionID[0])
	lightningServerNonce := generateFixtureNonce(
		t, output.LightningServerPubKey, lightningServerSessionID[0],
	)
	allNonces := [][]byte{userNonce, cosignerNonce, lightningServerNonce}

	userPartialSignature, err := output.ComputeUserCollaborativePartialSignature(
		0, spendTx, sigHashes,
		basePrivKeys[0],
		userSessionID,
		cosignerNonce,
		lightningServerNonce,
	)
	require.NoError(t, err)
	cosignerPartialSignature, err := output.ComputeCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		basePrivKeys[1],
		cosignerSessionID,
		userNonce,
		lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerCollaborativePartialSignature(
			0, spendTx, sigHashes,
			basePrivKeys[2],
			lightningServerSessionID,
			userNonce,
			cosignerNonce,
		)
	require.NoError(t, err)

	// A partial signature only verifies against the participant that produced it.
	err = output.VerifyUserCollaborativePartialSignature(
		0, spendTx, sigHashes,
		userPartialSignature, userNonce, cosignerNonce, lightningServerNonce,
	)
	require.NoError(t, err)
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
	err = output.VerifyUserCollaborativePartialSignature(
		0, spendTx, sigHashes,
		cosignerPartialSignature, userNonce, cosignerNonce, lightningServerNonce,
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

	witness, err := output.AssembleCollaborativeWitness(finalSignature)
	require.NoError(t, err)
	spendTx.TxIn[0].Witness = witness
	require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

	// Fee estimation sizes spends with the longest witness, so a real witness
	// must never exceed it.
	require.LessOrEqual(
		t,
		spendTx.TxIn[0].Witness.SerializeSize(),
		LongestToClientV9CollaborativeWitness().SerializeSize(),
	)
}

func TestToClientV9_NonCollaborativeSpend(t *testing.T) {
	tests := []struct {
		name      string
		sequence  uint32
		wantPanic string
	}{
		{
			name:      "after the timelock",
			sequence:  uint32(fixtureTimelock),
			wantPanic: "",
		},
		{
			name:     "signing before the timelock panics",
			sequence: uint32(fixtureTimelock) - 1,
			wantPanic: fmt.Sprintf(
				"input sequence must encode at least %d blocks, got %d",
				fixtureTimelock, fixtureTimelock-1,
			),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			privKeys := fixtureBasePrivateKeys(t)
			output := newFixtureToClientV9(t)

			prevTx := fundingTx(t, output, fixtureAmount)
			spendTx := spendingTx(prevTx, tt.sequence)
			sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])

			compute := func() ([]byte, error) {
				return output.ComputeNonCollaborativeFullSignature(
					0, spendTx, sigHashes, privKeys[0], privKeys[1],
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
			longestWitness, err := LongestToClientV9NonCollaborativeWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// TestToClientV9_CollaborativeCrossCheck pins the fully signed collaborative
// 3-of-3 spending tx, which is fully deterministic: keys, transaction and
// session ids are all fixed. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this
// exact transaction from the same inputs: any update to the pinned values
// here (keys, fixture parameters, session ids, signedTxHex) must be
// replicated there.
//
// The non-collaborative path takes no session ids from its caller, so it
// cannot be pinned this way.
func TestToClientV9_CollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "02000000000101a837380d2af23b45ced7945fd5ededd3f59862b7df9be2f319b0aa063075149d0000000000ffffffff01b8820100000000002251204066ed44a4aedd8e58889175e61001b79c23303b358b1c70d588847c59e6134e01406e1bafbc4289ee42acde75ebfdf563150c868e043e0294060aa7a18a6cc42aae68d86f35fe69c73f9d77943d3a6b1ebc497370995e2cde79256b8d17b257ab2800000000" //nolint:lll

	basePrivKeys := fixtureBasePrivateKeys(t)
	output := newFixtureToClientV9(t)

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])

	userSessionID := bytes.Repeat([]byte{0x10}, 32)
	cosignerSessionID := bytes.Repeat([]byte{0x20}, 32)
	lightningServerSessionID := bytes.Repeat([]byte{0x30}, 32)

	userNonce := generateFixtureNonce(t, output.UserPubKey, userSessionID[0])
	cosignerNonce := generateFixtureNonce(t, output.CosignerPubKey, cosignerSessionID[0])
	lightningServerNonce := generateFixtureNonce(
		t, output.LightningServerPubKey, lightningServerSessionID[0],
	)

	userPartialSignature, err := output.ComputeUserCollaborativePartialSignature(
		0, spendTx, sigHashes,
		basePrivKeys[0],
		userSessionID,
		cosignerNonce,
		lightningServerNonce,
	)
	require.NoError(t, err)
	cosignerPartialSignature, err := output.ComputeCosignerCollaborativePartialSignature(
		0, spendTx, sigHashes,
		basePrivKeys[1],
		cosignerSessionID,
		userNonce,
		lightningServerNonce,
	)
	require.NoError(t, err)
	lightningServerPartialSignature, err :=
		output.ComputeLightningServerCollaborativePartialSignature(
			0, spendTx, sigHashes,
			basePrivKeys[2],
			lightningServerSessionID,
			userNonce,
			cosignerNonce,
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

	witness, err := output.AssembleCollaborativeWitness(finalSignature)
	require.NoError(t, err)
	spendTx.TxIn[0].Witness = witness

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}
