package musig

import (
	"encoding/hex"
	"fmt"
	"testing"

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
		muunKey.PubKey().SerializeCompressed(),
	}
	path := []uint32{1, 2} // derivation path

	// derive musig(user,muun)/1/2 using our helper
	aggKeyAll, err := MuSig2ComputeInternalKey(musigVersion, pubKeys, path)
	require.NoError(t, err)

	// derive let xpub=musig(user,muun); xpub/1/2 using bip32 impl from btcec
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
		sig := muunSignMusig(t, musigVersion, msg, tweak)

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
		muunKey.PubKey().SerializeCompressed(),
	}
	path := []uint32{0x80000000 | 3, 1} // derivation path

	// derive musig(user,muun)/3'/1 using our helper
	_, err := MuSig2ComputeInternalKey(musigVersion, pubKeys, path)
	require.Error(t, err, "Trying to derive a hardened MuSig key")
}

// Test that signatures work for bip328+taproot tweak bytes
func TestMuSig2Bip32UnhardenedSignatureTaprootTweak2(t *testing.T) {
	// parameters
	musigVersion := Musig2v100
	pubKeys := [][]byte{
		userKey.PubKey().SerializeCompressed(),
		muunKey.PubKey().SerializeCompressed(),
	}
	taprootTweak, _ := hex.DecodeString(
		"2222222222222222222222222222222222222222222222222222222222222222")

	tweak := TapScriptTweak(taprootTweak).WithUnhardenedDerivationPath([]uint32{1, 2})
	//                                          vvv━━━━━━━━━━━━━━━━━━━━━━━━━━━━━^^^^
	// Create final key for tr(musig(user,muun)/1/2, {...})
	taprootAggKey, err := Musig2CombinePubKeysWithTweak(
		musigVersion, pubKeys, tweak)

	require.NoError(t, err)
	require.Equal(t,
		"0324aec31c06d51473a81c35f8070485b6c19c8d652e42ebfae9cead99d17f9b44",
		hex.EncodeToString(taprootAggKey.FinalKey.SerializeCompressed()),
	)

	// Then validate that musig signature works for the unhardened key
	{
		msg, _ := hex.DecodeString(
			"1111111111111111111111111111111111111111111111111111111111111111")

		sig := muunSignMusig(t, musigVersion, msg, tweak)

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
//
// This test also ensures that the following output descriptor can be paid and
// redeemed.
//
// Output descriptor of this test:
//
//	tr(
//	  musig(userKey, muunKey)/123,
//	  {
//	     musig(userKey, muunKey)/88,
//	     musig(userKey, muunKey)/1/2,
//	  }
//	)
//
// It is important to notice that there will be no unhardened private key to
// produce a signature here. Instead, BIP32 will be used to derive tweaks for
// the signature & aggregated key. The trick is that the individual keys will
// not change, we're only tweaking the aggregated public key.
func TestMuSig2Bip328UnhardenedTapscript(t *testing.T) {
	musigVersion := Musig2v100

	pubKeys := [][]byte{
		userKey.PubKey().SerializeCompressed(),
		muunKey.PubKey().SerializeCompressed(),
	}

	// derive musig(user,muun)/1/2
	agg_1_2, err := MuSig2ComputeInternalKey(musigVersion, pubKeys, []uint32{1, 2})
	require.NoError(t, err)
	signerCombinedPubKey_1_2 := agg_1_2.FinalKey

	// derive musig(user,muun)/88
	agg_88, err := MuSig2ComputeInternalKey(musigVersion, pubKeys, []uint32{88})
	require.NoError(t, err)
	signerCombinedPubKey_88 := agg_88.FinalKey

	// derive musig(user,muun)/123
	internalKeyAgg, err := MuSig2ComputeInternalKey(musigVersion, pubKeys, []uint32{1, 2})
	require.NoError(t, err)
	internalKey := internalKeyAgg.FinalKey

	// We're going to commit to a script and spend the output using the
	// script. This is just an OP_CHECKSIG with the combined MuSig2 public
	// key.
	leaf_88 := testScriptSchnorrSig(t, signerCombinedPubKey_88)
	leaf_1_2 := testScriptSchnorrSig(t, signerCombinedPubKey_1_2)
	tapScriptTree := txscript.AssembleTaprootScriptTree(leaf_88, leaf_1_2)

	// Create final key for tr(musig(user,muun)/123, {...}) applying taproot
	// tweak bytes for tapscript.rootMerlkeHash and bip32 tweaks for /123
	// derivation path
	rootMerkleHash := tapScriptTree.RootNode.TapHash()

	tweak :=
		TapScriptTweak(rootMerkleHash[:]).
			WithUnhardenedDerivationPath([]uint32{123})

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
			//   musig(userKey, muunKey)/123, <- redeem internalKey
			//   {
			//      musig(userKey, muunKey)/88,
			//      musig(userKey, muunKey)/1/2,
			//   }
			// )
			description: "keyspend with musig(user,muun)/123",
			p2trKey:     p2trKey,

			rootScript:    tapScriptTree,
			witnessScript: nil,

			signer: func(t *testing.T, msg []byte) []byte {
				// in the keyspend path we must pass the script root
				// hash to compute the final tweaked key.
				return muunSignMusig(t, musigVersion, msg, tweak)
			},
		},
		{
			// tr(
			//   musig(userKey, muunKey)/123,
			//   {
			//      musig(userKey, muunKey)/88, <------- redeem
			//      musig(userKey, muunKey)/1/2,
			//   }
			// )
			description: "tapscript with musig(user,muun)/88",
			internalKey: internalKey,

			rootScript:    tapScriptTree,
			witnessScript: leaf_88.Script,

			signer: func(t *testing.T, msg []byte) []byte {
				tweak := NoopTweak().WithUnhardenedDerivationPath([]uint32{88})
				return muunSignMusig(t, musigVersion, msg, tweak)
			},
		},
		{
			// tr(
			//   musig(userKey, muunKey)/123,
			//   {
			//      musig(userKey, muunKey)/88,
			//      musig(userKey, muunKey)/1/2, <------ redeem
			//   }
			// )
			description: "tapscript with musig(user,muun)/1/2",
			internalKey: signerCombinedPubKey_88,

			rootScript:    tapScriptTree,
			witnessScript: leaf_1_2.Script,

			signer: func(t *testing.T, msg []byte) []byte {
				tweak := NoopTweak().WithUnhardenedDerivationPath([]uint32{1, 2})
				return muunSignMusig(t, musigVersion, msg, tweak)
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

// implementation of bip32 for musig, the naive way. use this function to validate
// against implementated code
func nativeBip32MusigDerivation(t *testing.T, aggregatedKey *secp256k1.PublicKey, path []uint32) *secp256k1.PublicKey {
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
