package musig

import (
	"encoding/hex"
	"fmt"
	"os"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	musig2v100 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/btcsuite/btcd/chaincfg/chainhash"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/btcsuite/btclog"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/stretchr/testify/require"
)

var (
	userKey = secp256k1.PrivKeyFromBytes(hexDecode(
		"507d881f0b5e1b12423cb0c84a196fb24227f3fe1540a1c7b20bf78d83de4533",
	))
	muunKey = secp256k1.PrivKeyFromBytes(hexDecode(
		"b6f14c73ee5269f5a13a11f48ad54306293ee134e924f680fcd35f615881105b",
	))
	swapperKey = secp256k1.PrivKeyFromBytes(hexDecode(
		"aaa14c73ee5269f5a13a11aaaad54306293ee134e924f680fcd35f6158811aaa",
	))
)

// testScriptSchnorrSig returns a simple bitcoin script that locks the funds to
// a Schnorr signature of the given public key.
func testScriptSchnorrSig(t *testing.T, pubKey *btcec.PublicKey) txscript.TapLeaf {

	builder := txscript.NewScriptBuilder()
	builder.AddData(schnorr.SerializePubKey(pubKey))
	builder.AddOp(txscript.OP_CHECKSIG)
	script2, err := builder.Script()
	require.NoError(t, err)
	return txscript.NewBaseTapLeaf(script2)
}

// TestTweakedKeyGeneration ensures that our helpers converges towards the same
// result as btcd/txscript
func TestTweakedKeyGeneration(t *testing.T) {
	musigVersion := Musig2v100
	pubKeys := [][]byte{
		userKey.PubKey().SerializeCompressed(),
		muunKey.PubKey().SerializeCompressed(),
	}

	// first combine the public keys
	aggKey, err := MuSig2ComputeInternalKey(musigVersion, pubKeys, nil)
	require.NoError(t, err)
	signerCombinedPubKey := aggKey.PreTweakedKey

	tapScriptHash := hexDecode(
		"c7d15407975a8a3ed8686b607ee955880745289fedde01c5bdfb4a933c73f911")
	tweak := TapScriptTweak(tapScriptHash)

	// build the tweaked key for this tapscript using txscript helpers
	tweakedPubKey := txscript.ComputeTaprootOutputKey(
		signerCombinedPubKey, tapScriptHash)

	// ensure our method produces the same Q as txscript
	musigCalculatedQ, err := Musig2CombinePubKeysWithTweak(
		musigVersion, pubKeys, tweak)

	require.NoError(t, err)
	require.Equal(t,
		hex.EncodeToString(tweakedPubKey.SerializeCompressed()),
		hex.EncodeToString(musigCalculatedQ.FinalKey.SerializeCompressed()),
	)
}

// Computes the internalKey without tweaks and an optional unhardenedDerivationPath
func MuSig2ComputeInternalKey(
	musigVersion MusigVersion,
	pubKeys [][]byte,
	unhardenedDerivationPath []uint32,
) (*musig2v100.AggregateKey, error) {
	tweaks := NoopTweak().WithUnhardenedDerivationPath(unhardenedDerivationPath)

	return Musig2CombinePubKeysWithTweak(musigVersion, pubKeys, tweaks)
}

