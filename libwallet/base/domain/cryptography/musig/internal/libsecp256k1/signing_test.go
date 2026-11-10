package libsecp256k1

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	btcecmusig2 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/stretchr/testify/require"
)

func TestSignMatchesBtcec(t *testing.T) {
	for _, signerCount := range []int{2, 3} {
		t.Run(fmt.Sprintf("%d signers", signerCount), func(t *testing.T) {
			signers := newSigners(t, signerCount)
			message := sha256.Sum256([]byte("a transaction to sign"))

			aggregation, err := AggregatePublicKeys(signers.publicKeys)
			require.NoError(t, err)

			aggregateNonce, err := AggregateNonces(signers.publicNonces)
			require.NoError(t, err)

			for i := range signers.publicKeys {
				signature, err := aggregation.Sign(
					message[:],
					aggregateNonce,
					signers.secretKeys[i],
					signers.sessionIDs[i],
				)
				require.NoError(t, err)

				expected := signers.signWithBtcec(t, i, message)
				require.Equal(t, expected, signature, "signer %d", i)
			}
		})
	}
}

func TestVerifyPartialSignature(t *testing.T) {
	signers := newSigners(t, 3)
	message := sha256.Sum256([]byte("a transaction to sign"))

	aggregation, err := AggregatePublicKeys(signers.publicKeys)
	require.NoError(t, err)

	aggregateNonce, err := AggregateNonces(signers.publicNonces)
	require.NoError(t, err)

	signature, err := aggregation.Sign(
		message[:], aggregateNonce, signers.secretKeys[0], signers.sessionIDs[0],
	)
	require.NoError(t, err)

	err = aggregation.VerifyPartialSignature(
		message[:], aggregateNonce, signature,
		signers.publicNonces[0], signers.publicKeys[0],
	)
	require.NoError(t, err)

	// The same partial signature does not verify against another signer's nonce
	// and key.
	err = aggregation.VerifyPartialSignature(
		message[:], aggregateNonce, signature,
		signers.publicNonces[1], signers.publicKeys[1],
	)
	require.Error(t, err, "verification against another signer")

	otherMessage := sha256.Sum256([]byte("a different transaction"))
	err = aggregation.VerifyPartialSignature(
		otherMessage[:], aggregateNonce, signature,
		signers.publicNonces[0], signers.publicKeys[0],
	)
	require.Error(t, err, "verification against another message")
}

func TestSignIsDeterministic(t *testing.T) {
	signers := newSigners(t, 2)
	message := sha256.Sum256([]byte("a transaction to sign"))

	aggregation, err := AggregatePublicKeys(signers.publicKeys)
	require.NoError(t, err)

	aggregateNonce, err := AggregateNonces(signers.publicNonces)
	require.NoError(t, err)

	sign := func() ([]byte, error) {
		return aggregation.Sign(
			message[:], aggregateNonce,
			signers.secretKeys[0], signers.sessionIDs[0],
		)
	}

	first, err := sign()
	require.NoError(t, err)

	// Signing derives a fresh secret nonce from the session id every call, so
	// the same inputs always produce the same partial signature.
	second, err := sign()
	require.NoError(t, err)

	require.Equal(t, first, second)
}

func TestSignRejectsBadInput(t *testing.T) {
	signers := newSigners(t, 2)
	message := sha256.Sum256([]byte("a transaction to sign"))

	aggregation, err := AggregatePublicKeys(signers.publicKeys)
	require.NoError(t, err)

	aggregateNonce, err := AggregateNonces(signers.publicNonces)
	require.NoError(t, err)

	_, err = aggregation.Sign(
		message[:15], aggregateNonce, signers.secretKeys[0], signers.sessionIDs[0],
	)
	require.Error(t, err, "a short message")

	_, err = aggregation.Sign(
		message[:], aggregateNonce[:PublicNonceSize-1],
		signers.secretKeys[0], signers.sessionIDs[0],
	)
	require.Error(t, err, "a short aggregate nonce")

	_, err = aggregation.Sign(
		message[:], aggregateNonce, signers.secretKeys[0][:SecretKeySize-1],
		signers.sessionIDs[0],
	)
	require.Error(t, err, "a short secret key")

	_, err = aggregation.Sign(
		message[:], aggregateNonce, signers.secretKeys[0], signers.sessionIDs[0][:16],
	)
	require.Error(t, err, "a short session id")
}

