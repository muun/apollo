package musig

import (
	"crypto/sha256"
	"encoding/hex"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
)

func TestCombineAndSign(t *testing.T) {
	roundTrip(t, nil)
}

func TestCombineAndSignWithScriptPath(t *testing.T) {
	scriptPath := RandomSessionID() // any random 32 bytes
	roundTrip(t, scriptPath[:])
}

// TestDroppingScriptPathFails asserts that combining pubKeys with a scriptPath
// fails to produce a valid signature when the scriptPath is not given to the
// signing functions.
func TestDroppingScriptPathFails(t *testing.T) {
	session := newRandomSigningSession(t)
	scriptPath := RandomSessionID() // any random 32 bytes
	toSign := sha256.Sum256([]byte("hole"))

	tweakedKey, _ := session.combinedKeys(t, TapScriptTweak(scriptPath[:]))

	// partial and final signatures computed without the scriptPath yield a
	// signature that does not verify against the scriptPath-committed combined key
	muunPartialSignature, swapServerPartialSignature, fullSignature := session.sign(
		t, toSign[:], KeySpendOnlyTweak(),
	)

	valid, err := VerifySignature(Musig2v100, toSign[:], tweakedKey, fullSignature)
	require.NoError(t, err)
	require.False(t, valid)

	// finalizing with the scriptPath while the partials omitted it must fail loudly
	_, err = ComputeFinalSignature(
		Musig2v100,
		toSign[:],
		session.userKey,
		session.allPubKeys,
		[][]byte{session.muunNonce, session.swapServerNonce},
		[][]byte{muunPartialSignature, swapServerPartialSignature},
		session.userSessionID,
		TapScriptTweak(scriptPath[:]),
	)
	require.Error(t, err)
}

// TestComputePartialSignatureMissingNonceFails asserts that, in a 3-signer
// session, providing the nonce of only one of the two other participants fails
// instead of producing a partial signature.
func TestComputePartialSignatureMissingNonceFails(t *testing.T) {
	t.Parallel()

	session := newRandomSigningSession(t)
	toSign := sha256.Sum256([]byte("muun-3-of-3"))

	_, err := ComputePartialSignature(
		Musig2v100,
		toSign[:],
		session.muunKey,
		session.allPubKeys,
		[][]byte{session.userNonce},
		session.muunSessionID,
		KeySpendOnlyTweak(),
	)
	require.ErrorContains(t, err, "some nonces are missing")
}

// TestComputeFinalSignatureMissingPartialSignatureFails asserts that, in a
// 3-signer session, combining the partial signature of only one of the two
// other participants fails instead of producing a final signature.
func TestComputeFinalSignatureMissingPartialSignatureFails(t *testing.T) {
	t.Parallel()

	session := newRandomSigningSession(t)
	toSign := sha256.Sum256([]byte("muun-3-of-3"))

	muunPartialSignature, err := ComputePartialSignature(
		Musig2v100,
		toSign[:],
		session.muunKey,
		session.allPubKeys,
		[][]byte{session.userNonce, session.swapServerNonce},
		session.muunSessionID,
		KeySpendOnlyTweak(),
	)
	require.NoError(t, err)

	_, err = ComputeFinalSignature(
		Musig2v100,
		toSign[:],
		session.userKey,
		session.allPubKeys,
		[][]byte{session.muunNonce, session.swapServerNonce},
		[][]byte{muunPartialSignature},
		session.userSessionID,
		KeySpendOnlyTweak(),
	)
	require.ErrorContains(t, err, "some signatures are still missing")
}

func TestCrossCheckWithJavaWithKeySpendTweak(t *testing.T) {
	t.Parallel()

	crossCheckWithJava(
		t,
		KeySpendOnlyTweak(),
		"022ae899ba133dfde5661bdc53045f0c0aeffce6174a33c29c4aa1265e425e3e77",
		"d66bf9847ecf4837d3a6f9f591c4f64c053c03c79f971aa88ceb6cd7edad641b",
		"2cd6944762b86f502dc3762d35a338032846650c56f097aa46dd503d7f48c722",
		"a394cdca042d08ab1c25ead40deabe5eb1297d705b666cb93ae73e198dfbbd7ea37b005126142a57c0a8a07a779df7f13589141e2058253800fc5463b17c2ad5", //nolint:lll
	)
}

