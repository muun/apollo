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

func TestToClientHTLCEnforcementOutput_Address(t *testing.T) {
	const (
		// Fixed vectors shared with the backend transaction scheme cross tests.
		userKey            = "tprv8ZgxMBicQKsPdJQ6vbpyPo1TMKsGbvDULSFRrJB6z1o1Jn4x52rZWnPB8w3xjhKbfH8vz9Sv91jNsgXTn8xEEufHSme1xQmWYdrZWWSAdgN" //nolint:lll
		cosignerKey        = "tprv8ZgxMBicQKsPe9kwGRAvhKiZVq3J9tHvJbQ1SEAPuW9Ccb6yq3Zr5kPcR9647JbaGVAXPXcG7rjakUHgXEwKUD64SJSmwZr7y72KK7GG5J8" //nolint:lll
		lightningServerKey = "tprv8ZgxMBicQKsPcsbCVeqqF1KVdH7gwDJbxbzpCxDUsoXHdb6SnTPYxdwSAKGHbiCkJCG7JoDt1asvfWuA6Gbjr5GZ3L5MxLvr2Ef4Rx2HyZH" //nolint:lll
		addressPath        = "m/schema:1'/recovery:1'/external:1/0"

		// Public key of the 32-byte private key 0x04 repeated.
		revocationPubKeyHex = "03462779ad4aad39514614751a71085f2f10e1c7a593e4e030efb5b8721ce55b0b"

		enforcementAddress = "bcrt1pgy0jwlv3k5k2jfhhr48he47ffm7rj4g4lt7d86c4kzhl8zedpelswa9fs3"
	)

	rawRevocationPubKey, err := hex.DecodeString(revocationPubKeyHex)
	require.NoError(t, err)
	clientRevocationPubKey, err := btcec.ParsePubKey(rawRevocationPubKey)
	require.NoError(t, err)
	firstPaymentHash := sha256.Sum256([]byte("preimage-1"))
	secondPaymentHash := sha256.Sum256([]byte("preimage-2"))

	// The pinned address commits to the spec's 1-year delay, so nothing shortens it here.
	output, err := NewToClientHTLCEnforcementOutput(
		publicKeyTripleForTest(t, userKey, cosignerKey, lightningServerKey),
		addressPath,
		clientRevocationPubKey,
		[][]byte{firstPaymentHash[:], secondPaymentHash[:]},
	)
	require.NoError(t, err)

	got, err := output.Address()
	require.NoError(t, err)

	require.Equal(t, enforcementAddress, got.Address())
	require.Equal(t, ToClientHTLCEnforcement, got.Version())
	require.Equal(t, addressPath, got.DerivationPath())
}

func TestToClientHTLCEnforcementOutput_RejectsUncoveredDerivationPath(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	_, err := NewToClientHTLCEnforcementOutput(
		fixtureBaseKeyTriple(t),
		"m/schema:1'/recovery:1'/contacts:2/0",
		testbtcscript.PrivKeyFromByte(0x04).PubKey(),
		[][]byte{paymentHash[:]},
	)
	require.Error(t, err)
}

func TestToClientHTLCEnforcementOutput_RejectsMorePaymentHashesThanMaxPayments(t *testing.T) {
	paymentHashes := make([][]byte, MaxPayments+1)
	for i := range paymentHashes {
		paymentHashes[i] = make([]byte, sha256.Size)
	}
	const wantPanic = "at most 255 payment hashes are allowed, got 256"

	require.PanicsWithError(t, wantPanic, func() {
		_, _ = NewToClientHTLCEnforcementOutput(
			fixtureBaseKeyTriple(t),
			fixturePath,
			testbtcscript.PrivKeyFromByte(0x04).PubKey(),
			paymentHashes,
		)
	})
	require.PanicsWithError(t, wantPanic, func() {
		_, _ = LongestToClientHTLCEnforcementPreimageWitness(MaxPayments + 1)
	})
}

