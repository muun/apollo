package musig

import (
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography/musig2v040"
)

// TestFinalNonceMatchesUpstream asserts that finalNonceV040 reproduces the final
// signing nonce musig2v040 derives on its own, so that combining stays in step
// with signing.
// A signer's partial signature carries the nonce that was actually used in its R
// field, which is what makes the comparison possible without pinning values.
func TestFinalNonceMatchesUpstream(t *testing.T) {
	t.Parallel()

	msg := [32]byte{1, 2, 3}
	tweak := KeySpendOnlyTweak()

	userKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)
	cosignerKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)

	allPubKeys := [][]byte{
		userKey.PubKey().SerializeCompressed(),
		cosignerKey.PubKey().SerializeCompressed(),
	}
	signerPublicKeys, err := MuSig2ParsePubKeys(Musig2v040Muun, allPubKeys)
	require.NoError(t, err)

	userSessionID := RandomSessionID()
	cosignerSessionID := RandomSessionID()

	userNonce, err := MuSig2GenerateNonce(
		Musig2v040Muun, userSessionID[:], allPubKeys[0],
	)
	require.NoError(t, err)
	cosignerNonce, err := MuSig2GenerateNonce(
		Musig2v040Muun, cosignerSessionID[:], allPubKeys[1],
	)
	require.NoError(t, err)

	// signing derives the final nonce internally and hands it back as the R of
	// the partial signature
	session, err := createSessionV040(
		userKey, signerPublicKeys, tweak, userNonce,
	)
	require.NoError(t, err)

	haveAllNonces, err := session.RegisterPubNonce(cosignerNonce.PubNonce)
	require.NoError(t, err)
	require.True(t, haveAllNonces)

	partialSignature, err := signV040(session, msg)
	require.NoError(t, err)

	aggregateNonce, err := musig2v040.AggregateNonces(
		[][66]byte{userNonce.PubNonce, cosignerNonce.PubNonce},
	)
	require.NoError(t, err)

	combinedKey, err := MuSig2CombineKeys(
		Musig2v040Muun, signerPublicKeys, tweak,
	)
	require.NoError(t, err)

	finalNonce, err := finalNonceV040(aggregateNonce, combinedKey.FinalKey, msg)
	require.NoError(t, err)

	require.True(t, finalNonce.IsEqual(partialSignature.R))
}
