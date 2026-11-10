package musig

import (
	"encoding/hex"
	"fmt"
	"testing"

	musig2v100 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/txscript"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
)

// This test ensures that an BIP32 derivated pub xpub/1/2 signature matches
// musig(..)/1/2 using our signature helpers. The testing procedure ensures
// a stable hdkeychain derivation matches our method. It also ensures that
// a valid signature for that derived key is produced.
func TestMuSig2Bip32UnhardenedSignature(t *testing.T) {
	// parameters
	musigVersion := Musig2v100
	pubKeys := [][]byte{
		userKey.PubKey().SerializeCompressed(),
		cosignerKey.PubKey().SerializeCompressed(),
	}
	path := []uint32{1, 2} // derivation path

	// derive musig(user,cosigner)/1/2 using our helper
	aggKeyAll, err := MuSig2ComputeInternalKey(musigVersion, pubKeys, path)
	require.NoError(t, err)

	// derive let xpub=musig(user,cosigner); xpub/1/2 using bip32 impl from btcec
	bip32pub := nativeBip32MusigDerivation(t, aggKeyAll.PreTweakedKey, path)

	// Validate that the hdkeychain.ExtendedKey derivation matches our implenentation
	// asserting that generated pubkeys match
	require.Equal(
		t,
		bip32pub.SerializeCompressed(),
		aggKeyAll.FinalKey.SerializeCompressed(),
	)

	// Then validate that musig signature works for the unhardened key
	{
		msg, _ := hex.DecodeString(
			"1111111111111111111111111111111111111111111111111111111111111111")
		tweak := NoopTweak().WithUnhardenedDerivationPath(path)
		sig := cosignerSignMusig(t, musigVersion, msg, tweak)

		valid, err := VerifySignature(
			musigVersion,
			msg,
			bip32pub.SerializeCompressed(),
			sig,
		)
		require.NoError(t, err)
		require.True(t, valid)
	}

	// additionally, ensure that the pubkey we calculate using our internal
	// MuSig2Bip32Tweaks function matches the derived xpub
	{
		step, _, err := getBip32TweaksForAggregatedKey(aggKeyAll.PreTweakedKey, path)
		require.NoError(t, err)

		// asserting that generated pubkeys match
		require.Equal(
			t,
			bip32pub.SerializeCompressed(),
			step.pubKeyBytes,
		)
	}
}

func TestMuSig2Bip32FailureModes(t *testing.T) {
	// parameters
	musigVersion := Musig2v100
	pubKeys := [][]byte{
		userKey.PubKey().SerializeCompressed(),
		cosignerKey.PubKey().SerializeCompressed(),
	}
	path := []uint32{0x80000000 | 3, 1} // derivation path

	// derive musig(user,cosigner)/3'/1 using our helper
	_, err := MuSig2ComputeInternalKey(musigVersion, pubKeys, path)
	require.Error(t, err, "Trying to derive a hardened MuSig key")
}

// Test that key aggregation and signing work for taproot tweak bytes.
//
// An unhardened derivation path cannot be combined with any other tweak type, so this test does
// not set one.
func TestMuSig2SignatureWithTaprootTweak(t *testing.T) {
	// parameters
	musigVersion := Musig2v100
	pubKeys := [][]byte{
		userKey.PubKey().SerializeCompressed(),
		cosignerKey.PubKey().SerializeCompressed(),
	}
	taprootTweak, _ := hex.DecodeString(
		"2222222222222222222222222222222222222222222222222222222222222222")

	tweak := TapScriptTweak(taprootTweak)
	// Create final key for tr(musig(user,cosigner), {...})
	taprootAggKey, err := Musig2CombinePubKeysWithTweak(
		musigVersion, pubKeys, tweak)

	require.NoError(t, err)
	require.Equal(t,
		"0324aec31c06d51473a81c35f8070485b6c19c8d652e42ebfae9cead99d17f9b44",
		hex.EncodeToString(taprootAggKey.FinalKey.SerializeCompressed()),
	)

	// Then validate that musig signature works for the tweaked key
	{
		msg, _ := hex.DecodeString(
			"1111111111111111111111111111111111111111111111111111111111111111")

		sig := cosignerSignMusig(t, musigVersion, msg, tweak)

		valid, err := VerifySignature(
			musigVersion,
			msg,
			taprootAggKey.FinalKey.SerializeCompressed(),
			sig,
		)
		require.NoError(t, err)
		require.True(t, valid)
	}
}

