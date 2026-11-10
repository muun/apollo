package libsecp256k1

import (
	"crypto/sha256"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/stretchr/testify/require"
)

func TestSignSchnorrMatchesBtcec(t *testing.T) {
	secretKey := sha256.Sum256([]byte("signing key"))
	message := sha256.Sum256([]byte("a message to sign"))

	signature, err := SignSchnorr(message[:], secretKey[:])
	require.NoError(t, err)

	// Passing no auxiliary randomness sends btcec down BIP340 default signing,
	// which is what libsecp256k1 always does. Without the option btcec derives
	// its nonce with RFC6979 instead, and the signatures differ.
	btcecSecretKey, _ := btcec.PrivKeyFromBytes(secretKey[:])
	expected, err := schnorr.Sign( //nolint:forbidigo // btcec is the reference
		btcecSecretKey, message[:], schnorr.CustomNonce([32]byte{}),
	)
	require.NoError(t, err)

	require.Equal(t, expected.Serialize(), signature)
}

func TestVerifySignature(t *testing.T) {
	secretKey := sha256.Sum256([]byte("signing key"))
	message := sha256.Sum256([]byte("a message to sign"))

	_, publicKey := btcec.PrivKeyFromBytes(secretKey[:])
	xOnlyPublicKey := schnorr.SerializePubKey(publicKey)

	signature, err := SignSchnorr(message[:], secretKey[:])
	require.NoError(t, err)

	err = VerifySignature(signature, message[:], xOnlyPublicKey)
	require.NoError(t, err)

	otherMessage := sha256.Sum256([]byte("a different message"))
	err = VerifySignature(signature, otherMessage[:], xOnlyPublicKey)
	require.Error(t, err, "verification against another message")

	otherSecretKey := sha256.Sum256([]byte("other signing key"))
	_, otherPublicKey := btcec.PrivKeyFromBytes(otherSecretKey[:])
	otherKey := schnorr.SerializePubKey(otherPublicKey)
	err = VerifySignature(signature, message[:], otherKey)
	require.Error(t, err, "verification against another key")
}

func TestSchnorrRejectsBadInput(t *testing.T) {
	secretKey := sha256.Sum256([]byte("signing key"))
	message := sha256.Sum256([]byte("a message to sign"))

	_, err := SignSchnorr(message[:15], secretKey[:])
	require.Error(t, err, "a short message")

	_, err = SignSchnorr(message[:], secretKey[:SecretKeySize-1])
	require.Error(t, err, "a short secret key")

	signature, err := SignSchnorr(message[:], secretKey[:])
	require.NoError(t, err)

	_, publicKey := btcec.PrivKeyFromBytes(secretKey[:])
	xOnlyPublicKey := schnorr.SerializePubKey(publicKey)

	err = VerifySignature(signature[:SignatureSize-1], message[:], xOnlyPublicKey)
	require.Error(t, err, "a short signature")

	err = VerifySignature(signature, message[:], xOnlyPublicKey[:16])
	require.Error(t, err, "a short public key")
}
