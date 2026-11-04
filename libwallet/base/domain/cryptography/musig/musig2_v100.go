package musig

// This file implements MuSig2 v100 on top of libsecp256k1. The v040 variant
// stays on the Go implementation in musig2v040, which libsecp256k1 has no
// equivalent of: it only implements the final BIP327.

import (
	"bytes"
	"slices"

	"github.com/btcsuite/btcd/btcec/v2"
	btcecmusig2 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/musig/internal/libsecp256k1"
)

// taprootTweakTag is the BIP341 tag the tweak of a taproot output commits under.
const taprootTweakTag = "TapTweak"

// generateNonceV100 implements MuSig2GenerateNonce for v100, returning the
// public nonce.
func generateNonceV100(sessionID, publicKeyBytes []byte) ([]byte, error) {
	return libsecp256k1.GeneratePublicNonce(sessionID, publicKeyBytes)
}

// computePartialSignatureV100 implements ComputePartialSignature for v100.
//
// data, signerPrivateKeyBytes and signerSessionID must be 32 bytes long, and
// otherParticipantPublicNonces must hold one 66-byte nonce for every entry in
// allParticipantPublicKeys other than this signer's.
func computePartialSignatureV100(
	data []byte,
	signerPrivateKeyBytes []byte,
	allParticipantPublicKeys []*btcec.PublicKey,
	otherParticipantPublicNonces [][]byte,
	signerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	_, signerPublicKey := btcec.PrivKeyFromBytes(signerPrivateKeyBytes)

	if !slices.ContainsFunc(allParticipantPublicKeys, signerPublicKey.IsEqual) {
		return nil, errors.New("signing key is not found in key set")
	}

	// Signing derives this signer's nonce again from the session id, so only
	// its public half is needed to complete the session's aggregate nonce.
	signerPublicNonce, err := libsecp256k1.GeneratePublicNonce(
		signerSessionID, signerPublicKey.SerializeCompressed(),
	)
	if err != nil {
		return nil, err
	}

	aggregateNonce, err := libsecp256k1.AggregateNonces(
		append([][]byte{signerPublicNonce}, otherParticipantPublicNonces...),
	)
	if err != nil {
		return nil, err
	}

	aggregation, err := aggregateKeysV100(allParticipantPublicKeys, tweak)
	if err != nil {
		return nil, err
	}

	return aggregation.Sign(
		data, aggregateNonce, signerPrivateKeyBytes, signerSessionID,
	)
}

// verifyPartialSignatureV100 implements VerifyPartialSignature for v100.
//
// data must be 32 bytes long, signerPublicNonce and every nonce in
// allParticipantPublicNonces 66, and partialSignature 32.
func verifyPartialSignatureV100(
	data []byte,
	signerPublicKey *btcec.PublicKey,
	signerPublicNonce []byte,
	allParticipantPublicKeys []*btcec.PublicKey,
	allParticipantPublicNonces [][]byte,
	partialSignature []byte,
	tweak *MuSig2Tweaks,
) error {

	aggregateNonce, err := libsecp256k1.AggregateNonces(allParticipantPublicNonces)
	if err != nil {
		return err
	}

	aggregation, err := aggregateKeysV100(allParticipantPublicKeys, tweak)
	if err != nil {
		return err
	}

	return aggregation.VerifyPartialSignature(
		data,
		aggregateNonce,
		partialSignature,
		signerPublicNonce,
		signerPublicKey.SerializeCompressed(),
	)
}

// combineSignaturesV100 implements CombineSignatures for v100.
//
// data must be 32 bytes long, and allParticipantPublicNonces and
// allParticipantPartialSignatures must hold one 66-byte nonce and one 32-byte
// signature per signer.
func combineSignaturesV100(
	data []byte,
	signerPublicKeys []*btcec.PublicKey,
	allParticipantPublicNonces [][]byte,
	allParticipantPartialSignatures [][]byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	aggregateNonce, err := libsecp256k1.AggregateNonces(allParticipantPublicNonces)
	if err != nil {
		return nil, err
	}

	aggregation, err := aggregateKeysV100(signerPublicKeys, tweak)
	if err != nil {
		return nil, err
	}

	return aggregation.CombineSignatures(
		data, aggregateNonce, allParticipantPartialSignatures,
	)
}

// aggregateKeysV100 aggregates the signer keys and applies the tweaks to the
// result.
//
// This is the only place MuSig2Tweaks is interpreted for v100: the aggregation
// carries the tweaks with it, so signing, verifying and combining take the
// aggregation rather than reading the tweaks again. Only one kind is applied,
// with the same precedence btcec uses, since the aggregate key would otherwise
// depend on which of the two produced it.
func aggregateKeysV100(
	allSignerPubKeys []*btcec.PublicKey,
	tweaks *MuSig2Tweaks,
) (*libsecp256k1.KeyAggregation, error) {

	aggregation, err := aggregateSignerKeysV100(allSignerPubKeys)
	if err != nil {
		return nil, err
	}

	if err := applyTweaksV100(aggregation, tweaks); err != nil {
		return nil, err
	}

	return aggregation, nil
}