func TestCombineSignaturesMatchesBtcec(t *testing.T) {
	for _, signerCount := range []int{2, 3} {
		t.Run(fmt.Sprintf("%d signers", signerCount), func(t *testing.T) {
			signers := newSigners(t, signerCount)
			message := sha256.Sum256([]byte("a transaction to sign"))

			aggregation, partialSignatures := signers.sign(t, message)

			aggregateNonce, err := AggregateNonces(signers.publicNonces)
			require.NoError(t, err)

			signature, err := aggregation.CombineSignatures(
				message[:], aggregateNonce, partialSignatures,
			)
			require.NoError(t, err)

			expected := signers.combineWithBtcec(t, message)
			require.Equal(t, expected, signature)
		})
	}
}

func TestCombineSignaturesVerifiesTheResult(t *testing.T) {
	signers := newSigners(t, 3)
	message := sha256.Sum256([]byte("a transaction to sign"))

	aggregation, partialSignatures := signers.sign(t, message)

	aggregateNonce, err := AggregateNonces(signers.publicNonces)
	require.NoError(t, err)

	// A partial signature left out, so the sum is not the signature the
	// aggregate key should have produced.
	_, err = aggregation.CombineSignatures(
		message[:], aggregateNonce, partialSignatures[:len(partialSignatures)-1],
	)
	require.Error(t, err, "combining an incomplete set of partial signatures")

	// A partial signature from a different session, valid on its own but not
	// for this one.
	otherMessage := sha256.Sum256([]byte("a different transaction"))
	otherPartialSignature, err := aggregation.Sign(
		otherMessage[:], aggregateNonce, signers.secretKeys[0], signers.sessionIDs[0],
	)
	require.NoError(t, err)

	tampered := append([][]byte{otherPartialSignature}, partialSignatures[1:]...)
	_, err = aggregation.CombineSignatures(message[:], aggregateNonce, tampered)
	require.Error(t, err, "combining a partial signature from another session")
}

func TestCombineSignaturesRejectsBadInput(t *testing.T) {
	signers := newSigners(t, 2)
	message := sha256.Sum256([]byte("a transaction to sign"))

	aggregation, partialSignatures := signers.sign(t, message)

	aggregateNonce, err := AggregateNonces(signers.publicNonces)
	require.NoError(t, err)

	_, err = aggregation.CombineSignatures(message[:], aggregateNonce, nil)
	require.Error(t, err, "combining no partial signatures")

	short := [][]byte{
		partialSignatures[0][:PartialSignatureSize-1], partialSignatures[1],
	}
	_, err = aggregation.CombineSignatures(message[:], aggregateNonce, short)
	require.Error(t, err, "a short partial signature")
}

// signers holds a set of signers that have exchanged public nonces, in both this
// package's representation and btcec's.
type signers struct {
	secretKeys   [][]byte
	publicKeys   [][]byte
	sessionIDs   [][]byte
	publicNonces [][]byte

	btcecSecretKeys []*btcec.PrivateKey
	btcecPublicKeys []*btcec.PublicKey
	btcecNonces     []*btcecmusig2.Nonces
}

