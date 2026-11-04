package musig

// This file contains generic adapters for both versions musig2v040 and v100

import (
	"bytes"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	btcecmusig2 "github.com/btcsuite/btcd/btcec/v2/schnorr/musig2"
	"github.com/go-errors/errors"
	"github.com/lightningnetwork/lnd/input"
)

type MusigVersion uint8

const (
	// Muun's variant of MuSig2 based on secp256k1_zkp implementation
	// at commit https://github.com/jonasnick/secp256k1-zkp/tree/0aeaa5dfb19445f845890f3a4502c934550f4548
	// and nonces calculated with random entropy from sessionID (only).
	// - not null scriptPath are not spendable with this implementation
	// - key sorting is disabled, the order [user,cosigner] is enforced
	// - xOnly keys are used
	// - tapscript is not spendable
	Musig2v040Muun MusigVersion = 40

	// MuSig2 as specified by BIP327, named after the 1.0.0 draft that became
	// it. Key aggregation, signing and combining go through libsecp256k1,
	// bound by the musig/internal/libsecp256k1 package.
	Musig2v100 MusigVersion = 100
)

// muSig2ComputePartialSignature dispatches partial signing to the
// implementation of the given version.
func muSig2ComputePartialSignature(
	musigVersion MusigVersion,
	data []byte,
	signerPrivateKeyBytes []byte,
	allParticipantPublicKeys []*btcec.PublicKey,
	otherParticipantPublicNonces [][]byte,
	signerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	tweak.checkSingleTweakType()

	switch musigVersion {
	case Musig2v040Muun:
		return computePartialSignatureV040(
			data,
			signerPrivateKeyBytes,
			allParticipantPublicKeys,
			otherParticipantPublicNonces,
			signerSessionID,
			tweak,
		)

	case Musig2v100:
		return computePartialSignatureV100(
			data,
			signerPrivateKeyBytes,
			allParticipantPublicKeys,
			otherParticipantPublicNonces,
			signerSessionID,
			tweak,
		)

	default:
		return nil, errors.Errorf("unknown MuSig2 version: <%d>", musigVersion)
	}
}

// muSig2VerifyPartialSignature dispatches partial signature verification to the
// implementation of the given version.
func muSig2VerifyPartialSignature(
	musigVersion MusigVersion,
	data []byte,
	signerPublicKey *btcec.PublicKey,
	signerPublicNonce []byte,
	allParticipantPublicKeys []*btcec.PublicKey,
	allParticipantPublicNonces [][]byte,
	partialSignature []byte,
	tweak *MuSig2Tweaks,
) error {

	tweak.checkSingleTweakType()

	switch musigVersion {
	case Musig2v040Muun:
		return errors.New("partial signature verification is not implemented for musigv40")

	case Musig2v100:
		return verifyPartialSignatureV100(
			data,
			signerPublicKey,
			signerPublicNonce,
			allParticipantPublicKeys,
			allParticipantPublicNonces,
			partialSignature,
			tweak,
		)

	default:
		return errors.Errorf("unknown MuSig2 version: <%d>", musigVersion)
	}
}

// muSig2CombineSignatures dispatches signature combining to the implementation
// of the given version.
func muSig2CombineSignatures(
	musigVersion MusigVersion,
	data []byte,
	signerPublicKeys []*btcec.PublicKey,
	allParticipantPublicNonces [][]byte,
	allParticipantPartialSignatures [][]byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	tweak.checkSingleTweakType()

	switch musigVersion {
	case Musig2v040Muun:
		return combineSignaturesV040(
			data,
			signerPublicKeys,
			allParticipantPublicNonces,
			allParticipantPartialSignatures,
			tweak,
		)

	case Musig2v100:
		return combineSignaturesV100(
			data,
			signerPublicKeys,
			allParticipantPublicNonces,
			allParticipantPartialSignatures,
			tweak,
		)

	default:
		return nil, errors.Errorf("unknown MuSig2 version: <%d>", musigVersion)
	}
}

