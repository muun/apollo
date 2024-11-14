package musig

// This file contains generic adapters for both versions musig2v040 and v100

import (
	"bytes"
	"fmt"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	musig2v100 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/lightningnetwork/lnd/input"
	"github.com/muun/libwallet/musig2v040"
)

type MusigVersion uint8

const (
	// Muun's variant of MuSig2 based on secp256k1_zkp implementation
	// at commit https://github.com/jonasnick/secp256k1-zkp/tree/0aeaa5dfb19445f845890f3a4502c934550f4548
	// and nonces calculated with random entropy from sessionId (only).
	// - not null scriptPath are not spendable with this implementation
	// - key sorting is disabled, the order [user,muun] is enforced
	// - xOnly keys are used
	// - tapscript is not spendable
	Musig2v040Muun MusigVersion = 40

	// version 1.0.0rc2 of the MuSig2 BIP draft.
	// It uses the github.com/btcsuite/btcd/btcec/v2/schnorr/musig2 package
	// imported by go.mod
	Musig2v100 MusigVersion = 100
)

func MuSig2GenerateNonce(
	musigVersion MusigVersion,
	sessionId []byte,
	publicKeyBytes []byte,
) (*musig2v100.Nonces, error) {

	switch musigVersion {
	case Musig2v040Muun:
		return musig2v040.GenNonces(
			musig2v040.WithCustomRand(
				bytes.NewBuffer(sessionId),
			),
		)

	case Musig2v100:
		if len(publicKeyBytes) == 0 {
			return nil, fmt.Errorf("a public key must be provided to generate nonces for MuSig2v100")
		}

		publicKey, err := ParsePubKey(musigVersion, publicKeyBytes)
		if err != nil {
			return nil, err
		}

		return musig2v100.GenNonces(
			musig2v100.WithPublicKey(publicKey),
			musig2v100.WithCustomRand(bytes.NewBuffer(sessionId)),
		)
	default:
		return nil, fmt.Errorf("unknown address version: <%d>",
			musigVersion)
	}
}

// ParsePubKey forces the kind of PublicKey needed for each MuSig version
func ParsePubKey(musigVersion MusigVersion, pubKeyBytes []byte) (*btcec.PublicKey, error) {
	switch musigVersion {
	case Musig2v040Muun:
		var (
			pubKey *btcec.PublicKey
			err    error
		)

		if len(pubKeyBytes) == 33 {
			// if the not xOnly compressed was provided, then remove the
			// parity bit
			pubKey, err = schnorr.ParsePubKey(pubKeyBytes[1:])
			if err != nil {
				return nil, fmt.Errorf(
					"error parsing public key for v0.4.0 (compressed format): %v",
					err,
				)
			}
		} else {
			pubKey, err = schnorr.ParsePubKey(pubKeyBytes)
			if err != nil {
				return nil, fmt.Errorf(
					"error parsing public key for v0.4.0 (x-only format): %v",
					err,
				)
			}
		}

		return pubKey, nil
	case Musig2v100:
		pubKey, err := btcec.ParsePubKey(pubKeyBytes)
		if err != nil {
			return nil, fmt.Errorf("error parsing public key for v1.0.0 ("+
				"compressed format): %v", err)
		}
		return pubKey, nil
	default:
		return nil, fmt.Errorf("unknown MuSig2 version: <%d>",
			musigVersion)
	}
}

// MuSig2ParsePubKeys parses a list of raw public keys as the signing keys of a
// MuSig2 signing session.
func MuSig2ParsePubKeys(musigVersion MusigVersion,
	rawPubKeys [][]byte) ([]*btcec.PublicKey, error) {

	allSignerPubKeys := make([]*btcec.PublicKey, len(rawPubKeys))
	if len(rawPubKeys) < 2 {
		return nil, fmt.Errorf("need at least two signing public keys")
	}

	for idx, pubKeyBytes := range rawPubKeys {
		pubKey, err := ParsePubKey(musigVersion, pubKeyBytes)
		if err != nil {
			return nil, fmt.Errorf("error parsing signer "+
				"public key %d: %v", idx, err)
		}
		allSignerPubKeys[idx] = pubKey
	}

	return allSignerPubKeys, nil
}