func TestCrossCheckWithJavaWithTapScriptTweak(t *testing.T) {
	t.Parallel()

	crossCheckWithJava(
		t,
		TapScriptTweak(
			hexDecode("7c2e1f3b5a4d6c8e9f0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60"),
		),
		"03db66ccb93a4f2f40825206182c4ccb79d14b2f15801140bdfdf86476eb060cd4",
		"58ca36bf7bc2d4a769185696e28bce6e1a7d42ce5a84a02ead2680deb988b95d",
		"a04a189d0f1336c73cf8a0fb6d906b46587b1df69b034163d2e1f2d873fcd0ca",
		"bc63134cb4f8b0747bb625f893c863faaf8fed56e377e167fe8f7739ef974716ce833a3d605b576abbe5e76850b9f3a9e5ac4e3dc4e25651045c372d65423014", //nolint:lll
	)
}

func TestCrossCheckWithJavaWithNoopTweak(t *testing.T) {
	t.Parallel()

	crossCheckWithJava(
		t,
		NoopTweak(),
		// Noop applies no tweak, so the combined key equals the internal key.
		"03495c41e6968547775527ad75bce0a011d7a06d4647c90269c654638d926b3cf4",
		"3836bef4525d6c1ff5f0aae1f080ef4693b804dfe8b3fcefe8126b9dc2c37a02",
		"b1105ae9f360ba019ce56f31bcd4dbf8d1cb7d5938862633d00defc8521e3729",
		"06629b3ab9f851069e85412345e88c23869c921ab2c27ddc4ad7268ef7ca8711d3544e1279106bbc0a303363beb8bc0afac262eeda93f88413cfc85d4de64373", //nolint:lll
	)
}

// roundTrip drives a full 3-of-3 round trip with random keys: muun and the
// swap server each produce a partial signature, then the user signs last and
// combines everything. Asserts the resulting signature is valid and that
// tampering with it breaks verification.
func roundTrip(t *testing.T, scriptPath []byte) {
	session := newRandomSigningSession(t)
	toSign := sha256.Sum256([]byte("muun-3-of-3"))

	tweak := KeySpendOnlyTweak()
	if scriptPath != nil {
		tweak = TapScriptTweak(scriptPath)
	}

	tweakedKey, _ := session.combinedKeys(t, tweak)

	_, _, fullSignature := session.sign(t, toSign[:], tweak)

	assertValidSignature(t, toSign[:], tweakedKey, fullSignature)
}

// pinnedMsg is the message signed by all the pinned cross-check vectors.
var pinnedMsg = hexDecode(
	"0102030000000000000000000000000000000000000000000000000000000000")

