package musig

import (
	"crypto/sha256"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
)

// TestComputePartialAndFinalSignature2Of2 drives the two-step 2-of-2 flow
// (muun partial-signs first, the user signs last and combines) against pinned
// vectors. The vectors are the same as the "sanity 1 (v100)" case of
// TestMuSig2Tests2of2, so the v100-only functions are asserted byte-compatible
// with the deprecated version-parametric ones.
func TestComputePartialAndFinalSignature2Of2(t *testing.T) {
	t.Parallel()

	userKey := hexDecode("507d881f0b5e1b12423cb0c84a196fb24227f3fe1540a1c7b20bf78d83de4533")
	muunKey := hexDecode("b6f14c73ee5269f5a13a11f48ad54306293ee134e924f680fcd35f615881105b")
	msg := hexDecode("ef2ecc1f48c0b28ccaf8f3a8c6477740d869964ebc152a2c5f93f19e7b84b103")
	userSessionID := hexDecode("5c9360026e39ad06251a27916dcf086a7b2deb6789c5dcd75ba10e540cf37e13")
	muunSessionID := hexDecode("cad3ec6737e2fb125d976bfe382441c59c6a4d46382bfab75e9d3f1b43a9b0a7")
	tweak := KeySpendOnlyTweak()

	userPublicKeyBytes := secp256k1.PrivKeyFromBytes(userKey).PubKey().SerializeCompressed()
	muunPublicKeyBytes := secp256k1.PrivKeyFromBytes(muunKey).PubKey().SerializeCompressed()

	combinedKey, err := Musig2CombinePubKeysWithTweak(
		Musig2v100,
		[][]byte{userPublicKeyBytes, muunPublicKeyBytes},
		tweak,
	)
	require.NoError(t, err)
	require.Equal(
		t,
		"03c367d7ef80b10687820dda279e0e6054dadccc30550c8eae7e21a945069cb7e0",
		hex.EncodeToString(combinedKey.FinalKey.SerializeCompressed()),
	)

	userNonce, err := MuSig2GenerateNonce(Musig2v100, userSessionID, userPublicKeyBytes)
	require.NoError(t, err)
	muunNonce, err := MuSig2GenerateNonce(Musig2v100, muunSessionID, muunPublicKeyBytes)
	require.NoError(t, err)

	muunPartialSignature, err := ComputePartialSignature2Of2(
		msg,
		muunKey,
		userPublicKeyBytes,
		userNonce.PubNonce[:],
		muunSessionID,
		tweak,
	)
	require.NoError(t, err)
	require.Equal(
		t,
		"18422b132ac447af9e98db197d45becb26c83aa4fa658312dd8357e1e8309ce4",
		hex.EncodeToString(muunPartialSignature),
	)

	fullSignature, err := ComputeFinalSignature2Of2(
		msg,
		userKey,
		muunPublicKeyBytes,
		muunNonce.PubNonce[:],
		muunPartialSignature,
		userSessionID,
		tweak,
	)
	require.NoError(t, err)
	require.Equal(
		t,
		"5e9034fe55b901308dd4751855e50a2181ec264b76fb5a986c87e78084202a9a83588f0e45e103645a01f313d7f3b03a5fcc5bc68ff7c41ae4d451271441d20b", //nolint:lll
		hex.EncodeToString(fullSignature),
	)

	assertValidSignature(t, msg, combinedKey.FinalKey.SerializeCompressed(), fullSignature)
}

// TestComputeFullSignature2Of2 runs a full 2-of-2 session with random keys and
// asserts the aggregated signature verifies against the combined key.
func TestComputeFullSignature2Of2(t *testing.T) {
	t.Parallel()

	signerAKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)
	signerBKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)

	toSign := sha256.Sum256([]byte("muun-2-of-2"))
	tweak := KeySpendOnlyTweak()

	combinedKey, err := Musig2CombinePubKeysWithTweak(
		Musig2v100,
		[][]byte{
			signerAKey.PubKey().SerializeCompressed(),
			signerBKey.PubKey().SerializeCompressed(),
		},
		tweak,
	)
	require.NoError(t, err)

	fullSignature, err := ComputeFullSignature2Of2(toSign[:], signerAKey, signerBKey, tweak)
	require.NoError(t, err)

	assertValidSignature(
		t, toSign[:], combinedKey.FinalKey.SerializeCompressed(), fullSignature,
	)
}

// TestComputeFullSignature2Of2WithScriptPath runs a full 2-of-2 session with a
// tapscript tweak and asserts the aggregated signature verifies against the
// scriptPath-committed combined key.
func TestComputeFullSignature2Of2WithScriptPath(t *testing.T) {
	t.Parallel()

	signerAKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)
	signerBKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)

	toSign := sha256.Sum256([]byte("muun-2-of-2"))
	scriptPath := RandomSessionID() // any random 32 bytes
	tweak := TapScriptTweak(scriptPath[:])

	combinedKey, err := Musig2CombinePubKeysWithTweak(
		Musig2v100,
		[][]byte{
			signerAKey.PubKey().SerializeCompressed(),
			signerBKey.PubKey().SerializeCompressed(),
		},
		tweak,
	)
	require.NoError(t, err)

	fullSignature, err := ComputeFullSignature2Of2(toSign[:], signerAKey, signerBKey, tweak)
	require.NoError(t, err)

	assertValidSignature(
		t, toSign[:], combinedKey.FinalKey.SerializeCompressed(), fullSignature,
	)
}