// TestSpendTapscript ensures that we can successfully spend an output descriptor
// with the shape
//
//	tr(
//	   internalKey,
//	   {
//	      spendPathAKey,
//	      spendPathBKey,
//	   }
//	 )
//
// The above output descritptor involves
// 1) committing to a TapScript + internalKey. We will call this Key-only spend path
// 2) using any of the two extra scripts. We will call this TapScript spend path
//
// Any of the keys involved can unilaterally spend the output. In practice, each
// of the keys is indeed a MuSig aggregated key. But thanks to schnorr
// signatures, they do look exactly the same as a regular public key.
//
// 1) Key-only spend path =====================================================
//
// In this approach we use the internalKey to sign the taproot transaction. The
// key we use is not the key as is, instead we "tweak" the key adding
// an addendum of data in a process that produces a new key pair. Thanks to
// Schnorr signatures, we can tweak both the private and the public parts of the
// key and the result of both are compatible. The process and math of how this
// works are detailed below.
//
// The addendum itself is a commitment to the merkle root of the entire TapScript.
//
// Without tweaks:
//
//	sign(key, nonce, Message): (Sig, PubNonce) ->
//	   let PubNonce = nonce * G
//	   let PubKey = key * G
//	   let Hash = h(PubNonce || PubKey || message) ━━━━━┓
//	   let Sig = nonce + Hash * priv ───────────────────╂──┐
//	   return (Sig, PubNonce)                           ┃  │
//	                                                    ┃  │
//	verify(PubKey, Sig, PubNonce, Message) ->           ┃  │
//	   let Hash = h(PubNonce || PubKey || Message) <━━━━┛  │
//	                                                       │
//	   return   Sig ==      nonce + Hash *  priv <─────────┘ here is the Sig, and we validate it by performing
//	   return Sig*G == G * (nonce + Hash *  priv)            all the calculations again, but we don't have private
//	   return Sig*G ==    nonce*G + Hash * (priv*G)          values, so we multiply both terms by G to obtain
//	   return Sig*G ==   PubNonce + Hash *  PubKey           all the public information to verify Sig
//
// With tweaks:
//
//	sign(key, nonce, Message, Tweak t): (Sig, PubNonce) ->
//	   let PubNonce = nonce * G
//	   let PubKey = (priv+t) * G ──────────────────────────┐ add Tweak (t)
//	   let Hash = h(PubNonce || PubKey || message) ━━━━━━━┓│
//	   let Sig = nonce + Hash * (priv+t) ─────────────────╂┤
//	   return (Sig, PubNonce)                             ┃│
//	                                                      ┃│
//	verify(PubKey, Sig, PubNonce, Message, Tweak t) ->    ┃│
//	   let Hash = h(PubNonce || PubKey || Message) <━━━━━━┛│
//	            Sig ==      nonce + Hash *  priv+t <───────┤ we've added a tweak "t" to the private key, and to balance
//	          Sig*G == G * (nonce + Hash *  priv+t)        │ out the equation, we've also added t*G to the public key
//	          Sig*G ==    nonce*G + Hash * (priv+t)*G      │ this makes the verification work without modification
//	   return Sig*G ==   PubNonce + Hash *  PubKey <───────┘ (priv+t)*G == PubKey \\ nonce*G == PubNonce
//
// BIP86 establishes that if we are committing to a key-only spend path ONLY,
// effectively disabling any TapScript paths then the tweak must commit to an
// empty [0]byte{} scriptPath
//
// To generate the address, we first need to tweak the internalKey using the
// merkle root of the scripts, we will call this parameter "scriptPath".
//
// let tweak = h_tapTweak(internalPubKey || scriptPath)
// tweakedPubKey = internalPubKey + (tweak*G)
// tweakedPrivKey = internalKey + tweak
//
// Our address will be constructed as Bech32m(tweakedPubKey), and the key itself
// now can be used (along with the opaque merkleRoot) to spend the output.
//
// The tweak can be calculated by passing scriptPath to the MuSig2 helpers.
//
// If the use case requires it, it is possible to disable the keyspend path, leaving
// only the script path enabled. To do it, the internalKey must be disabled using
// the hash of a fixed point in the curve.
// https://github.com/bitcoin/bips/blob/master/bip-0341.mediawiki#constructing-and-spending-taproot-outputs
//
// 2) TapScript path ==========================================================
//
// For TapScript, we must provide a couple things to the transaction's control
// block to make it work:
// - internalKey
// - parity bit of the tweakedPubKey + version of the leafs of the TapScript tree
// - proof of inclusion of the script (merkle proof)
// - the WitnessScript (same as on the leaf) used to redeem the UTXO
func TestSpendTapscript(t *testing.T) {
	spendPathAKey := userKey
	spendPathBKey := muunKey
	internalKey := swapperKey

	// We're going to commit to a script and spend the output using the
	// script. This is just an OP_CHECKSIG with the public key.
	leafA := testScriptSchnorrSig(t, spendPathAKey.PubKey())
	leafB := testScriptSchnorrSig(t, spendPathBKey.PubKey())
	tapScriptTree := txscript.AssembleTaprootScriptTree(leafA, leafB)
	scriptRootHash := tapScriptTree.RootNode.TapHash()

	testCases := []tapscriptTestCase{
		{
			// tr(
			//   internalKey, <----------------------------- redeem
			//   {
			//      spendPathAKey,
			//      spendPathBKey,
			//   }
			// )
			description: "Spend using the internal key",
			internalKey: internalKey.PubKey(),

			rootScript:    tapScriptTree,
			witnessScript: nil,

			signer: func(t *testing.T, msg []byte) []byte {
				// Before we sign the sighash, we'll need to apply the
				// taptweak to the private key based on the tapScriptRootHash.
				privKeyTweak := txscript.TweakTaprootPrivKey(*internalKey, scriptRootHash[:])
				sig, err := schnorr.Sign(privKeyTweak, msg)
				require.NoError(t, err)
				return sig.Serialize()
			},
		},
		{
			// tr(
			//   internalKey,
			//   {
			//      spendPathAKey, <-------------------- redeem
			//      spendPathBKey,
			//   }
			// )
			description: "Spend tapscript with leafA",
			internalKey: internalKey.PubKey(),

			rootScript:    tapScriptTree,
			witnessScript: leafA.Script,

			signer: func(t *testing.T, msg []byte) []byte {
				sig, err := schnorr.Sign(spendPathAKey, msg)
				require.NoError(t, err)
				return sig.Serialize()
			},
		},
		{
			// tr(
			//   internalKey,
			//   {
			//      spendPathAKey,
			//      spendPathBKey, <-------------------- redeem
			//   }
			// )
			description: "Spend tapscript with leafB",
			internalKey: internalKey.PubKey(),

			rootScript:    tapScriptTree,
			witnessScript: leafB.Script,

			signer: func(t *testing.T, msg []byte) []byte {
				sig, err := schnorr.Sign(spendPathBKey, msg)
				require.NoError(t, err)
				return sig.Serialize()
			},
		},
	}

	for _, test := range testCases {
		name := fmt.Sprintf("tapscript test case=%s", test.description)
		t.Run(name, func(t *testing.T) {
			testTapscriptSpend(t, test)
		})
	}
}