// Computes the tweakedKey using a TapScript.merkleRoot or empty bytes as
// recommended by BIP0086.
// The tweakedKey is used to generate the output address: Bech32m(tweakedKey)
func Musig2CombinePubKeysWithTweak(
	musigVersion MusigVersion,
	pubKeys [][]byte,
	tweaks *MuSig2Tweaks,
) (*musig2v100.AggregateKey, error) {

	keys, err := MuSig2ParsePubKeys(musigVersion, pubKeys)
	if err != nil {
		return nil, err
	}

	return MuSig2CombineKeys(musigVersion, keys, tweaks)
}

// MuSig2CombineKeys combines the given set of public keys into a single
// combined MuSig2 combined public key, applying the given tweaks.
func MuSig2CombineKeys(musigVersion MusigVersion,
	allSignerPubKeys []*btcec.PublicKey,
	tweaks *MuSig2Tweaks) (*musig2v100.AggregateKey, error) {

	sortKeys := musigVersion != Musig2v040Muun

	switch musigVersion {
	case Musig2v040Muun:
		return combineKeysV040(allSignerPubKeys, sortKeys, tweaks)

	case Musig2v100:
		return combineKeysV100RC2(allSignerPubKeys, sortKeys, tweaks)

	default:
		return nil, fmt.Errorf("unknown MuSig2 version: <%d>",
			musigVersion)
	}
}

// combineKeysV100rc1 implements the MuSigCombineKeys logic for the MuSig2 BIP
// draft version 1.0.0rc2.
func combineKeysV100RC2(allSignerPubKeys []*btcec.PublicKey, sortKeys bool,
	tweaks *MuSig2Tweaks) (*musig2v100.AggregateKey, error) {

	// Convert the tweak options into the appropriate MuSig2 API functional
	// options.
	var keyAggOpts []musig2v100.KeyAggOption
	switch {
	case tweaks.TaprootBIP0086Tweak:
		keyAggOpts = append(keyAggOpts, musig2v100.WithBIP86KeyTweak())
	case len(tweaks.TaprootTweak) > 0:
		keyAggOpts = append(keyAggOpts, musig2v100.WithTaprootKeyTweak(
			tweaks.TaprootTweak,
		))
	case len(tweaks.GenericTweaks) > 0:
		keyAggOpts = append(keyAggOpts, musig2v100.WithKeyTweaks(
			tweaks.GenericTweaks...,
		))
	case len(tweaks.UnhardenedDerivationPath) > 0:
		bip328tweaks, err := getKeyDerivationTweaksForMusig(
			allSignerPubKeys, tweaks.UnhardenedDerivationPath)
		if err != nil {
			return nil, err
		}
		keyAggOpts = append(keyAggOpts, musig2v100.WithKeyTweaks(
			bip328tweaks...,
		))
	}

	// Then we'll use this information to compute the aggregated public key.
	combinedKey, _, _, err := musig2v100.AggregateKeys(
		allSignerPubKeys, sortKeys, keyAggOpts...,
	)
	return combinedKey, err
}

// returns a list of generic tweaks to derive a specific unhardened key for Musig2v100
// as per BIP32 + BIP328
func getKeyDerivationTweaksForMusig(
	allSignerPubKeys []*btcec.PublicKey,
	unhardenedDerivationPath []uint32,
) ([]musig2v100.KeyTweakDesc, error) {

	aggregatedKey, err := MuSig2CombineKeys(
		Musig2v100, allSignerPubKeys, NoopTweak())
	if err != nil {
		return nil, err
	}

	_, tweakDerivationSteps, err := getBip32TweaksForAggregatedKey(
		aggregatedKey.PreTweakedKey, unhardenedDerivationPath)
	if err != nil {
		return nil, err
	}

	return tweakDerivationSteps, nil
}

