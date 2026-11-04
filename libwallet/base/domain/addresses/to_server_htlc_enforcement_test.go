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

func TestToServerHTLCEnforcementOutput_Address(t *testing.T) {
	const (
		// Fixed vectors shared with the backend transaction scheme cross tests.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"

		// Public key of the 32-byte private key 0x04 repeated.
		revocationPubKeyHex = "03462779ad4aad39514614751a71085f2f10e1c7a593e4e030efb5b8721ce55b0b"

		enforcementAddress = "bcrt1pnf5xh42406wjr269d9mllf9pyve382p9lwjganrzq6v64v0r4xuqn8zptf"
	)

	rawRevocationPubKey, err := hex.DecodeString(revocationPubKeyHex)
	require.NoError(t, err)
	clientRevocationPubKey, err := btcec.ParsePubKey(rawRevocationPubKey)
	require.NoError(t, err)
	paymentHash := sha256.Sum256([]byte("preimage-1"))

	// The pinned address commits to the spec's 1-year delay, so nothing shortens it here.
	output, err := NewToServerHTLCEnforcementOutput(
		publicKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey),
		addressPath,
		clientRevocationPubKey,
		paymentHash[:],
	)
	require.NoError(t, err)

	got, err := output.Address()
	require.NoError(t, err)

	require.Equal(t, enforcementAddress, got.Address())
	require.Equal(t, ToServerHTLCEnforcement, got.Version())
	require.Equal(t, addressPath, got.DerivationPath())
}

func TestToServerHTLCEnforcementOutput_RejectsUncoveredDerivationPath(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	_, err := NewToServerHTLCEnforcementOutput(
		fixtureBaseKeyTriple(t),
		"m/schema:1'/recovery:1'/contacts:2/0",
		testbtcscript.PrivKeyFromByte(0x04).PubKey(),
		paymentHash[:],
	)
	require.Error(t, err)
}

// TestToServerHTLCEnforcementOutput_MetadataOutputData pins the output_data
// committed to in the channel state metadata, which counterparties and
// watchtowers must reproduce: the tap branch hash of the
// (preimage, revocation) pair.
func TestToServerHTLCEnforcementOutput_MetadataOutputData(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage-1"))
	output := newServerEnforcementOutputForTest(
		t, newServerEnforcementKeysForTest(t), paymentHash[:],
	)

	outputData, err := output.MetadataOutputData()
	require.NoError(t, err)

	require.Equal(
		t,
		"89181e0191503bc4b3bdf68f988abcbcf6a533427762b25930a47e529de7fde5",
		hex.EncodeToString(outputData),
	)
}

// serverEnforcementKeysForTest holds the participant private keys of one
// enforcement test output: the wallet keys at the schema base path and the
// revocation key.
type serverEnforcementKeysForTest struct {
	user            *hd_keys.HDPrivateKey
	cosigner        *hd_keys.HDPrivateKey
	lightningServer *hd_keys.HDPrivateKey
	revocation      *btcec.PrivateKey
}

// newServerEnforcementKeysForTest builds the participants from deterministic
// keys.
func newServerEnforcementKeysForTest(t *testing.T) serverEnforcementKeysForTest {
	t.Helper()

	return serverEnforcementKeysForTest{
		user:            basePrivKeyForTest(t, 0x01),
		cosigner:        basePrivKeyForTest(t, 0x02),
		lightningServer: basePrivKeyForTest(t, 0x03),
		revocation:      testbtcscript.PrivKeyFromByte(0x04),
	}
}

// serverEnforcementKeysTriple returns the participants' public keys as a
// triple.
func serverEnforcementKeysTriple(
	t *testing.T,
	keys serverEnforcementKeysForTest,
) *hd_keys.PublicKeyTriple {
	t.Helper()

	return hd_keys.NewPublicKeyTriple(
		keys.user.PublicKey(), keys.cosigner.PublicKey(), keys.lightningServer.PublicKey(),
	)
}