// This test ensures the same functionality as the previous one, but the
// internalKey and one spendPath used are MuSig2v100
//
// Output descriptor of this test:
//
//	tr(
//	   musig(userKey, muunKey),
//	   {
//	      musig(userKey, muunKey),
//	      userKey,
//	   }
//	 )
func TestSpendTapscriptMusig(t *testing.T) {
	musigVersion := Musig2v100

	pubKeys := [][]byte{
		userKey.PubKey().SerializeCompressed(),
		muunKey.PubKey().SerializeCompressed(),
	}

	// first combine the public keys to get the internalKey P
	aggKeyAll, err := MuSig2ComputeInternalKey(musigVersion, pubKeys, nil)
	require.NoError(t, err)
	signerCombinedPubKey := aggKeyAll.PreTweakedKey

	// We're going to commit to a script and spend the output using the
	// script. This is just an OP_CHECKSIG with the combined MuSig2 public
	// key.
	leafMusig := testScriptSchnorrSig(t, signerCombinedPubKey)
	leafRawKey := testScriptSchnorrSig(t, userKey.PubKey())
	tapScriptTree := txscript.AssembleTaprootScriptTree(leafMusig, leafRawKey)
	scriptRootHash := tapScriptTree.RootNode.TapHash()

	randomPriv, _ := secp256k1.GeneratePrivateKey()
	randomPub := randomPriv.PubKey()

	testCases := []tapscriptTestCase{
		{
			//   tr(
			//      musig(userKey, muunKey), <----------- redeem
			//      {
			//         musig(userKey, muunKey),
			//         userKey,
			//      }
			//    )
			description: "Keyspend using musig as signer",
			internalKey: signerCombinedPubKey,

			rootScript:    tapScriptTree,
			witnessScript: nil,

			signer: func(t *testing.T, msg []byte) []byte {
				// in the keyspend path we must pass the script root
				// hash to compute the final tweaked key.
				tweak := TapScriptTweak(scriptRootHash[:])
				return muunSignMusig(t, musigVersion, msg, tweak)
			},
		},
		{
			//   tr(
			//      musig(userKey, muunKey),
			//      {
			//         musig(userKey, muunKey), <---- redeem
			//         userKey,
			//      }
			//    )
			description: "MuSig spend tapscript",
			internalKey: signerCombinedPubKey,

			rootScript:    tapScriptTree,
			witnessScript: leafMusig.Script,

			signer: func(t *testing.T, msg []byte) []byte {
				// when signing with a regular (or musig) key in
				// script spend path no tweaks should be applied
				// see the next test case for a non-musig example
				tweak := NoopTweak()
				return muunSignMusig(t, musigVersion, msg, tweak)
			},
		},
		{
			//   tr(
			//      musig(userKey, muunKey),
			//      {
			//         musig(userKey, muunKey),
			//         userKey, <-------------------- redeem
			//      }
			//    )
			description: "key-only spend tapscript",
			internalKey: signerCombinedPubKey,

			rootScript:    tapScriptTree,
			witnessScript: leafRawKey.Script,

			signer: func(t *testing.T, msg []byte) []byte {
				sig, err := schnorr.Sign(userKey, msg)
				require.NoError(t, err)
				return sig.Serialize()
			},
		},
		{
			// this test ensures that even though a random key was
			// used to generate the outpoint, a valid tapscript
			// input script can redeem it
			//
			//   tr(
			//      RANDOM_PUB_KEY, ================> (CHANGED!)
			//      {
			//         musig(userKey, muunKey),
			//         userKey, <-------------------- redeem
			//      }
			//    )
			description: "key-only(user) spend tapscript. redeeming from random keyspend+tapscript(user)",
			internalKey: randomPub,

			rootScript:    tapScriptTree,
			witnessScript: leafRawKey.Script,

			signer: func(t *testing.T, msg []byte) []byte {
				sig, err := schnorr.Sign(userKey, msg)
				require.NoError(t, err)
				return sig.Serialize()
			},
		},
	}

	for _, test := range testCases {
		name := fmt.Sprintf("tapscript test case=%s", test.description)
		t.Run(name, func(t *testing.T) {
			testTapscriptSpend(t, test)
		})
	}
}

