package addresses

import (
	"bytes"
	"encoding/hex"
	"fmt"
	"reflect"
	"testing"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/wire"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/platform/test/testbtcscript"
)

func TestCreateAddressV7(t *testing.T) {
	const (
		// Same keys and timelock as TestToClientDescriptors_Integration, which funds and sweeps
		// the scheme against bitcoind.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"
		timelock           = int64(10)
		v7Address          = "2N7RBwArtkwosUsBsguPAFS1ydsG5FeeZM9"
	)

	baseKeys := baseKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey)
	setSpendDelaysForTest(t, timelock)

	output, err := NewToClientV7(baseKeys, addressPath)
	require.NoError(t, err)

	got, err := output.Address()
	if err != nil {
		t.Fatalf("CreateAddressV7() error = %v", err)
	}

	want := &WalletAddress{address: v7Address, derivationPath: addressPath, version: V7}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("CreateAddressV7() = %v, want %v", got, want)
	}
}

func TestToClientV7_RejectsUncoveredDerivationPath(t *testing.T) {
	_, err := NewToClientV7(
		fixtureBaseKeyTriple(t), "m/schema:1'/recovery:1'/contacts:2/0",
	)
	require.Error(t, err)
}

// newFixtureToClientV7 builds a ToClientV7 over the fixture keys.
func newFixtureToClientV7(t *testing.T) *ToClientV7 {
	t.Helper()

	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToClientV7(fixtureKeyTriple(t), fixturePath)
	require.NoError(t, err)
	return output
}

func TestToClientV7_CollaborativeSpend(t *testing.T) {
	privKeys := fixtureBasePrivateKeys(t)
	output := newFixtureToClientV7(t)

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])
	amount := btcutil.Amount(fixtureAmount)

	userSignature, err := output.ComputeUserPartialSignature(
		0, spendTx, sigHashes, amount, privKeys[0],
	)
	require.NoError(t, err)
	cosignerSignature, err := output.ComputeCosignerPartialSignature(
		0, spendTx, sigHashes, amount, privKeys[1],
	)
	require.NoError(t, err)
	lightningServerSignature, err := output.ComputeLightningServerPartialSignature(
		0, spendTx, sigHashes, amount, privKeys[2],
	)
	require.NoError(t, err)

	// Every signature verifies for its signer.
	err = output.VerifyUserPartialSignature(0, spendTx, sigHashes, amount, userSignature)
	require.NoError(t, err)
	err = output.VerifyCosignerPartialSignature(0, spendTx, sigHashes, amount, cosignerSignature)
	require.NoError(t, err)
	err = output.VerifyLightningServerPartialSignature(
		0, spendTx, sigHashes, amount, lightningServerSignature,
	)
	require.NoError(t, err)

	// A signature attributed to the wrong signer must not verify.
	err = output.VerifyUserPartialSignature(0, spendTx, sigHashes, amount, cosignerSignature)
	require.ErrorContains(t, err, "invalid signature")

	witness, err := output.AssembleCollaborativeWitness(
		userSignature, cosignerSignature, lightningServerSignature,
	)
	require.NoError(t, err)
	spendTx.TxIn[0].Witness = witness
	inputScript, err := output.InputScript()
	require.NoError(t, err)
	spendTx.TxIn[0].SignatureScript = inputScript

	require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

	// Fee estimation sizes spends with the longest witness and input script,
	// so a real spend must never exceed them.
	longestWitness, err := LongestToClientV7CollaborativeWitness()
	require.NoError(t, err)
	require.LessOrEqual(
		t,
		spendTx.TxIn[0].Witness.SerializeSize(),
		longestWitness.SerializeSize(),
	)
	longestInputScript, err := LongestToClientV7InputScript()
	require.NoError(t, err)
	require.LessOrEqual(
		t,
		len(spendTx.TxIn[0].SignatureScript),
		len(longestInputScript),
	)
}

func TestToClientV7_NonCollaborativeSpend(t *testing.T) {
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
			output := newFixtureToClientV7(t)

			prevTx := fundingTx(t, output, fixtureAmount)
			spendTx := spendingTx(prevTx, tt.sequence)
			sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])
			amount := btcutil.Amount(fixtureAmount)

			compute := func() (*NonCollaborativeFullSignatures, error) {
				return output.ComputeNonCollaborativeFullSignatures(
					0, spendTx, sigHashes, amount, privKeys[0], privKeys[1],
				)
			}
			if tt.wantPanic != "" {
				require.PanicsWithError(t, tt.wantPanic, func() { _, _ = compute() })
				return
			}
			signatures, err := compute()
			require.NoError(t, err)
			witness, err := output.AssembleNonCollaborativeWitness(signatures)
			require.NoError(t, err)
			spendTx.TxIn[0].Witness = witness
			inputScript, err := output.InputScript()
			require.NoError(t, err)
			spendTx.TxIn[0].SignatureScript = inputScript

			require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

			// Fee estimation sizes spends with the longest witness and input
			// script, so a real spend must never exceed them.
			longestWitness, err := LongestToClientV7NonCollaborativeWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
			longestInputScript, err := LongestToClientV7InputScript()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				len(spendTx.TxIn[0].SignatureScript),
				len(longestInputScript),
			)
		})
	}
}