// newServerEnforcementOutputForTest builds a ToServerHTLCEnforcementOutput
// over keys committing to paymentHash, expiring after the fixture timelock.
func newServerEnforcementOutputForTest(
	t *testing.T,
	keys serverEnforcementKeysForTest,
	paymentHash []byte,
) *ToServerHTLCEnforcementOutput {
	t.Helper()

	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToServerHTLCEnforcementOutput(
		serverEnforcementKeysTriple(t, keys), fixturePath, keys.revocation.PubKey(),
		paymentHash,
	)
	require.NoError(t, err)
	return output
}

// Signing a spend through the non-collaborative leaf panics before the
// expiration; the spend is accepted after it.
func TestToServerHTLCEnforcementOutput_NonCollaborativeSpend(t *testing.T) {
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
			keys := newServerEnforcementKeysForTest(t)
			output := newServerEnforcementOutputForTest(t, keys, paymentHash[:])

			prevTx := fundingTx(t, output, fixtureAmount)
			spendTx := spendingTx(prevTx, tt.sequence)

			compute := func() ([]byte, error) {
				return ComputeToServerHTLCEnforcementNonCollaborativeFullSignature(
					0,
					spendTx,
					testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0]),
					keys.user,
					keys.cosigner,
					serverEnforcementKeysTriple(t, keys),
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

			witness, err := AssembleToServerHTLCEnforcementNonCollaborativeWitness(
				finalSignature,
				serverEnforcementKeysTriple(t, keys),
				fixturePath,
				branchHash,
			)
			require.NoError(t, err)
			spendTx.TxIn[0].Witness = witness

			require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToServerHTLCEnforcementNonCollaborativeWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

func TestToServerHTLCEnforcementOutput_Preimage(t *testing.T) {
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
			keys := newServerEnforcementKeysForTest(t)
			output := newServerEnforcementOutputForTest(t, keys, tt.paymentHash)

			prevTx := fundingTx(t, output, fixtureAmount)

			// The preimage spend has no relative timelock.
			claimTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			SignToServerHTLCEnforcementPreimageForTest(
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
			longestWitness, err := LongestToServerHTLCEnforcementPreimageWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				claimTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// SignToServerHTLCEnforcementPreimageForTest signs a preimage leaf spend the way the lightning
// server completes it: every participant contributes its partial through the
// model, the server verifies them, combines them and assembles the witness
// once it has the preimage.
func SignToServerHTLCEnforcementPreimageForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToServerHTLCEnforcementOutput,
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
func TestToServerHTLCEnforcementOutput_VerifyPreimagePartialSignature(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newServerEnforcementKeysForTest(t)
	output := newServerEnforcementOutputForTest(t, keys, paymentHash[:])

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

func TestToServerHTLCEnforcementOutput_CollaborativeSpend(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newServerEnforcementKeysForTest(t)
	output := newServerEnforcementOutputForTest(t, keys, paymentHash[:])

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
	longestWitness, err := LongestToServerHTLCEnforcementCollaborativeWitness()
	require.NoError(t, err)
	require.LessOrEqual(t, spendTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// SignToServerHTLCEnforcementCollaborativeSpendForTest fully signs the
// collaborative key path spend with all three private keys locally.
func SignToServerHTLCEnforcementCollaborativeSpendForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToServerHTLCEnforcementOutput,
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

// The lightning server's revocation spend only verifies with the revocation key
// the user revealed when revoking the state.
func TestToServerHTLCEnforcementOutput_RevocationSpend(t *testing.T) {
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
			keys := newServerEnforcementKeysForTest(t)
			output := newServerEnforcementOutputForTest(t, keys, paymentHash[:])

			prevTx := fundingTx(t, output, fixtureAmount)

			// The revocation spend has no relative timelock.
			penaltyTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			SignToServerHTLCEnforcementRevocationForTest(
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
			longestWitness, err := LongestToServerHTLCEnforcementRevocationWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				penaltyTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// TestToServerHTLCEnforcementOutput_CollaborativeCrossCheck pins the fully
// signed collaborative (3-of-3 key path) spending tx, which is fully
// deterministic: keys, transaction and session ids are all fixed. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this exact
// transaction from the same inputs: any update to the pinned values here (keys,
// fixture parameters, session ids, signedTxHex) must be replicated there.
//
// The non-collaborative and revocation spends have no cross check: both sides
// run their MuSig2 session internally, so their signatures are not reproducible.
func TestToServerHTLCEnforcementOutput_CollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "02000000000101bb7a5a4c5bc714a1a7c11d08f5c89672b234479d0301018a3ebed5525e4dd5240000000000ffffffff01b8820100000000002251205006fabe6fe274018416b016a4c56318c43b99a6a8f6a6c2a602a77239cbad29014025693eedc4315fc41853036c41120411f238a04fc9292e1d05d5200da75fdd7d9b6456d330fcff536881ba0b71fe0d1dc5c2058621765b1c3ebfe316f993d0ca00000000" //nolint:lll

	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newServerEnforcementKeysForTest(t)
	output := newServerEnforcementOutputForTest(t, keys, paymentHash[:])

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

// TestToServerHTLCEnforcementOutput_PreimageCrossCheck pins the fully signed
// preimage claim, deterministic for the same reasons as the collaborative
// spend. The backend's TransactionSchemeSignedTransactionLibwalletCrossTest
// must produce this exact transaction from the same inputs: any update to the
// pinned values here (keys, fixture parameters, session ids, preimage,
// signedTxHex) must be replicated there.
func TestToServerHTLCEnforcementOutput_PreimageCrossCheck(t *testing.T) {
	const signedTxHex = "02000000000101dd9506d36d3ac36f9c75b9f6d4a31759c3dd2f95dff270b74ace040576e8d9ad0000000000ffffffff01b882010000000000225120d0105e551087d8d56e2d3f08246c53b77ae6b8afae6cef55e4818e6f4fb69aec0420aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa40a1df8fc065d9edaa7f0f067584ba20e34fcde1b35df44fea109a94b61aa6c7678c1f0bd5e60ecd0f1da87fa57e12fe664e1fe5026f7649ff2887d362870c1f07492086a4daa3b2f898e25068339eed0d2f669f95c5722e3513bab3c6fc9b732ba91bad82012088a820e0e77a507412b120f6ede61f62295b1a7b2ff19d3dcc8f7253e51663470c888e8761c086a4daa3b2f898e25068339eed0d2f669f95c5722e3513bab3c6fc9b732ba91b3167b34747d0856e22b165ad39951c78a7588b1f5dbe0a54ad7948c0cc834c8b01c69a79b9424c877ff0d749ed1f47075c65b190fb4b7103a7d294686d5131fc00000000" //nolint:lll

	preimage := bytes.Repeat([]byte{0xaa}, 32)
	paymentHash := sha256.Sum256(preimage)
	keys := newServerEnforcementKeysForTest(t)
	output := newServerEnforcementOutputForTest(t, keys, paymentHash[:])

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

// SignToServerHTLCEnforcementRevocationForTest assembles the witness for a spend
// through the revocation leaf, which the lightning server signs with the
// revealed revocation key. Both partial signatures of the 2-of-2 MuSig2 are
// computed locally.
func SignToServerHTLCEnforcementRevocationForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToServerHTLCEnforcementOutput,
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
		tapScriptTree, toServerHTLCEnforcementRevocationLeafIndex, index, tx, sigHashes,
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
		tapScriptTree, toServerHTLCEnforcementRevocationLeafIndex,
	)
	require.NoError(t, err)

	tx.TxIn[index].Witness = wire.TxWitness{
		signature,
		tapScriptTree.LeafMerkleProofs[toServerHTLCEnforcementRevocationLeafIndex].Script,
		controlBlock,
	}
}