type tapscriptTestCase struct {
	// a description of the test
	description string

	// Internal key as defined in https://bips.dev/386 this key will be used
	// to craft an outpupt script for taproot using the following algorithm:
	//
	//	merkle_root:        HashTapBranch(TREE)
	//	32_byte_output_key: internalKey + int(HashTapTweak(bytes(internalKey) || rootScript))G
	//	scriptPubKey:       OP_1 <32_byte_output_key>
	//
	// internalKey cannot be present along p2trKey
	internalKey *secp256k1.PublicKey

	// Key used for the P2TR OP_1 <key> to be redeemed. Use this alternative
	// to enable better customization of the address. This may be necessary
	// when the internalKey is the result of many tweak operations
	//
	// internalKey cannot be present along p2trKey
	p2trKey *secp256k1.PublicKey

	// rootScript commits the internalKey to a pre-defined script. it is
	// used to tweak the internalKey and produce the final Bech32m address
	// - when the key-only spend path is chosen, either musig or the plain
	//   keys will be tweaked
	// - when TapScript spend path is chosen, the witnessScript+proof of
	//   inclusion need to be provided in the control block along with the
	//   internalKey. the validator will then re-compute the bech32m with
	//   this information to verify the transaction
	rootScript *txscript.IndexedTapScriptTree

	// TapScript requires a witnessScript belonging to a valid leaf of the
	// rootScript
	witnessScript []byte

	// a signer function to spend the funds of receiverAddress. any key can
	// sign. For key-only spend path the tweak bytes need to be computed
	// from scriptRootHash. For TapScript it is not necessary.
	signer func(t *testing.T, msg []byte) []byte
}

