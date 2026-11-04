package libsecp256k1

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"testing"

	btcecmusig2 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/stretchr/testify/require"
)

func TestGeneratePublicNonceMatchesBtcec(t *testing.T) {
	publicKeys, btcecKeys := signerKeys(3)

	for i := range publicKeys {
		sessionID := sha256.Sum256(fmt.Appendf(nil, "session %d", i))

		nonce, err := GeneratePublicNonce(sessionID[:], publicKeys[i])
		require.NoError(t, err)

		expected, err := btcecmusig2.GenNonces(
			btcecmusig2.WithPublicKey(btcecKeys[i]),
			btcecmusig2.WithCustomRand(bytes.NewBuffer(sessionID[:])),
		)
		require.NoError(t, err)

		require.Equal(t, expected.PubNonce[:], nonce)
	}
}

func TestAggregateNoncesMatchesBtcec(t *testing.T) {
	publicKeys, _ := signerKeys(3)

	nonces := make([][]byte, len(publicKeys))
	btcecNonces := make([][btcecmusig2.PubNonceSize]byte, len(publicKeys))

	for i := range publicKeys {
		sessionID := sha256.Sum256(fmt.Appendf(nil, "session %d", i))

		nonce, err := GeneratePublicNonce(sessionID[:], publicKeys[i])
		require.NoError(t, err)

		nonces[i] = nonce
		btcecNonces[i] = [btcecmusig2.PubNonceSize]byte(nonce)
	}

	aggregate, err := AggregateNonces(nonces)
	require.NoError(t, err)

	expected, err := btcecmusig2.AggregateNonces(btcecNonces)
	require.NoError(t, err)

	require.Equal(t, expected[:], aggregate)
}

func TestGeneratePublicNonceRejectsBadInput(t *testing.T) {
	publicKeys, _ := signerKeys(1)
	sessionID := sha256.Sum256([]byte("session"))

	// btcec silently zero pads a short session id instead of rejecting it.
	_, err := GeneratePublicNonce(sessionID[:16], publicKeys[0])
	require.Error(t, err, "a short session id")

	// libsecp256k1 rejects an all zero session id as a defense against a faulty
	// random number generator.
	_, err = GeneratePublicNonce(make([]byte, SessionIDSize), publicKeys[0])
	require.Error(t, err, "an all zero session id")

	_, err = GeneratePublicNonce(sessionID[:], publicKeys[0][:PublicKeySize-1])
	require.Error(t, err, "a short public key")
}

func TestAggregateNoncesRejectsBadInput(t *testing.T) {
	_, err := AggregateNonces(nil)
	require.Error(t, err, "aggregating no nonces")

	_, err = AggregateNonces([][]byte{make([]byte, PublicNonceSize-1)})
	require.Error(t, err, "a short public nonce")
}