func newSigners(t *testing.T, count int) *signers {
	t.Helper()

	s := &signers{}

	for i := range count {
		secretKey := sha256.Sum256(fmt.Appendf(nil, "signing key %d", i))
		sessionID := sha256.Sum256(fmt.Appendf(nil, "signing session %d", i))

		btcecSecretKey, btcecPublicKey := btcec.PrivKeyFromBytes(secretKey[:])
		publicKey := btcecPublicKey.SerializeCompressed()

		publicNonce, err := GeneratePublicNonce(sessionID[:], publicKey)
		require.NoError(t, err)

		btcecNonce, err := btcecmusig2.GenNonces(
			btcecmusig2.WithPublicKey(btcecPublicKey),
			btcecmusig2.WithCustomRand(bytes.NewBuffer(sessionID[:])),
		)
		require.NoError(t, err)

		s.secretKeys = append(s.secretKeys, secretKey[:])
		s.publicKeys = append(s.publicKeys, publicKey)
		s.sessionIDs = append(s.sessionIDs, sessionID[:])
		s.publicNonces = append(s.publicNonces, publicNonce)

		s.btcecSecretKeys = append(s.btcecSecretKeys, btcecSecretKey)
		s.btcecPublicKeys = append(s.btcecPublicKeys, btcecPublicKey)
		s.btcecNonces = append(s.btcecNonces, btcecNonce)
	}

	return s
}

// signWithBtcec produces one signer's partial signature through the Go
// implementation, with key sorting off so that both sides aggregate the keys in
// the same order.
func (s *signers) signWithBtcec(t *testing.T, signer int, message [32]byte) []byte {
	t.Helper()

	signingContext, err := btcecmusig2.NewContext(
		s.btcecSecretKeys[signer], false,
		btcecmusig2.WithKnownSigners(s.btcecPublicKeys),
	)
	require.NoError(t, err)

	session, err := signingContext.NewSession(
		btcecmusig2.WithPreGeneratedNonce(s.btcecNonces[signer]),
	)
	require.NoError(t, err)

	for other := range s.btcecNonces {
		if other == signer {
			continue
		}

		_, err := session.RegisterPubNonce(s.btcecNonces[other].PubNonce)
		require.NoError(t, err)
	}

	signature, err := session.Sign(message)
	require.NoError(t, err)

	serialized := signature.S.Bytes()

	return serialized[:]
}

// sign has every signer produce its partial signature over message.
func (s *signers) sign(t *testing.T, message [32]byte) (*KeyAggregation, [][]byte) {
	t.Helper()

	aggregation, err := AggregatePublicKeys(s.publicKeys)
	require.NoError(t, err)

	aggregateNonce, err := AggregateNonces(s.publicNonces)
	require.NoError(t, err)

	partialSignatures := make([][]byte, len(s.publicKeys))
	for i := range s.publicKeys {
		partialSignatures[i], err = aggregation.Sign(
			message[:], aggregateNonce, s.secretKeys[i], s.sessionIDs[i],
		)
		require.NoError(t, err)
	}

	return aggregation, partialSignatures
}

// combineWithBtcec produces the final signature through the Go implementation.
func (s *signers) combineWithBtcec(t *testing.T, message [32]byte) []byte {
	t.Helper()

	signingContext, err := btcecmusig2.NewContext(
		s.btcecSecretKeys[0], false,
		btcecmusig2.WithKnownSigners(s.btcecPublicKeys),
	)
	require.NoError(t, err)

	session, err := signingContext.NewSession(
		btcecmusig2.WithPreGeneratedNonce(s.btcecNonces[0]),
	)
	require.NoError(t, err)

	for other := 1; other < len(s.btcecNonces); other++ {
		_, err := session.RegisterPubNonce(s.btcecNonces[other].PubNonce)
		require.NoError(t, err)
	}

	_, err = session.Sign(message)
	require.NoError(t, err)

	for other := 1; other < len(s.btcecSecretKeys); other++ {
		partialSignature := s.signWithBtcec(t, other, message)

		var scalar btcecmusig2.PartialSignature
		err := scalar.Decode(bytes.NewReader(partialSignature))
		require.NoError(t, err)

		_, err = session.CombineSig(&scalar)
		require.NoError(t, err)
	}

	return session.FinalSig().Serialize()
}