// This test ensures that the MuSig helpers can successfully derive an
// unhardened path from an aggregated key. It also ensures that the helpers can
// successfully create a valid signature for it
// This test also ensures that the following output descriptor can be paid and redeemed.
//
// Output descriptor of this test:
//
//	tr(
//	  musig(userKey, cosignerKey),
//	  {
//	     musig(userKey, cosignerKey)/88,
//	     musig(userKey, cosignerKey)/1/2,
//	  }
//	)
//
// The internal key is not derived because an unhardened derivation path cannot be combined with
// any other tweak type, and the key spend path needs the taproot script tweak.
//
// It is important to notice that there will be no unhardened private key to
// produce a signature here. Instead, BIP32 will be used to derive tweaks for
// the signature & aggregated key. The trick is that the individual keys will
// not change, we're only tweaking the aggregated public key.
func TestMuSig2Bip328UnhardenedTapscript(t *testing.T) {
	musigVersion := Musig2v100

	pubKeys := [][]byte{
		userKey.PubKey().SerializeCompressed(),
		cosignerKey.PubKey().SerializeCompressed(),
	}

	// derive musig(user,cosigner)/1/2
	agg1_2, err := MuSig2ComputeInternalKey(
		musigVersion,
		pubKeys,
		[]uint32{1, 2},
	)
	require.NoError(t, err)
	signerCombinedPubKey1_2 := agg1_2.FinalKey

	// derive musig(user,cosigner)/88
	agg88, err := MuSig2ComputeInternalKey(
		musigVersion,
		pubKeys,
		[]uint32{88},
	)
	require.NoError(t, err)
	signerCombinedPubKey88 := agg88.FinalKey

	// We're going to commit to a script and spend the output using the script. This is just an
	// OP_CHECKSIG with the combined MuSig2 public key.
	leaf88 := testScriptSchnorrSig(
		t,
		signerCombinedPubKey88,
	)
	leaf1_2 := testScriptSchnorrSig(
		t,
		signerCombinedPubKey1_2,
	)
	tapScriptTree := txscript.AssembleTaprootScriptTree(leaf88, leaf1_2)

	// Create final key for tr(musig(user,cosigner), {...}) applying taproot tweak bytes for
	// tapscript.rootMerkleHash
	rootMerkleHash := tapScriptTree.RootNode.TapHash()

	tweak := TapScriptTweak(rootMerkleHash[:])

	keySpendAggregatedKey, err := Musig2CombinePubKeysWithTweak(
		musigVersion, pubKeys, tweak)
	require.NoError(t, err)
	p2trKey := keySpendAggregatedKey.FinalKey
	require.Equal(t,
		"02a7be2941c035b89481f985f5ea1853c7ad806449078bc47d397849b308084b16",
		hex.EncodeToString(p2trKey.SerializeCompressed()),
	)

	testCases := []tapscriptTestCase{
		{
			// tr(
			//   musig(userKey, cosignerKey), <- redeem internalKey
			//   {
			//      musig(userKey, cosignerKey)/88,
			//      musig(userKey, cosignerKey)/1/2,
			//   }
			// )
			description: "keyspend with musig(user,cosigner)",
			p2trKey:     p2trKey,

			rootScript:    tapScriptTree,
			witnessScript: nil,

			signer: func(t *testing.T, msg []byte) []byte {
				// in the keyspend path we must pass the script root
				// hash to compute the final tweaked key.
				return cosignerSignMusig(t, musigVersion, msg, tweak)
			},
		},
		{
			// tr( musig(userKey, cosignerKey)/1/2, { musig(userKey, cosignerKey)/88, <----- redeem
			// musig(userKey, cosignerKey)/1/2, } )
			description: "tapscript with musig(user,cosigner)/88",
			internalKey: signerCombinedPubKey1_2,

			rootScript:    tapScriptTree,
			witnessScript: leaf88.Script,

			signer: func(t *testing.T, msg []byte) []byte {
				tweak := NoopTweak().WithUnhardenedDerivationPath([]uint32{88})
				return cosignerSignMusig(t, musigVersion, msg, tweak)
			},
		},
		{
			// tr(
			//   musig(userKey, cosignerKey)/88,
			//   {
			//      musig(userKey, cosignerKey)/88,
			//      musig(userKey, cosignerKey)/1/2, <------ redeem
			//   }
			// )
			description: "tapscript with musig(user,cosigner)/1/2",
			internalKey: signerCombinedPubKey88,

			rootScript:    tapScriptTree,
			witnessScript: leaf1_2.Script,

			signer: func(t *testing.T, msg []byte) []byte {
				tweak := NoopTweak().WithUnhardenedDerivationPath([]uint32{1, 2})
				return cosignerSignMusig(t, musigVersion, msg, tweak)
			},
		},
	}

	for _, test := range testCases {
		name := fmt.Sprintf("Bip328 test case=%s", test.description)
		t.Run(name, func(t *testing.T) {
			testTapscriptSpend(t, test)
		})
	}
}

