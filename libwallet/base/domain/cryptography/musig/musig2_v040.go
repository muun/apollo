package musig

// This file implements Muun's MuSig2 v040 variant, which stays on the Go
// implementation in musig2v040: libsecp256k1 only implements the final BIP327,
// and the keys and nonces of this variant are not compatible with it.

import (
	"bytes"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	btcecmusig2 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/musig2v040"
)

// generateNonceV040 implements MuSig2GenerateNonce for v040, which derives the
// nonce from the session id alone. sessionID must be 32 bytes long: a shorter
// one is padded with zeroes rather than rejected.
func generateNonceV040(sessionID []byte) (*btcecmusig2.Nonces, error) {
	return musig2v040.GenNonces(
		musig2v040.WithCustomRand(bytes.NewBuffer(sessionID)),
	)
}

// computePartialSignatureV040 implements ComputePartialSignature for v040.
//
// data, signerPrivateKeyBytes and signerSessionID must be 32 bytes long, and
// otherParticipantPublicNonces must hold one 66-byte nonce for every entry in
// allParticipantPublicKeys other than this signer's.
func computePartialSignatureV040(
	data []byte,
	signerPrivateKeyBytes []byte,
	allParticipantPublicKeys []*btcec.PublicKey,
	otherParticipantPublicNonces [][]byte,
	signerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	signerPrivateKey := secp256k1.PrivKeyFromBytes(signerPrivateKeyBytes)
	signerPublicKeyBytes := signerPrivateKey.PubKey().SerializeCompressed()

	signerNonce, err := MuSig2GenerateNonce(
		Musig2v040Muun, signerSessionID, signerPublicKeyBytes,
	)
	if err != nil {
		return nil, err
	}

	// Create a signing session with the given private key and list of all
	// known participant public keys.
	session, err := createSessionV040(
		signerPrivateKey,
		allParticipantPublicKeys,
		tweak,
		signerNonce,
	)
	if err != nil {
		return nil, err
	}

	// Add all nonces from the other participants.
	for _, otherNonce := range otherParticipantPublicNonces {
		if _, err := session.RegisterPubNonce([66]byte(otherNonce)); err != nil {
			return nil, err
		}
	}

	sig, err := signV040(session, [32]byte(data))
	if err != nil {
		return nil, err
	}

	ret, err := SerializePartialSignature(sig)
	if err != nil {
		return nil, err
	}

	return ret[:], nil
}

// combineSignaturesV040 implements CombineSignatures for v040.
//
// data must be 32 bytes long, and allParticipantPublicNonces and
// allParticipantPartialSignatures must hold one 66-byte nonce and one 32-byte
// signature per signer.
func combineSignaturesV040(
	data []byte,
	signerPublicKeys []*btcec.PublicKey,
	allParticipantPublicNonces [][]byte,
	allParticipantPartialSignatures [][]byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	msg := [32]byte(data)

	pubNonces := make([][66]byte, len(allParticipantPublicNonces))
	for idx, pubNonce := range allParticipantPublicNonces {
		pubNonces[idx] = [66]byte(pubNonce)
	}

	aggregateNonce, err := musig2v040.AggregateNonces(pubNonces)
	if err != nil {
		return nil, err
	}

	combinedKey, err := MuSig2CombineKeys(Musig2v040Muun, signerPublicKeys, tweak)
	if err != nil {
		return nil, err
	}

	finalNonce, err := finalNonceV040(aggregateNonce, combinedKey.FinalKey, msg)
	if err != nil {
		return nil, err
	}

	sig, err := combineSigsV040(
		finalNonce,
		allParticipantPartialSignatures,
		msg,
		signerPublicKeys,
		tweak,
	)
	if err != nil {
		return nil, err
	}

	if !sig.Verify(data, combinedKey.FinalKey) {
		return nil, errors.New("combined signature is invalid")
	}

	return sig.Serialize()[:], nil
}

// createSessionV040 creates the signing session of this signer, with its own
// nonce already registered.
func createSessionV040(
	privKey *btcec.PrivateKey,
	allSignerPubKeys []*btcec.PublicKey,
	tweaks *MuSig2Tweaks,
	localNonces *btcecmusig2.Nonces,
) (*musig2v040.Session, error) {

	if len(tweaks.UnhardenedDerivationPath) > 0 {
		return nil, errors.Errorf(
			"unhardened derivation is not available for Musig2v040Muun")
	}

	if len(tweaks.TaprootTweak) > 0 {
		return nil, errors.Errorf(
			"taproot tweak bytes are not allowed for MuSig2v040Muun")
	}

	if localNonces == nil {
		return nil, errors.Errorf("error creating MuSig2 signing " +
			"context: localNonces must be provided")
	}

	// The context keeps track of all signing keys and our local key.
	allOpts := append(
		[]musig2v040.ContextOption{
			musig2v040.WithKnownSigners(allSignerPubKeys),
		},
		tweaks.ToV040ContextOptions()...,
	)
	muSigContext, err := musig2v040.NewContext(privKey, false, allOpts...)
	if err != nil {
		return nil, errors.Errorf("error creating MuSig2 signing "+
			"context: %w", err)
	}

	muSigSession, err := muSigContext.NewSession(
		musig2v040.WithPreGeneratedNonce(localNonces),
	)
	if err != nil {
		return nil, errors.Errorf("error creating MuSig2 signing "+
			"session: %w", err)
	}

	return muSigSession, nil
}