// crossCheckWithJava drives the full 3-signer flow against pinned vectors:
// the combined keys, public nonces, partial signatures and final signature
// are asserted byte-exact. The same vectors are pinned by testCrossCheckWithLibwallet
// in backend-libs/taproot4j's Musig3Of3Test, so both sides of the JNI bridge
// assert the same contract.
func crossCheckWithJava(
	t *testing.T,
	tweak *MuSig2Tweaks,
	expectedCombinedPub string,
	expectedMuunPartialSignature string,
	expectedSwapServerPartialSignature string,
	expectedFullSignature string,
) {
	t.Helper()

	session := newPinnedSigningSession(t)

	tweakedKey, internalKey := session.combinedKeys(t, tweak)
	require.Equal(t, expectedCombinedPub, hex.EncodeToString(tweakedKey))

	// the internal key is the aggregate before applying the taproot tweak,
	// so it is the same for both vectors
	require.Equal(
		t,
		"03495c41e6968547775527ad75bce0a011d7a06d4647c90269c654638d926b3cf4",
		hex.EncodeToString(internalKey),
	)

	// nonce generation does not depend on the scriptPath tweak, so the expected
	// nonces are the same for both vectors
	require.Equal(t, "033851b4c20a8d2724c0beb2aa9d870fb40b9808953fa0210b9e0968634e7df300027d3c122a2b5ef8a99e272252439b5c2973a3672154c1f75dac65d373a3f280dd", hex.EncodeToString(session.userNonce))       //nolint:lll
	require.Equal(t, "039d31580aa30e79b174b2b0170cb20e8fcf90a67e6cd26cff43e91f521a62d658025706076ac9aff93b1804499c7245f4d1bc684d4a29fb312cb1ffb654c9c616f1", hex.EncodeToString(session.muunNonce))       //nolint:lll
	require.Equal(t, "025e350dd6c5c9fa2717ac5270f01ad7b70cb6676d60d847e0f5b61d8b89b77d6c02fc9aa7163d82d55c8fa97305755976358b13c9e577dc82aeeef8064f84d1e41e", hex.EncodeToString(session.swapServerNonce)) //nolint:lll

	muunPartialSignature, swapServerPartialSignature, fullSignature := session.sign(
		t, pinnedMsg, tweak,
	)
	require.Equal(
		t,
		expectedMuunPartialSignature,
		hex.EncodeToString(muunPartialSignature),
	)
	require.Equal(
		t,
		expectedSwapServerPartialSignature,
		hex.EncodeToString(swapServerPartialSignature),
	)
	require.Equal(t, expectedFullSignature, hex.EncodeToString(fullSignature))

	assertValidSignature(t, pinnedMsg, tweakedKey, fullSignature)
}

// signingSession holds the private keys, session ids and public
// nonces of the three participants of a 3-of-3 signing session.
type signingSession struct {
	userKey             []byte
	muunKey             []byte
	swapServerKey       []byte
	allPubKeys          [][]byte
	userSessionID       []byte
	muunSessionID       []byte
	swapServerSessionID []byte
	userNonce           []byte
	muunNonce           []byte
	swapServerNonce     []byte
}

// newRandomSigningSession builds a session with random keys and session ids.
func newRandomSigningSession(t *testing.T) *signingSession {
	t.Helper()

	userKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)
	muunKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)
	swapServerKey, err := btcec.NewPrivateKey()
	require.NoError(t, err)

	userSessionID := RandomSessionID()
	muunSessionID := RandomSessionID()
	swapServerSessionID := RandomSessionID()

	return newSigningSession(
		t,
		userKey.Serialize(), muunKey.Serialize(), swapServerKey.Serialize(),
		userSessionID[:], muunSessionID[:], swapServerSessionID[:],
	)
}

// newPinnedSigningSession builds the fixed session used by the pinned vector
// tests.
func newPinnedSigningSession(t *testing.T) *signingSession {
	t.Helper()

	return newSigningSession(
		t,
		hexDecode("6e39c6add6323a5ac5f65e50231fb815026476e734eb9f4f66dce3298fddf1dc"),
		hexDecode("b876ecf97c19588cf4be95ddc0b06c0d9f623f2cf679276c25e4dfb512b19743"),
		hexDecode("c9b6e3a1f2d4857b0e9c6d3f5a8b1724e6f0a9d8c7b5e4f3a2918070605040e3"),
		hexDecode("52fdfc072182654f163f5f0f9a621d729566c74d10037c4d7bbb0407d1e2c649"),
		hexDecode("81855ad8681d0d86d1e91e00167939cb6694d2c422acd208a0072939487f6999"),
		hexDecode("eb9d18a44784045d87f3c67cf22746e995af5a25367951baa2ff6cd471c483f1"),
	)
}