// Combinations of different tweak types are not supported. The tests below assert
// that adding an unhardened derivation path to an existing non-noop tweak fails.

func TestWithUnhardenedDerivationPathOnBip86TweakFails(t *testing.T) {
	require.PanicsWithError(
		t,
		"combining tweaks of different types is not supported",
		func() {
			KeySpendOnlyTweak().WithUnhardenedDerivationPath([]uint32{1, 2})
		},
	)
}

func TestWithUnhardenedDerivationPathOnTapScriptTweakFails(t *testing.T) {
	scriptRoot := [32]byte{1, 2, 3}

	require.PanicsWithError(
		t,
		"combining tweaks of different types is not supported",
		func() {
			TapScriptTweak(scriptRoot[:]).WithUnhardenedDerivationPath([]uint32{1, 2})
		},
	)
}

func TestWithUnhardenedDerivationPathOnGenericTweaksFails(t *testing.T) {
	tweaks := &MuSig2Tweaks{
		GenericTweaks: []musig2v100.KeyTweakDesc{{Tweak: [32]byte{1, 2, 3}}},
	}

	require.PanicsWithError(
		t,
		"combining tweaks of different types is not supported",
		func() {
			tweaks.WithUnhardenedDerivationPath([]uint32{1, 2})
		},
	)
}

// implementation of bip32 for musig, the naive way. use this function to validate against
// implementated code
func nativeBip32MusigDerivation(
	t *testing.T,
	aggregatedKey *secp256k1.PublicKey,
	path []uint32,
) *secp256k1.PublicKey {
	chainCode, _ := hex.DecodeString(
		"868087ca02a6f974c4598924c36b57762d32cb45717167e300622c7167e38965")

	retKey := aggregatedKey
	var err error

	// derive using ExtendedKey
	xpub := hdkeychain.NewExtendedKey(
		[]byte{0x04, 0x88, 0xb2, 0x1e}, // version=xpub
		aggregatedKey.SerializeCompressed(),
		chainCode,
		[]byte{0x00, 0x00, 0x00, 0x00}, // parentFP
		0,                              // depth
		0,                              // childNum
		false,                          // isPrivate
	)

	for _, child := range path {
		xpub, err = xpub.Derive(child)
		require.NoError(t, err)
		retKey, err = xpub.ECPubKey()
		require.NoError(t, err)
	}

	return retKey
}