// TestToClientV7_CollaborativeCrossCheck pins the fully signed collaborative
// 3-of-3 spending tx, which is fully deterministic: keys and transaction are
// fixed and ECDSA nonces are RFC 6979. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this
// exact transaction from the same inputs: any update to the pinned values
// here (keys, fixture parameters, signedTxHex) must be replicated there.
func TestToClientV7_CollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "02000000000101016123e75e929870b0eafaee7fbe6cccde79b298ff6e446cbd7e8f05059da9760000000023220020b6abdf0374946682b7f77d75196f9344a1fba598a7171ce4fd0aa5e0623b8029ffffffff01b88201000000000017a91411b449c665ba1bd041a3fb12fa1cfceff16786008704483045022100d97a9fd0deb528f5eb4ed0af57057e987aac044e0a2f67e9b332257d0d5e196802201feacefe6c1f413cd80322daed5f6657a367897b255cb1ed1ea45a60f9934c2201483045022100ee02dfbc6913a289086182ac2beeb28d30b5dcae99daae219029db68893db0ea02200c9da2539d0e5475a25b55542dc08423495d8eb4ec43bbaddec92203a7b1d69d01483045022100cfadde8401cfb9dffb95d63b4f158d55e9ea41b5da65fcec21468835a817a5d202207394dc4a4d11dbee6debcda765037495f52e1c9fef8535761bdfa29ab60aa12e017021035917f32a648c4e35de79357e35684bb3323087df6e5f91d7b6caf477c2cdefcead210339fc8585b6326ec00c02b21ed6eaf1723d81e3a45e2a4e8353d5f625713c053cad210326c608c1e23df7d0006f0d0f1fc0928a7b235fc7321ddafb12b70710e6da48f3ac7364029000b26800000000" //nolint:lll

	privKeys := fixtureBasePrivateKeys(t)
	output := newFixtureToClientV7(t)

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])
	amount := btcutil.Amount(fixtureAmount)

	userSignature, err := output.ComputeUserPartialSignature(
		0, spendTx, sigHashes, amount, privKeys[0],
	)
	require.NoError(t, err)
	cosignerSignature, err := output.ComputeCosignerPartialSignature(
		0, spendTx, sigHashes, amount, privKeys[1],
	)
	require.NoError(t, err)
	lightningServerSignature, err := output.ComputeLightningServerPartialSignature(
		0, spendTx, sigHashes, amount, privKeys[2],
	)
	require.NoError(t, err)

	witness, err := output.AssembleCollaborativeWitness(
		userSignature, cosignerSignature, lightningServerSignature,
	)
	require.NoError(t, err)
	spendTx.TxIn[0].Witness = witness
	inputScript, err := output.InputScript()
	require.NoError(t, err)
	spendTx.TxIn[0].SignatureScript = inputScript

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}

// TestToClientV7_NonCollaborativeCrossCheck pins the fully signed
// non-collaborative (2-of-2 + timelock) spending tx, which is fully
// deterministic: keys and transaction are fixed and ECDSA nonces are RFC 6979.
// The backend's TransactionSchemeSignedTransactionLibwalletCrossTest must
// produce this exact transaction from the same inputs: any update to the
// pinned values here (keys, fixture parameters, signedTxHex) must be
// replicated there.
func TestToClientV7_NonCollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "02000000000101016123e75e929870b0eafaee7fbe6cccde79b298ff6e446cbd7e8f05059da9760000000023220020b6abdf0374946682b7f77d75196f9344a1fba598a7171ce4fd0aa5e0623b80299000000001b88201000000000017a91411b449c665ba1bd041a3fb12fa1cfceff1678600870400483045022100e868a1c8e570a9e4ade0e61e0551695d120d5a0b7acb4982ecdc1008e4c826da022077e8f7e7c64d2088ade9a2acd0032b66102b7c4fcb50183b9aadda869c08c483014830450221008ea4de218014a26c678e5b7a4d49766fc59cc9072efd630c81773bc34f061c2702200c4e009f00d7c2ad606c62101d834412e45743864b4b0099a245d3c76edb2c77017021035917f32a648c4e35de79357e35684bb3323087df6e5f91d7b6caf477c2cdefcead210339fc8585b6326ec00c02b21ed6eaf1723d81e3a45e2a4e8353d5f625713c053cad210326c608c1e23df7d0006f0d0f1fc0928a7b235fc7321ddafb12b70710e6da48f3ac7364029000b26800000000" //nolint:lll

	privKeys := fixtureBasePrivateKeys(t)
	output := newFixtureToClientV7(t)

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, uint32(fixtureTimelock))
	sigHashes := testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0])
	amount := btcutil.Amount(fixtureAmount)

	signatures, err := output.ComputeNonCollaborativeFullSignatures(
		0, spendTx, sigHashes, amount, privKeys[0], privKeys[1],
	)
	require.NoError(t, err)

	witness, err := output.AssembleNonCollaborativeWitness(signatures)
	require.NoError(t, err)
	spendTx.TxIn[0].Witness = witness
	inputScript, err := output.InputScript()
	require.NoError(t, err)
	spendTx.TxIn[0].SignatureScript = inputScript

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}
