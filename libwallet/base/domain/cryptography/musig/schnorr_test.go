package musig

import (
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
)

func TestSignSchnorr(t *testing.T) {
	t.Parallel()

	privateKeyBytes := hexDecode(
		"507d881f0b5e1b12423cb0c84a196fb24227f3fe1540a1c7b20bf78d83de4533")
	data := hexDecode(
		"ef2ecc1f48c0b28ccaf8f3a8c6477740d869964ebc152a2c5f93f19e7b84b103")

	signature, err := SignSchnorr(privateKeyBytes, data)
	require.NoError(t, err)
	require.Len(t, signature, schnorr.SignatureSize)

	// The signature must verify under the signer's x-only public key.
	sig, err := schnorr.ParseSignature(signature)
	require.NoError(t, err)

	publicKey := secp256k1.PrivKeyFromBytes(privateKeyBytes).PubKey()
	require.True(t, sig.Verify(data, publicKey))
}

// TestSignSchnorrCrossCheckWithJava asserts the signature byte-exact against a pinned
// vector. SignSchnorr is deterministic, and the same vector is pinned by
// testCrossCheckWithLibwallet in backend-libs/taproot4j's SchnorrTest, so both sides
// of the JNI bridge assert the same contract.
func TestSignSchnorrCrossCheckWithJava(t *testing.T) {
	t.Parallel()

	privateKeyBytes := hexDecode(
		"507d881f0b5e1b12423cb0c84a196fb24227f3fe1540a1c7b20bf78d83de4533")
	data := hexDecode(
		"ef2ecc1f48c0b28ccaf8f3a8c6477740d869964ebc152a2c5f93f19e7b84b103")

	signature, err := SignSchnorr(privateKeyBytes, data)
	require.NoError(t, err)

	require.Equal(
		t,
		"6ab848a5e59613b028675e6461363472532804bc89fe3b54b98f5e014ae4861e"+
			"9a0991034de435e0661d351078f455da2954a1d685e3e3aa49570caaa0eaa3e9",
		hex.EncodeToString(signature),
	)
}

func TestSignSchnorrRejectsNon32ByteData(t *testing.T) {
	t.Parallel()

	privateKeyBytes := hexDecode(
		"507d881f0b5e1b12423cb0c84a196fb24227f3fe1540a1c7b20bf78d83de4533")

	_, err := SignSchnorr(privateKeyBytes, []byte{0x01, 0x02, 0x03})
	require.Error(t, err)
}

// TestVerifySignatureTellsInvalidFromMalformed pins the distinction the JNI
// bridge depends on: it reports an invalid signature to Java as false, and only
// throws when the inputs could not be read.
func TestVerifySignatureTellsInvalidFromMalformed(t *testing.T) {
	t.Parallel()

	privateKeyBytes := hexDecode(
		"507d881f0b5e1b12423cb0c84a196fb24227f3fe1540a1c7b20bf78d83de4533")
	data := hexDecode(
		"ef2ecc1f48c0b28ccaf8f3a8c6477740d869964ebc152a2c5f93f19e7b84b103")

	privateKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	publicKey := privateKey.PubKey().SerializeCompressed()

	signature, err := SignSchnorr(privateKeyBytes, data)
	require.NoError(t, err)

	valid, err := VerifySignature(Musig2v100, data, publicKey, signature)
	require.NoError(t, err)
	require.True(t, valid)

	otherData := hexDecode(
		"1111111111111111111111111111111111111111111111111111111111111111")

	valid, err = VerifySignature(Musig2v100, otherData, publicKey, signature)
	require.NoError(t, err, "a signature that does not verify is not an error")
	require.False(t, valid)

	_, err = VerifySignature(Musig2v100, data, publicKey, signature[:32])
	require.Error(t, err, "a signature that cannot be read is an error")
}