// combineKeysV040 implements the MuSigCombineKeys logic for the MuSig2 BIP
// draft version 0.4.0.
func combineKeysV040(allSignerPubKeys []*btcec.PublicKey, sortKeys bool,
	tweaks *MuSig2Tweaks) (*musig2v100.AggregateKey, error) {

	// Convert the tweak options into the appropriate MuSig2 API functional
	// options.
	var keyAggOpts []musig2v040.KeyAggOption
	switch {
	case tweaks.TaprootBIP0086Tweak:
		keyAggOpts = append(keyAggOpts, musig2v040.WithBIP86KeyTweak())
	case len(tweaks.TaprootTweak) > 0:
		return nil, fmt.Errorf(
			"taproot tweak bytes are not allowed for MuSig2v040Muun")
	case len(tweaks.GenericTweaks) > 0:
		return nil, fmt.Errorf(
			"generic tweaks are not available for Musig2v040Muun")
	case len(tweaks.UnhardenedDerivationPath) > 0:
		return nil, fmt.Errorf(
			"unhardened derivation is not available for Musig2v040Muun")
	}

	// Then we'll use this information to compute the aggregated public key.
	combinedKey, _, _, err := musig2v040.AggregateKeys(
		allSignerPubKeys, sortKeys, keyAggOpts...,
	)

	// Copy the result back into the default version's native type.
	return &musig2v100.AggregateKey{
		FinalKey:      combinedKey.FinalKey,
		PreTweakedKey: combinedKey.PreTweakedKey,
	}, err
}

// MuSig2CreateContext creates a new MuSig2 signing context.
func MuSig2CreateContext(
	musigVersion MusigVersion,
	privKey *btcec.PrivateKey,
	allSignerPubKeys []*btcec.PublicKey,
	tweaks *MuSig2Tweaks,
	localNonces *musig2v100.Nonces,
) (input.MuSig2Context, input.MuSig2Session, error) {

	switch musigVersion {
	case Musig2v040Muun:
		if len(tweaks.UnhardenedDerivationPath) > 0 {
			return nil, nil, fmt.Errorf(
				"unhardened derivation is not available for Musig2v040Muun")
		}

		if len(tweaks.TaprootTweak) > 0 {
			return nil, nil, fmt.Errorf(
				"taproot tweak bytes are not allowed for MuSig2v040Muun")
		}

		return createContextV040(
			privKey, allSignerPubKeys, tweaks, localNonces,
		)

	case Musig2v100:
		return createContextV100RC2(
			privKey, allSignerPubKeys, tweaks, localNonces,
		)

	default:
		return nil, nil, fmt.Errorf("unknown MuSig2 : <%d>",
			musigVersion)
	}
}

// createContextV100RC2 implements the MuSig2CreateContext logic for the MuSig2
// BIP draft version 1.0.0rc2.
func createContextV100RC2(
	privKey *btcec.PrivateKey,
	allSignerPubKeys []*btcec.PublicKey,
	tweaks *MuSig2Tweaks,
	localNonces *musig2v100.Nonces,
) (*musig2v100.Context, *musig2v100.Session, error) {

	if localNonces == nil {
		return nil, nil, fmt.Errorf("error creating MuSig2 signing " +
			"context: localNonces must be provided")
	}

	// The context keeps track of all signing keys and our local key.
	options, err := tweaks.ToContextOptions(allSignerPubKeys)
	if err != nil {
		return nil, nil, err
	}
	allOpts := append(options, musig2v100.WithKnownSigners(allSignerPubKeys))
	muSigContext, err := musig2v100.NewContext(privKey, true, allOpts...)
	if err != nil {
		return nil, nil, fmt.Errorf("error creating MuSig2 signing "+
			"context: %v", err)
	}

	muSigSession, err := muSigContext.NewSession(
		musig2v100.WithPreGeneratedNonce(localNonces),
	)
	if err != nil {
		return nil, nil, fmt.Errorf("error creating MuSig2 signing "+
			"session: %v", err)
	}

	return muSigContext, muSigSession, nil
}