// signV040 signs with this signer's key and returns the partial signature in
// the most recent version of the MuSig2 API.
func signV040(
	session *musig2v040.Session,
	msg [32]byte,
) (*btcecmusig2.PartialSignature, error) {

	partialSig, err := session.Sign(msg)
	if err != nil {
		return nil, errors.Errorf("error signing with local key: %w", err)
	}

	return &btcecmusig2.PartialSignature{
		S: partialSig.S,
		R: partialSig.R,
	}, nil
}

// combineKeysV040 implements MuSig2CombineKeys for v040.
func combineKeysV040(allSignerPubKeys []*btcec.PublicKey,
	tweaks *MuSig2Tweaks) (*btcecmusig2.AggregateKey, error) {

	// Convert the tweak options into the appropriate MuSig2 API functional options.
	var keyAggOpts []musig2v040.KeyAggOption
	switch {
	case tweaks.TaprootBIP0086Tweak:
		keyAggOpts = append(keyAggOpts, musig2v040.WithBIP86KeyTweak())
	case len(tweaks.TaprootTweak) > 0:
		return nil, errors.Errorf(
			"taproot tweak bytes are not allowed for MuSig2v040Muun")
	case len(tweaks.GenericTweaks) > 0:
		return nil, errors.Errorf(
			"generic tweaks are not available for Musig2v040Muun")
	case len(tweaks.UnhardenedDerivationPath) > 0:
		return nil, errors.Errorf(
			"unhardened derivation is not available for Musig2v040Muun")
	}

	// Then we'll use this information to compute the aggregated public key.
	combinedKey, _, _, err := musig2v040.AggregateKeys(
		allSignerPubKeys, false, keyAggOpts...,
	)
	if err != nil {
		return nil, err
	}

	// Copy the result back into the default version's native type.
	return &btcecmusig2.AggregateKey{
		FinalKey:      combinedKey.FinalKey,
		PreTweakedKey: combinedKey.PreTweakedKey,
	}, nil
}

// finalNonceV040 computes the final signing nonce of the session, the R value
// of the final signature.
func finalNonceV040(
	aggregateNonce [66]byte,
	combinedKey *btcec.PublicKey,
	msg [32]byte,
) (*btcec.PublicKey, error) {

	nonce, _, err := musig2v040.ComputeSigningNonce(
		aggregateNonce, combinedKey, msg,
	)
	if err != nil {
		return nil, err
	}

	nonce.ToAffine()

	return btcec.NewPublicKey(&nonce.X, &nonce.Y), nil
}

// combineSigsV040 combines every participant's partial signature into the final
// Schnorr signature, applying the tweak-dependent adjustments.
func combineSigsV040(
	finalNonce *btcec.PublicKey,
	rawPartialSigs [][]byte,
	msg [32]byte,
	signerKeys []*btcec.PublicKey,
	tweaks *MuSig2Tweaks,
) (*schnorr.Signature, error) {

	partialSigs := make([]*musig2v040.PartialSignature, len(rawPartialSigs))
	for idx, rawPartialSig := range rawPartialSigs {
		partialSig, err := DeserializePartialSignature(rawPartialSig)
		if err != nil {
			return nil, err
		}
		partialSigs[idx] = &musig2v040.PartialSignature{
			S: partialSig.S,
			R: partialSig.R,
		}
	}

	var combineOpts []musig2v040.CombineOption
	switch {
	case tweaks.TaprootBIP0086Tweak:
		combineOpts = append(combineOpts, musig2v040.WithBip86TweakedCombine(
			msg, signerKeys, false,
		))
	case len(tweaks.TaprootTweak) > 0:
		return nil, errors.Errorf(
			"taproot tweak bytes are not allowed for MuSig2v040Muun")
	case len(tweaks.GenericTweaks) > 0:
		return nil, errors.Errorf(
			"generic tweaks are not available for Musig2v040Muun")
	case len(tweaks.UnhardenedDerivationPath) > 0:
		return nil, errors.Errorf(
			"unhardened derivation is not available for Musig2v040Muun")
	}

	return musig2v040.CombineSigs(finalNonce, partialSigs, combineOpts...), nil
}

// ToV040ContextOptions converts the tweak descriptor to v0.4.0 context options.
func (t *MuSig2Tweaks) ToV040ContextOptions() []musig2v040.ContextOption {
	var tweakOpts []musig2v040.ContextOption

	if len(t.GenericTweaks) > 0 {
		genericTweaksCopy := make(
			[]musig2v040.KeyTweakDesc, len(t.GenericTweaks),
		)
		for idx := range t.GenericTweaks {
			genericTweaksCopy[idx] = musig2v040.KeyTweakDesc{
				Tweak:   t.GenericTweaks[idx].Tweak,
				IsXOnly: t.GenericTweaks[idx].IsXOnly,
			}
		}
		tweakOpts = append(tweakOpts, musig2v040.WithTweakedContext(
			genericTweaksCopy...,
		))
	}

	// The BIP0086 tweak and the taproot script tweak are mutually exclusive.
	if t.TaprootBIP0086Tweak {
		tweakOpts = append(tweakOpts, musig2v040.WithBip86TweakCtx())
	} else if len(t.TaprootTweak) > 0 {
		tweakOpts = append(tweakOpts, musig2v040.WithTaprootTweakCtx(
			t.TaprootTweak,
		))
	}

	return tweakOpts
}