// helper function to test tapscript spending paths. each test case must provide
// a full indexed taproot script and a leaf, along with a public key that will
// receive the funds at first and a signer function
func testTapscriptSpend(t *testing.T, tc tapscriptTestCase) {
	txscript.DisableLog()

	// change if you need to see what the script engine outputs
	verbose := false
	if verbose {
		logger := btclog.NewBackend(os.Stderr).Logger("test")
		logger.SetLevel(btclog.LevelTrace)
		txscript.UseLogger(logger)
	}

	hasInternalKey := tc.internalKey != nil
	hasP2trKey := tc.p2trKey != nil
	if !hasInternalKey && !hasP2trKey {
		t.Fatal("Either p2trKey or internalKey must be provided")
	}
	if hasInternalKey == hasP2trKey {
		t.Fatal("You must provide either p2trKey or internalKey, not both")
	}

	tapRootHash := tc.rootScript.RootNode.TapHash()

	if tc.p2trKey == nil {
		// generate a taproot address to receive the funds using the tweaked key
		tc.p2trKey = txscript.ComputeTaprootOutputKey(tc.internalKey, tapRootHash[:])
	}

	p2trScript, err := txscript.PayToTaprootScript(tc.p2trKey)
	require.NoError(t, err)

	tests := []struct {
		sigHashType txscript.SigHashType
	}{
		{sigHashType: txscript.SigHashDefault},
		{sigHashType: txscript.SigHashAll},
		{sigHashType: txscript.SigHashNone},
		{sigHashType: txscript.SigHashSingle},
		{sigHashType: txscript.SigHashSingle | txscript.SigHashAnyOneCanPay},
		{sigHashType: txscript.SigHashNone | txscript.SigHashAnyOneCanPay},
		{sigHashType: txscript.SigHashAll | txscript.SigHashAnyOneCanPay},
	}
	for _, test := range tests {
		name := fmt.Sprintf("sighash=%v", test.sigHashType)
		t.Run(name, func(t *testing.T) {
			testTx := wire.NewMsgTx(2)

			// set a dummy txOut pointing to the same address we are spending
			txOut := &wire.TxOut{Value: 1e8, PkScript: p2trScript}
			testTx.AddTxOut(txOut)

			// instruct the testTx to spend a virtual outpoint with
			// index 1
			testTx.AddTxIn(&wire.TxIn{
				PreviousOutPoint: wire.OutPoint{Index: 1},
			})

			// prevFetcher is the structure used to select a virtual
			// outpoint
			prevFetcher := txscript.NewCannedPrevOutputFetcher(
				txOut.PkScript, txOut.Value)

			// get a hash to sign for the tx with the selected leaf
			sigHashes := txscript.NewTxSigHashes(testTx, prevFetcher)

			var (
				msgToSign []byte
				sig       []byte
			)

			// if there is no tc.leaf, we assume key-only spend path
			if tc.witnessScript == nil {
				msgToSign, err = txscript.CalcTaprootSignatureHash(
					sigHashes,
					test.sigHashType,
					testTx, 0, prevFetcher,
				)
				require.NoError(t, err)

				// sign the taproot keyspend hash
				sig = tc.signer(t, msgToSign)

				validSignature, err := VerifySignature(
					Musig2v100,
					msgToSign,
					tc.p2trKey.SerializeCompressed(),
					sig,
				)
				require.NoError(t, err)
				require.True(t, validSignature, "Signature invalid for tweaked key")
			} else {
				leaf := txscript.NewBaseTapLeaf(tc.witnessScript)
				msgToSign, err = txscript.CalcTapscriptSignaturehash(
					sigHashes, test.sigHashType, testTx, 0,
					prevFetcher,
					leaf,
				)
				require.NoError(t, err)

				sig = tc.signer(t, msgToSign)
			}

			// Finally, append the sighash type to the final sig if
			// it's not the default sighash value (in which case
			// appending it is disallowed).
			if test.sigHashType != txscript.SigHashDefault {
				sig = append(sig, byte(test.sigHashType))
			}

			// If this isn't sighash default, then a sighash should
			// be applied. Otherwise, it should be a normal sig.
			expectedLen := schnorr.SignatureSize
			if test.sigHashType != txscript.SigHashDefault {
				expectedLen += 1
			}
			require.Len(t, sig, expectedLen)

			if tc.witnessScript == nil {
				testTx.TxIn[0].Witness = wire.TxWitness{sig}
			} else {
				// find the control block for the selected leaf
				tree := tc.rootScript
				leaf := txscript.NewBaseTapLeaf(tc.witnessScript)
				leafHash := leaf.TapHash()
				settleIdx := tree.LeafProofIndex[chainhash.Hash(leafHash)]
				settleMerkleProof := tree.LeafMerkleProofs[settleIdx]
				controlBlock := settleMerkleProof.ToControlBlock(tc.internalKey)
				controlBlockBytes, err := controlBlock.ToBytes()
				require.NoError(t, err)

				testTx.TxIn[0].Witness = wire.TxWitness{
					sig,
					tc.witnessScript,
					controlBlockBytes,
				}
			}

			// Finally, ensure that the signature produced is valid
			// by running the VM
			vm, err := txscript.NewEngine(
				txOut.PkScript, testTx, 0,
				txscript.StandardVerifyFlags,
				nil, sigHashes, txOut.Value, prevFetcher,
			)
			require.NoError(t, err)

			require.NoError(t, vm.Execute())
		})
	}
}