// TestToClientHTLCEnforcementOutput_MetadataOutputData pins the output_data
// committed to in the channel state metadata, which counterparties and
// watchtowers must reproduce: the tap branch hash of the
// (preimage, revocation) pair.
func TestToClientHTLCEnforcementOutput_MetadataOutputData(t *testing.T) {
	firstPaymentHash := sha256.Sum256([]byte("preimage-1"))
	secondPaymentHash := sha256.Sum256([]byte("preimage-2"))
	output := newEnforcementOutputForTest(
		t,
		newEnforcementKeysForTest(t),
		[][]byte{firstPaymentHash[:], secondPaymentHash[:]},
	)

	outputData, err := output.MetadataOutputData()
	require.NoError(t, err)

	require.Equal(
		t,
		"358ceee31e4250494c43bcea08459a91baa72b5a107217daf280806dbd1ae2d5",
		hex.EncodeToString(outputData),
	)
}

// enforcementKeysForTest holds the participant private keys of one enforcement
// test output: the wallet keys at the schema base path and the revocation key.
type enforcementKeysForTest struct {
	user            *hd_keys.HDPrivateKey
	cosigner        *hd_keys.HDPrivateKey
	lightningServer *hd_keys.HDPrivateKey
	revocation      *btcec.PrivateKey
}

// newEnforcementKeysForTest builds the participants from deterministic keys.
func newEnforcementKeysForTest(t *testing.T) enforcementKeysForTest {
	t.Helper()

	return enforcementKeysForTest{
		user:            basePrivKeyForTest(t, 0x01),
		cosigner:        basePrivKeyForTest(t, 0x02),
		lightningServer: basePrivKeyForTest(t, 0x03),
		revocation:      testbtcscript.PrivKeyFromByte(0x04),
	}
}

// enforcementKeysTriple returns the participants' public keys as a triple.
func enforcementKeysTriple(t *testing.T, keys enforcementKeysForTest) *hd_keys.PublicKeyTriple {
	t.Helper()

	return hd_keys.NewPublicKeyTriple(
		keys.user.PublicKey(), keys.cosigner.PublicKey(), keys.lightningServer.PublicKey(),
	)
}

// newEnforcementOutputForTest builds a ToClientHTLCEnforcementOutput over keys
// committing to paymentHashes, expiring after the fixture timelock.
func newEnforcementOutputForTest(
	t *testing.T,
	keys enforcementKeysForTest,
	paymentHashes [][]byte,
) *ToClientHTLCEnforcementOutput {
	t.Helper()

	setSpendDelaysForTest(t, fixtureTimelock)
	output, err := NewToClientHTLCEnforcementOutput(
		enforcementKeysTriple(t, keys), fixturePath, keys.revocation.PubKey(),
		paymentHashes,
	)
	require.NoError(t, err)
	return output
}

