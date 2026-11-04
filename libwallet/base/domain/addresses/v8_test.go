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

func TestCreateAddressV8(t *testing.T) {
	const (
		// Same keys and timelock as TestToClientDescriptors_Integration, which funds and sweeps
		// the scheme against bitcoind.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"
		timelock           = int64(10)
		v8Address          = "bcrt1qeqn5zw4zvkekcyhhuxc8hjthujt8lgl6d43u6reyp3eddhcdp9eszsmvmr"
	)

	baseKeys := baseKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey)
	setSpendDelaysForTest(t, timelock)

	output, err := NewToClientV8(baseKeys, addressPath)
	require.NoError(t, err)

	got, err := output.Address()
	if err != nil {
		t.Fatalf("CreateAddressV8() error = %v", err)
	}

	want := &WalletAddress{address: v8Address, derivationPath: addressPath, version: V8}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("CreateAddressV8() = %v, want %v", got, want)
	}
}

func TestToClientV8_RejectsUncoveredDerivationPath(t *testing.T) {
	_, err := NewToClientV8(
		fixtureBaseKeyTriple(t), "m/schema:1'/recovery:1'/contacts:2/0",
	)
	require.Error(t, err)
}

// newFixtureToClientV8 builds a ToClientV8 over the fixture keys.
func newFixtureToClientV8(t *testing.T) *ToClientV8 {
	t.Helper()

	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToClientV8(fixtureKeyTriple(t), fixturePath)
	require.NoError(t, err)
	return output
}

func TestToClientV8_CollaborativeSpend(t *testing.T) {
	privKeys := fixtureBasePrivateKeys(t)
	output := newFixtureToClientV8(t)

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

	require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

	// Fee estimation sizes spends with the longest witness, so a real witness
	// must never exceed it.
	longestWitness, err := LongestToClientV8CollaborativeWitness()
	require.NoError(t, err)
	require.LessOrEqual(
		t,
		spendTx.TxIn[0].Witness.SerializeSize(),
		longestWitness.SerializeSize(),
	)
}

func TestToClientV8_NonCollaborativeSpend(t *testing.T) {
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
			output := newFixtureToClientV8(t)

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

			require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToClientV8NonCollaborativeWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// TestToClientV8_CollaborativeCrossCheck pins the fully signed collaborative
// 3-of-3 spending tx, which is fully deterministic: keys and transaction are
// fixed and ECDSA nonces are RFC 6979. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this
// exact transaction from the same inputs: any update to the pinned values
// here (keys, fixture parameters, signedTxHex) must be replicated there.
func TestToClientV8_CollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "0200000000010134f4cd91802e0fda09994bc955a012b96ed6494f8f5592d2b70649c7d25188960000000000ffffffff01b882010000000000220020b6abdf0374946682b7f77d75196f9344a1fba598a7171ce4fd0aa5e0623b802904483045022100cc9d4a1ae05888dc93d662ec0d28b1f93522357bed5892faaa837a4b38757a3502203220a7cc995fe7441b9015b89146a454f4e497fea635a246e58b07f9ca4fc0d601483045022100ba4670962bd2f20bbaa5a903b56f06f6ba99f446b121de496b88a466d4b686e602202e674a5d4c2c30ce97203b2d5e01f02e9e662d6177b8a66d3d4d900ecdd46f8d0147304402205ad77b16dccf739baced6c4a5cceb03b06599c4150ecb19a12d8435074164db602200aaae6cc525ed09f0769fdca6c016312a1a76eb6ee1fc991a6be17b95fc5af5e017021035917f32a648c4e35de79357e35684bb3323087df6e5f91d7b6caf477c2cdefcead210339fc8585b6326ec00c02b21ed6eaf1723d81e3a45e2a4e8353d5f625713c053cad210326c608c1e23df7d0006f0d0f1fc0928a7b235fc7321ddafb12b70710e6da48f3ac7364029000b26800000000" //nolint:lll

	privKeys := fixtureBasePrivateKeys(t)
	output := newFixtureToClientV8(t)

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

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}

// TestToClientV8_NonCollaborativeCrossCheck pins the fully signed
// non-collaborative (2-of-2 + timelock) spending tx, which is fully
// deterministic: keys and transaction are fixed and ECDSA nonces are RFC 6979.
// The backend's TransactionSchemeSignedTransactionLibwalletCrossTest must
// produce this exact transaction from the same inputs: any update to the
// pinned values here (keys, fixture parameters, signedTxHex) must be
// replicated there.
func TestToClientV8_NonCollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "0200000000010134f4cd91802e0fda09994bc955a012b96ed6494f8f5592d2b70649c7d251889600000000009000000001b882010000000000220020b6abdf0374946682b7f77d75196f9344a1fba598a7171ce4fd0aa5e0623b80290400483045022100f8924e501dbdbdb8c10b46715061a2842a91c95e436588b7deeafcaef1e7c92302204ad148c533995611b000b69561bc124dda7488c426041bc069b0c5d66f002dc601483045022100adeaa70bef85798c750dcd86840b875300ba4716c29a43d2bb35f7a5bc396ec802200258397f5463953d3e9ec090296846f48febfe5ff37b76e5e40faa46b4b25ee9017021035917f32a648c4e35de79357e35684bb3323087df6e5f91d7b6caf477c2cdefcead210339fc8585b6326ec00c02b21ed6eaf1723d81e3a45e2a4e8353d5f625713c053cad210326c608c1e23df7d0006f0d0f1fc0928a7b235fc7321ddafb12b70710e6da48f3ac7364029000b26800000000" //nolint:lll

	privKeys := fixtureBasePrivateKeys(t)
	output := newFixtureToClientV8(t)

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

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}
