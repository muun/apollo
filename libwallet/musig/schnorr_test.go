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
		"a560ccf69c0e05b8c61114182c458e19e70da2e89192afb04359a19292bf7f62"+
			"ea026c4ddc20d0e7b9e1279c767d4fa0b347fda88eee0eca7c1d6fce0e499f98",
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