func newSigningSession(
	t *testing.T,
	userKey, muunKey, swapServerKey []byte,
	userSessionID, muunSessionID, swapServerSessionID []byte,
) *signingSession {
	t.Helper()

	allPubKeys := [][]byte{
		secp256k1.PrivKeyFromBytes(userKey).PubKey().SerializeCompressed(),
		secp256k1.PrivKeyFromBytes(muunKey).PubKey().SerializeCompressed(),
		secp256k1.PrivKeyFromBytes(swapServerKey).PubKey().SerializeCompressed(),
	}

	userNonce, err := MuSig2GenerateNonce(Musig2v100, userSessionID, allPubKeys[0])
	require.NoError(t, err)

	muunNonce, err := MuSig2GenerateNonce(Musig2v100, muunSessionID, allPubKeys[1])
	require.NoError(t, err)

	swapServerNonce, err := MuSig2GenerateNonce(Musig2v100, swapServerSessionID, allPubKeys[2])
	require.NoError(t, err)

	return &signingSession{
		userKey:             userKey,
		muunKey:             muunKey,
		swapServerKey:       swapServerKey,
		allPubKeys:          allPubKeys,
		userSessionID:       userSessionID,
		muunSessionID:       muunSessionID,
		swapServerSessionID: swapServerSessionID,
		userNonce:           userNonce.PubNonce[:],
		muunNonce:           muunNonce.PubNonce[:],
		swapServerNonce:     swapServerNonce.PubNonce[:],
	}
}

// combinedKeys combines the three participant keys with the given tweak and
// returns the compressed tweaked and non-tweaked keys.
func (s *signingSession) combinedKeys(t *testing.T, tweak *MuSig2Tweaks) ([]byte, []byte) {
	t.Helper()

	combinedKey, err := Musig2CombinePubKeysWithTweak(Musig2v100, s.allPubKeys, tweak)
	require.NoError(t, err)

	return combinedKey.FinalKey.SerializeCompressed(),
		combinedKey.PreTweakedKey.SerializeCompressed()
}

// sign runs the full 3-of-3 signing flow with the given tweak: muun and the
// swap server each produce a partial signature, then the user signs last and
// combines everything. Returns the muun and swap server partial signatures and
// the full signature.
func (s *signingSession) sign(
	t *testing.T,
	msg []byte,
	tweak *MuSig2Tweaks,
) ([]byte, []byte, []byte) {
	t.Helper()

	muunPartialSignature, err := ComputePartialSignature3Of3(
		msg,
		s.muunKey,
		s.allPubKeys[0],
		s.allPubKeys[2],
		s.userNonce,
		s.swapServerNonce,
		s.muunSessionID,
		tweak,
	)
	require.NoError(t, err)

	swapServerPartialSignature, err := ComputePartialSignature3Of3(
		msg,
		s.swapServerKey,
		s.allPubKeys[0],
		s.allPubKeys[1],
		s.userNonce,
		s.muunNonce,
		s.swapServerSessionID,
		tweak,
	)
	require.NoError(t, err)

	fullSignature, err := ComputeFinalSignature3Of3(
		msg,
		s.userKey,
		s.allPubKeys[1],
		s.allPubKeys[2],
		s.muunNonce,
		s.swapServerNonce,
		muunPartialSignature,
		swapServerPartialSignature,
		s.userSessionID,
		tweak,
	)
	require.NoError(t, err)

	return muunPartialSignature, swapServerPartialSignature, fullSignature
}

// assertValidSignature asserts the signature verifies against the combined key
// and that tampering with it breaks verification.
func assertValidSignature(t *testing.T, msg, combinedKey, signature []byte) {
	t.Helper()

	valid, err := VerifySignature(Musig2v100, msg, combinedKey, signature)
	require.NoError(t, err)
	require.True(t, valid)

	// ensure verification is not `() -> true`
	tampered := append([]byte{}, signature...)
	tampered[1] = ^tampered[1]
	valid, err = VerifySignature(Musig2v100, msg, combinedKey, tampered)
	require.NoError(t, err)
	require.False(t, valid)
}