// combineKeysV100 implements MuSig2CombineKeys for v100, reporting the
// aggregate key both before and after the tweaks are applied.
func combineKeysV100(
	allSignerPubKeys []*btcec.PublicKey,
	tweaks *MuSig2Tweaks,
) (*btcecmusig2.AggregateKey, error) {

	aggregation, err := aggregateSignerKeysV100(allSignerPubKeys)
	if err != nil {
		return nil, err
	}

	preTweakedKey, err := aggregateKeyOf(aggregation)
	if err != nil {
		return nil, err
	}

	if err := applyTweaksV100(aggregation, tweaks); err != nil {
		return nil, err
	}

	finalKey, err := aggregateKeyOf(aggregation)
	if err != nil {
		return nil, err
	}

	return &btcecmusig2.AggregateKey{
		FinalKey:      finalKey,
		PreTweakedKey: preTweakedKey,
	}, nil
}

func aggregateSignerKeysV100(
	allSignerPubKeys []*btcec.PublicKey,
) (*libsecp256k1.KeyAggregation, error) {

	serializedKeys := make([][]byte, len(allSignerPubKeys))
	for i, signerPubKey := range allSignerPubKeys {
		serializedKeys[i] = signerPubKey.SerializeCompressed()
	}

	// v100 sorts the signer keys, which libsecp256k1 leaves to its caller.
	slices.SortFunc(serializedKeys, bytes.Compare)

	return libsecp256k1.AggregatePublicKeys(serializedKeys)
}

func aggregateKeyOf(aggregation *libsecp256k1.KeyAggregation) (*btcec.PublicKey, error) {
	serialized, err := aggregation.PublicKey()
	if err != nil {
		return nil, err
	}

	return btcec.ParsePubKey(serialized)
}

// TODO: this does not compose tweaks. Only the first case that matches
// is applied, so an unhardened derivation path is dropped when any other tweak
// is set.
func applyTweaksV100(
	aggregation *libsecp256k1.KeyAggregation,
	tweaks *MuSig2Tweaks,
) error {

	var err error

	switch {
	case tweaks.TaprootBIP0086Tweak:
		err = applyTaprootTweakV100(aggregation, nil)

	case len(tweaks.TaprootTweak) > 0:
		err = applyTaprootTweakV100(aggregation, tweaks.TaprootTweak)

	case len(tweaks.GenericTweaks) > 0:
		err = applyGenericTweaksV100(aggregation, tweaks.GenericTweaks)

	case len(tweaks.UnhardenedDerivationPath) > 0:
		var derivationTweaks []btcecmusig2.KeyTweakDesc

		derivationTweaks, err = getKeyDerivationTweaksForMusig(
			aggregation, tweaks.UnhardenedDerivationPath,
		)
		if err == nil {
			err = applyGenericTweaksV100(aggregation, derivationTweaks)
		}
	}

	return err
}

// applyTaprootTweakV100 tweaks the aggregate key as BIP341 does, committing to
// the aggregate as the internal key and to scriptRoot, which is empty for an
// output that can only be spent through the key path.
func applyTaprootTweakV100(
	aggregation *libsecp256k1.KeyAggregation, scriptRoot []byte,
) error {

	internalKey, err := aggregation.PublicKey()
	if err != nil {
		return err
	}

	// The internal key is compressed, and the commitment is to its x-only form.
	tweak, err := libsecp256k1.TaggedHash(
		taprootTweakTag, append(internalKey[1:], scriptRoot...),
	)
	if err != nil {
		return err
	}

	return aggregation.AddXOnlyTweak(tweak)
}

func applyGenericTweaksV100(
	aggregation *libsecp256k1.KeyAggregation, tweaks []btcecmusig2.KeyTweakDesc,
) error {

	for _, tweak := range tweaks {
		var err error

		if tweak.IsXOnly {
			err = aggregation.AddXOnlyTweak(tweak.Tweak[:])
		} else {
			err = aggregation.AddPlainTweak(tweak.Tweak[:])
		}

		if err != nil {
			return err
		}
	}

	return nil
}

// returns a list of generic tweaks to derive a specific unhardened key from the
// untweaked aggregate, as per BIP32 + BIP328
func getKeyDerivationTweaksForMusig(
	aggregation *libsecp256k1.KeyAggregation,
	unhardenedDerivationPath []uint32,
) ([]btcecmusig2.KeyTweakDesc, error) {

	aggregateKey, err := aggregateKeyOf(aggregation)
	if err != nil {
		return nil, err
	}

	_, tweakDerivationSteps, err := getBip32TweaksForAggregatedKey(
		aggregateKey, unhardenedDerivationPath)
	if err != nil {
		return nil, err
	}

	return tweakDerivationSteps, nil
}
