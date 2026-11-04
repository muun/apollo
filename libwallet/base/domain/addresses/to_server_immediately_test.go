package addresses

import (
	"bytes"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/platform/test/testbtcscript"
)

func TestToServerImmediatelyOutput_Address(t *testing.T) {
	const (
		userKey                    = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey                = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey         = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath                = "m/schema:1'/recovery:1'/external:1/0"
		toServerImmediatelyAddress = "bcrt1qz48r0tm80xkc7saeensalsxdmflzp40trmq05u"
	)

	baseKeys := baseKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey)

	output, err := NewToServerImmediatelyOutput(baseKeys, addressPath)
	require.NoError(t, err)

	got, err := output.Address()
	require.NoError(t, err)

	want := New(ToServerImmediately, addressPath, toServerImmediatelyAddress)
	require.Equal(t, want, got)
}

func TestToServerImmediatelyOutput_RejectsUncoveredDerivationPath(t *testing.T) {
	_, err := NewToServerImmediatelyOutput(
		fixtureBaseKeyTriple(t), "m/schema:1'/recovery:1'/contacts:2/0",
	)
	require.Error(t, err)
}

// The lightning server's spend only verifies with the key the output pays to.
func TestToServerImmediatelyOutput_Spend(t *testing.T) {
	tests := []struct {
		name        string
		signingFill byte
		wantErr     bool
	}{
		{
			name:        "spend with the lightning server key",
			signingFill: 0x03,
			wantErr:     false,
		},
		{
			name:        "spend with a wrong key fails",
			signingFill: 0x05,
			wantErr:     true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			output, err := NewToServerImmediatelyOutput(fixtureBaseKeyTriple(t), fixturePath)
			require.NoError(t, err)

			prevTx := fundingTx(t, output, fixtureAmount)
			spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			signingKey, err := fixtureBasePrivateKey(t, tt.signingFill).DeriveTo(fixturePath)
			require.NoError(t, err)
			signingECKey, err := signingKey.ECPrivKey()
			require.NoError(t, err)

			SignToServerImmediatelyForTest(
				t,
				0,
				spendTx,
				testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0]),
				prevTx.TxOut[0],
				signingECKey,
			)

			vmErr := testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0)
			if tt.wantErr {
				require.Error(t, vmErr)
			} else {
				require.NoError(t, vmErr)
			}

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToServerImmediatelyWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// TestToServerImmediatelyOutput_CrossCheck pins the fully signed spending tx,
// which is fully deterministic: keys and transaction are fixed and the ECDSA
// nonce is derived from the private key and the message alone (RFC 6979). The
// backend's TransactionSchemeSignedTransactionLibwalletCrossTest must produce
// this exact transaction from the same inputs: any update to the pinned values
// here (keys, fixture parameters, signedTxHex) must be replicated there.
func TestToServerImmediatelyOutput_CrossCheck(t *testing.T) {
	const signedTxHex = "02000000000101188679adb421731bb3b31f703f97518ff3d268c43cca7046214e42d745c761aa0000000000ffffffff01b882010000000000160014194c15cb31792de4e22c242ece7f341de93b448802483045022100ecb126f6de69fac1134d9449f30941633e70b2a0334465ead8fcf59db0678871022057a2fd30235623345519a8086152050fbbc59af2cbc8337c77575f98674e66b001210326c608c1e23df7d0006f0d0f1fc0928a7b235fc7321ddafb12b70710e6da48f300000000" //nolint:lll

	output, err := NewToServerImmediatelyOutput(fixtureBaseKeyTriple(t), fixturePath)
	require.NoError(t, err)
	lightningServerKey, err := fixtureBasePrivateKey(t, 0x03).DeriveTo(fixturePath)
	require.NoError(t, err)
	lightningServerECKey, err := lightningServerKey.ECPrivKey()
	require.NoError(t, err)

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

	SignToServerImmediatelyForTest(
		t,
		0,
		spendTx,
		testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0]),
		prevTx.TxOut[0],
		lightningServerECKey,
	)

	var buf bytes.Buffer
	require.NoError(t, spendTx.Serialize(&buf))
	require.Equal(t, signedTxHex, hex.EncodeToString(buf.Bytes()))
}

// SignToServerImmediatelyForTest assembles the P2WPKH witness for a spend of
// prevOut, which the lightning server signs on its own.
func SignToServerImmediatelyForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	prevOut *wire.TxOut,
	lightningServerPrivKey *btcec.PrivateKey,
) {
	t.Helper()

	witness, err := txscript.WitnessSignature(
		tx, sigHashes, index, prevOut.Value, prevOut.PkScript,
		txscript.SigHashAll, lightningServerPrivKey, true,
	)
	require.NoError(t, err)
	tx.TxIn[index].Witness = witness
}