// createContextV040 implements the MuSig2CreateContext logic for the MuSig2 BIP
// draft version 0.4.0.
func createContextV040(
	privKey *btcec.PrivateKey,
	allSignerPubKeys []*btcec.PublicKey,
	tweaks *MuSig2Tweaks,
	localNonces *musig2v100.Nonces,
) (*musig2v040.Context, *musig2v040.Session, error) {

	if localNonces == nil {
		return nil, nil, fmt.Errorf("error creating MuSig2 signing " +
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
		return nil, nil, fmt.Errorf("error creating MuSig2 signing "+
			"context: %v", err)
	}

	muSigSession, err := muSigContext.NewSession(
		musig2v040.WithPreGeneratedNonce(localNonces),
	)
	if err != nil {
		return nil, nil, fmt.Errorf("error creating MuSig2 signing "+
			"session: %v", err)
	}

	return muSigContext, muSigSession, nil
}

// MuSig2Sign calls the Sign() method on the given versioned signing session and
// returns the result in the most recent version of the MuSig2 API.
func MuSig2Sign(
	session input.MuSig2Session,
	msg [32]byte,
) (*musig2v100.PartialSignature, error) {

	switch s := session.(type) {
	case *musig2v100.Session:
		partialSig, err := s.Sign(msg, musig2v100.WithSortedKeys())
		if err != nil {
			return nil, fmt.Errorf("error signing with local key: "+
				"%v", err)
		}

		return partialSig, nil

	case *musig2v040.Session:
		partialSig, err := s.Sign(msg)
		if err != nil {
			return nil, fmt.Errorf("error signing with local key: "+
				"%v", err)
		}

		return &musig2v100.PartialSignature{
			S: partialSig.S,
			R: partialSig.R,
		}, nil

	default:
		return nil, fmt.Errorf("invalid session type <%T>", s)
	}
}

// MuSig2CombineSig calls the CombineSig() method on the given versioned signing
// session and returns the result in the most recent version of the MuSig2 API.
func MuSig2CombineSig(
	session input.MuSig2Session,
	otherPartialSig *musig2v100.PartialSignature,
) (bool, error) {

	switch s := session.(type) {
	case *musig2v100.Session:
		haveAllSigs, err := s.CombineSig(otherPartialSig)
		if err != nil {
			return false, fmt.Errorf("error combining partial "+
				"signature: %v", err)
		}

		return haveAllSigs, nil

	case *musig2v040.Session:
		haveAllSigs, err := s.CombineSig(&musig2v040.PartialSignature{
			S: otherPartialSig.S,
			R: otherPartialSig.R,
		})
		if err != nil {
			return false, fmt.Errorf("error combining partial "+
				"signature: %v", err)
		}

		return haveAllSigs, nil

	default:
		return false, fmt.Errorf("invalid session type <%T>", s)
	}
}

// SerializePartialSignature encodes the partial signature to a fixed size byte
// array.
func SerializePartialSignature(
	sig *musig2v100.PartialSignature,
) ([input.MuSig2PartialSigSize]byte, error) {

	var (
		buf    bytes.Buffer
		result [input.MuSig2PartialSigSize]byte
	)
	if err := sig.Encode(&buf); err != nil {
		return result, fmt.Errorf("error encoding partial signature: "+
			"%v", err)
	}

	if buf.Len() != input.MuSig2PartialSigSize {
		return result, fmt.Errorf("invalid partial signature length, "+
			"got %d wanted %d", buf.Len(), input.MuSig2PartialSigSize)
	}

	copy(result[:], buf.Bytes())

	return result, nil
}

// DeserializePartialSignature decodes a partial signature from a byte slice.
func DeserializePartialSignature(
	scalarBytes []byte,
) (*musig2v100.PartialSignature, error) {

	if len(scalarBytes) != input.MuSig2PartialSigSize {
		return nil, fmt.Errorf("invalid partial signature length, got "+
			"%d wanted %d", len(scalarBytes), input.MuSig2PartialSigSize)
	}

	sig := &musig2v100.PartialSignature{}
	if err := sig.Decode(bytes.NewReader(scalarBytes)); err != nil {
		return nil, fmt.Errorf("error decoding partial signature: %w",
			err)
	}

	return sig, nil
}