// signs a message using the globally configured keys
func muunSignMusig(t *testing.T, musigVersion MusigVersion, msg []byte, tweak *MuSig2Tweaks) []byte {
	// generate musig sessionId
	userSession, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)
	muunSession, err := secp256k1.GeneratePrivateKey()
	require.NoError(t, err)

	// generate musig nonces
	userNonce, err := MuSig2GenerateNonce(
		musigVersion,
		userSession.Serialize(),
		SerializePublicKey(musigVersion, userKey.PubKey()),
	)
	require.NoError(t, err)

	muunNonce, err := MuSig2GenerateNonce(
		musigVersion,
		muunSession.Serialize(),
		SerializePublicKey(musigVersion, muunKey.PubKey()),
	)
	require.NoError(t, err)

	// sign muun's part
	muunPartialSignatureBytes, err := ComputeMuunPartialSignature(
		musigVersion,
		msg,
		SerializePublicKey(musigVersion, userKey.PubKey()),
		muunKey.Serialize(),
		userNonce.PubNonce[:],
		muunSession.Serialize(),
		tweak,
	)
	require.NoError(t, err)

	// sign user's part
	sig, err := ComputeUserPartialSignature(
		musigVersion,
		msg,
		userKey.Serialize(),
		SerializePublicKey(musigVersion, muunKey.PubKey()),
		muunPartialSignatureBytes,
		muunNonce.PubNonce[:],
		userSession.Serialize(),
		tweak,
	)
	require.NoError(t, err)

	return sig
}