// Signing a spend through the non-collaborative leaf panics before the
// expiration; the spend is accepted after it.
func TestToClientHTLCEnforcementOutput_NonCollaborativeSpend(t *testing.T) {
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
			keys := newEnforcementKeysForTest(t)
			output := newEnforcementOutputForTest(t, keys, [][]byte{paymentHash[:]})

			prevTx := fundingTx(t, output, fixtureAmount)
			spendTx := spendingTx(prevTx, tt.sequence)

			compute := func() ([]byte, error) {
				return ComputeToClientHTLCEnforcementNonCollaborativeFullSignature(
					0,
					spendTx,
					testbtcscript.SigHashesFor(spendTx, prevTx.TxOut[0]),
					keys.user,
					keys.cosigner,
					enforcementKeysTriple(t, keys),
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

			witness, err := AssembleToClientHTLCEnforcementNonCollaborativeWitness(
				finalSignature,
				enforcementKeysTriple(t, keys),
				fixturePath,
				branchHash,
			)
			require.NoError(t, err)
			spendTx.TxIn[0].Witness = witness

			require.NoError(t, testbtcscript.VerifyInput(t, spendTx, prevTx.TxOut[0], 0))

			// Fee estimation sizes spends with the longest witness, so a real
			// witness must never exceed it.
			longestWitness, err := LongestToClientHTLCEnforcementNonCollaborativeWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				spendTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

func TestToClientHTLCEnforcementOutput_Preimage(t *testing.T) {
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
			keys := newEnforcementKeysForTest(t)
			output := newEnforcementOutputForTest(t, keys, tt.paymentHashes)

			prevTx := fundingTx(t, output, fixtureAmount)

			// The preimage spend has no relative timelock.
			claimTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			SignToClientHTLCEnforcementPreimageForTest(
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
			longestWitness, err := LongestToClientHTLCEnforcementPreimageWitness(
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
func TestToClientHTLCEnforcementOutput_VerifyPreimagePartialSignature(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newEnforcementKeysForTest(t)
	output := newEnforcementOutputForTest(t, keys, [][]byte{paymentHash[:]})

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

// SignToClientHTLCEnforcementPreimageForTest signs a preimage leaf spend the way the
// user completes it: the counterparty partials are computed and verified, then
// combined with the user's and placed in the witness with the preimages.
func SignToClientHTLCEnforcementPreimageForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToClientHTLCEnforcementOutput,
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

func TestToClientHTLCEnforcementOutput_CollaborativeSpend(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newEnforcementKeysForTest(t)
	output := newEnforcementOutputForTest(t, keys, [][]byte{paymentHash[:]})

	prevTx := fundingTx(t, output, fixtureAmount)
	spendTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

	SignToClientHTLCEnforcementCollaborativeSpendForTest(
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
	longestWitness, err := LongestToClientHTLCEnforcementCollaborativeWitness()
	require.NoError(t, err)
	require.LessOrEqual(t, spendTx.TxIn[0].Witness.SerializeSize(), longestWitness.SerializeSize())
}

// SignToClientHTLCEnforcementCollaborativeSpendForTest signs the collaborative key path
// spend: every participant contributes its partial through the model and the
// lightning server verifies them, combines them and assembles the witness.
func SignToClientHTLCEnforcementCollaborativeSpendForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToClientHTLCEnforcementOutput,
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
func TestToClientHTLCEnforcementOutput_VerifyCollaborativePartialSignature(t *testing.T) {
	paymentHash := sha256.Sum256([]byte("preimage"))
	keys := newEnforcementKeysForTest(t)
	output := newEnforcementOutputForTest(t, keys, [][]byte{paymentHash[:]})

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

// The lightning server's revocation spend only verifies with the revocation key
// the user revealed when revoking the state.
func TestToClientHTLCEnforcementOutput_RevocationSpend(t *testing.T) {
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
			keys := newEnforcementKeysForTest(t)
			output := newEnforcementOutputForTest(t, keys, [][]byte{paymentHash[:]})

			prevTx := fundingTx(t, output, fixtureAmount)

			// The revocation spend has no relative timelock.
			penaltyTx := spendingTx(prevTx, wire.MaxTxInSequenceNum)

			SignToClientHTLCEnforcementRevocationForTest(
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
			longestWitness, err := LongestToClientHTLCEnforcementRevocationWitness()
			require.NoError(t, err)
			require.LessOrEqual(
				t,
				penaltyTx.TxIn[0].Witness.SerializeSize(),
				longestWitness.SerializeSize(),
			)
		})
	}
}

// TestToClientHTLCEnforcementOutput_CollaborativeCrossCheck pins the fully
// signed collaborative (3-of-3 key path) spending tx, which is fully
// deterministic: keys, transaction and session ids are all fixed. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this exact
// transaction from the same inputs: any update to the pinned values here (keys,
// fixture parameters, session ids, signedTxHex) must be replicated there.
//
// The non-collaborative and revocation spends have no cross check: both sides
// run their MuSig2 session internally, so their signatures are not reproducible.
func TestToClientHTLCEnforcementOutput_CollaborativeCrossCheck(t *testing.T) {
	const signedTxHex = "020000000001016aa7537303ab7e433df84966134450ddf2e5afe798e1dbfce7ebffdf5e38187c0000000000ffffffff01b882010000000000225120059fe5505f8ce219450e48d5550c2682f7f9d397540b320823dff2ce2051ecd70140ef0db85ca6a70b5508d762fbd3641697614883990eee2a9cfe31203aa4e8ea1f900a4d68a61ade8a136f5e235d94fe12c8f76d0ddb5a1bc6942bcbde0620befa00000000" //nolint:lll

	firstPaymentHash := sha256.Sum256(bytes.Repeat([]byte{0xaa}, 32))
	secondPaymentHash := sha256.Sum256(bytes.Repeat([]byte{0xbb}, 32))
	keys := newEnforcementKeysForTest(t)
	output := newEnforcementOutputForTest(
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

// TestToClientHTLCEnforcementOutput_PreimageCrossCheck pins the fully signed
// preimage claim over two payment hashes, deterministic for the same reasons as
// the collaborative spend. The backend's
// TransactionSchemeSignedTransactionLibwalletCrossTest must produce this exact
// transaction from the same inputs: any update to the pinned values here (keys,
// fixture parameters, session ids, preimages, signedTxHex) must be replicated
// there.
func TestToClientHTLCEnforcementOutput_PreimageCrossCheck(t *testing.T) {
	const signedTxHex = "020000000001016aa7537303ab7e433df84966134450ddf2e5afe798e1dbfce7ebffdf5e38187c0000000000ffffffff01b882010000000000225120059fe5505f8ce219450e48d5550c2682f7f9d397540b320823dff2ce2051ecd70520bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb20aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa406b9d248361e422a83e53cda6170699ca688124040a461fe67908f7813a11ecdd1ab3e0ef9d5fb7e5ca64e1acd274a08e6d0ad68b91e67f403d97cf357427ab7b702086a4daa3b2f898e25068339eed0d2f669f95c5722e3513bab3c6fc9b732ba91bad82012088a820e0e77a507412b120f6ede61f62295b1a7b2ff19d3dcc8f7253e51663470c888e8882012088a8204ca14526b2751b640d549ce7caf8ac39438592211a0ec370064d57666a682ad68761c186a4daa3b2f898e25068339eed0d2f669f95c5722e3513bab3c6fc9b732ba91b3167b34747d0856e22b165ad39951c78a7588b1f5dbe0a54ad7948c0cc834c8b01c69a79b9424c877ff0d749ed1f47075c65b190fb4b7103a7d294686d5131fc00000000" //nolint:lll

	firstPreimage := bytes.Repeat([]byte{0xaa}, 32)
	firstPaymentHash := sha256.Sum256(firstPreimage)
	secondPreimage := bytes.Repeat([]byte{0xbb}, 32)
	secondPaymentHash := sha256.Sum256(secondPreimage)
	keys := newEnforcementKeysForTest(t)
	output := newEnforcementOutputForTest(
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

// SignToClientHTLCEnforcementRevocationForTest assembles the witness for a spend
// through the revocation leaf, which the lightning server signs with the
// revealed revocation key. Both partial signatures of the 2-of-2 MuSig2 are
// computed locally.
func SignToClientHTLCEnforcementRevocationForTest(
	t *testing.T,
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	output *ToClientHTLCEnforcementOutput,
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
		tapScriptTree, toClientHTLCEnforcementRevocationLeafIndex, index, tx, sigHashes,
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
		tapScriptTree, toClientHTLCEnforcementRevocationLeafIndex,
	)
	require.NoError(t, err)

	tx.TxIn[index].Witness = wire.TxWitness{
		signature,
		tapScriptTree.LeafMerkleProofs[toClientHTLCEnforcementRevocationLeafIndex].Script,
		controlBlock,
	}
}