// MuSig2GenerateNonce derives a signer's nonce from a session id.
//
// For v100 the secret half is zeroed and must not be used: signing derives its
// own from the same session id, and libsecp256k1 never hands one out.
func MuSig2GenerateNonce(
	musigVersion MusigVersion,
	sessionID []byte,
	publicKeyBytes []byte,
) (*btcecmusig2.Nonces, error) {

	if len(sessionID) != 32 {
		return nil, errors.Errorf("expected a 32-byte session id, got %d",
			len(sessionID))
	}

	switch musigVersion {
	case Musig2v040Muun:
		return generateNonceV040(sessionID)

	case Musig2v100:
		publicNonce, err := generateNonceV100(sessionID, publicKeyBytes)
		if err != nil {
			return nil, err
		}

		return &btcecmusig2.Nonces{PubNonce: [66]byte(publicNonce)}, nil

	default:
		return nil, errors.Errorf("unknown MuSig2 version: <%d>",
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
			// if the not xOnly compressed was provided, then remove the parity bit
			pubKey, err = schnorr.ParsePubKey(pubKeyBytes[1:])
			if err != nil {
				return nil, errors.Errorf(
					"error parsing public key for v0.4.0 (compressed format): %w",
					err,
				)
			}
		} else {
			pubKey, err = schnorr.ParsePubKey(pubKeyBytes)
			if err != nil {
				return nil, errors.Errorf(
					"error parsing public key for v0.4.0 (x-only format): %w",
					err,
				)
			}
		}

		return pubKey, nil
	case Musig2v100:
		pubKey, err := btcec.ParsePubKey(pubKeyBytes)
		if err != nil {
			return nil, errors.Errorf("error parsing public key for v1.0.0 ("+
				"compressed format): %w", err)
		}
		return pubKey, nil
	default:
		return nil, errors.Errorf("unknown MuSig2 version: <%d>",
			musigVersion)
	}
}

// MuSig2ParsePubKeys parses a list of raw public keys as the signing keys of a
// MuSig2 signing session.
func MuSig2ParsePubKeys(musigVersion MusigVersion,
	rawPubKeys [][]byte) ([]*btcec.PublicKey, error) {

	allSignerPubKeys := make([]*btcec.PublicKey, len(rawPubKeys))
	if len(rawPubKeys) < 2 {
		return nil, errors.Errorf("need at least two signing public keys")
	}

	for idx, pubKeyBytes := range rawPubKeys {
		pubKey, err := ParsePubKey(musigVersion, pubKeyBytes)
		if err != nil {
			return nil, errors.Errorf("error parsing signer "+
				"public key %d: %w", idx, err)
		}
		allSignerPubKeys[idx] = pubKey
	}

	return allSignerPubKeys, nil
}

// Computes the tweakedKey using a TapScript.merkleRoot or empty bytes as recommended by BIP0086.
// The tweakedKey is used to generate the output address: Bech32m(tweakedKey)
func Musig2CombinePubKeysWithTweak(
	musigVersion MusigVersion,
	pubKeys [][]byte,
	tweaks *MuSig2Tweaks,
) (*btcecmusig2.AggregateKey, error) {

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
	tweaks *MuSig2Tweaks) (*btcecmusig2.AggregateKey, error) {

	tweaks.checkSingleTweakType()

	switch musigVersion {
	case Musig2v040Muun:
		return combineKeysV040(allSignerPubKeys, tweaks)

	case Musig2v100:
		return combineKeysV100(allSignerPubKeys, tweaks)

	default:
		return nil, errors.Errorf("unknown MuSig2 version: <%d>",
			musigVersion)
	}
}

// SerializePartialSignature encodes the partial signature to a fixed size byte array.
func SerializePartialSignature(
	sig *btcecmusig2.PartialSignature,
) ([input.MuSig2PartialSigSize]byte, error) {

	var (
		buf    bytes.Buffer
		result [input.MuSig2PartialSigSize]byte
	)
	if err := sig.Encode(&buf); err != nil {
		return result, errors.Errorf("error encoding partial signature: "+
			"%w", err)
	}

	if buf.Len() != input.MuSig2PartialSigSize {
		return result, errors.Errorf("invalid partial signature length, "+
			"got %d wanted %d", buf.Len(), input.MuSig2PartialSigSize)
	}

	copy(result[:], buf.Bytes())

	return result, nil
}

// DeserializePartialSignature decodes a partial signature from a byte slice.
func DeserializePartialSignature(
	scalarBytes []byte,
) (*btcecmusig2.PartialSignature, error) {

	if len(scalarBytes) != input.MuSig2PartialSigSize {
		return nil, errors.Errorf("invalid partial signature length, got "+
			"%d wanted %d", len(scalarBytes), input.MuSig2PartialSigSize)
	}

	sig := &btcecmusig2.PartialSignature{}
	if err := sig.Decode(bytes.NewReader(scalarBytes)); err != nil {
		return nil, errors.Errorf("error decoding partial signature: %w",
			err)
	}

	return sig, nil
}
