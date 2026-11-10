package musig

// This file contains Muun specific function that interact with the MuSig2
// contexts. Code specific to differentiate MuSig versions should not exist in
// this file.

import (
	"crypto/rand"

	"github.com/go-errors/errors"
)

// RandomSessionID returns a safe random session id. Session IDs must not be
// repeated otherwise private keys are compromised.
func RandomSessionID() [32]byte {
	var buf [32]byte
	_, err := rand.Read(buf[:])
	if err != nil {
		panic("couldn't read random bytes")
	}

	return buf
}

// ComputePartialSignature produces one signer's partial signature for an N-of-N
// MuSig2 session. allParticipantPublicKeys lists every participant's public key,
// and otherParticipantPublicNonces holds the public nonces of every
// other participant.
func ComputePartialSignature(
	musigVersion MusigVersion,
	data []byte,
	signerPrivateKeyBytes []byte,
	allParticipantPublicKeys [][]byte,
	otherParticipantPublicNonces [][]byte,
	signerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	if len(data) != 32 {
		return nil, errors.Errorf("expected 32-byte data, got %d", len(data))
	}
	if len(signerPrivateKeyBytes) != 32 {
		return nil, errors.Errorf("expected a 32-byte private key, got %d",
			len(signerPrivateKeyBytes))
	}
	if len(signerSessionID) != 32 {
		return nil, errors.Errorf("expected a 32-byte session id, got %d",
			len(signerSessionID))
	}

	signerPublicKeys, err := MuSig2ParsePubKeys(musigVersion, allParticipantPublicKeys)
	if err != nil {
		return nil, err
	}

	if len(otherParticipantPublicNonces) != len(allParticipantPublicKeys)-1 {
		return nil, errors.New("some nonces are missing")
	}

	for idx, pubNonce := range otherParticipantPublicNonces {
		if len(pubNonce) != 66 {
			return nil, errors.Errorf("expected 66-byte nonce at index %d, got %d",
				idx, len(pubNonce))
		}
	}

	return muSig2ComputePartialSignature(
		musigVersion,
		data,
		signerPrivateKeyBytes,
		signerPublicKeys,
		otherParticipantPublicNonces,
		signerSessionID,
		tweak,
	)
}

// VerifyPartialSignature checks one participant's partial signature of an
// N-of-N MuSig2 session. Verification only uses public data: the signer is
// named by its public key and public nonce.
func VerifyPartialSignature(
	musigVersion MusigVersion,
	data []byte,
	signerPublicKeyBytes []byte,
	signerPublicNonceBytes []byte,
	allParticipantPublicKeys [][]byte,
	allParticipantPublicNonces [][]byte,
	partialSignatureBytes []byte,
	tweak *MuSig2Tweaks,
) error {

	if len(data) != 32 {
		return errors.Errorf("expected 32 bytes of data to verify, got %d", len(data))
	}
	if len(signerPublicNonceBytes) != 66 {
		return errors.Errorf(
			"expected a 66-byte signer public nonce, got %d", len(signerPublicNonceBytes))
	}
	if len(partialSignatureBytes) != 32 {
		return errors.Errorf("expected a 32-byte partial signature, got %d",
			len(partialSignatureBytes))
	}
	if len(allParticipantPublicNonces) != len(allParticipantPublicKeys) {
		return errors.Errorf("expected %d nonces, got %d",
			len(allParticipantPublicKeys), len(allParticipantPublicNonces))
	}

	signerPublicKey, err := ParsePubKey(musigVersion, signerPublicKeyBytes)
	if err != nil {
		return err
	}

	signerPublicKeys, err := MuSig2ParsePubKeys(musigVersion, allParticipantPublicKeys)
	if err != nil {
		return err
	}

	for idx, pubNonce := range allParticipantPublicNonces {
		if len(pubNonce) != 66 {
			return errors.Errorf("expected 66-byte nonce at index %d, got %d",
				idx, len(pubNonce))
		}
	}

	return muSig2VerifyPartialSignature(
		musigVersion,
		data,
		signerPublicKey,
		signerPublicNonceBytes,
		signerPublicKeys,
		allParticipantPublicNonces,
		partialSignatureBytes,
		tweak,
	)
}

// CombineSignatures aggregates the partial signatures of every participant of
// an N-of-N MuSig2 session into the final signature. The returned signature is
// validated against the aggregated public key.
func CombineSignatures(
	musigVersion MusigVersion,
	data []byte,
	allParticipantPublicKeys [][]byte,
	allParticipantPublicNonces [][]byte,
	allParticipantPartialSignatures [][]byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	if len(data) != 32 {
		return nil, errors.Errorf("expected 32-byte data, got %d", len(data))
	}

	signerPublicKeys, err := MuSig2ParsePubKeys(musigVersion, allParticipantPublicKeys)
	if err != nil {
		return nil, err
	}

	if len(allParticipantPublicNonces) != len(allParticipantPublicKeys) {
		return nil, errors.Errorf("expected %d nonces, got %d",
			len(allParticipantPublicKeys), len(allParticipantPublicNonces))
	}
	if len(allParticipantPartialSignatures) != len(allParticipantPublicKeys) {
		return nil, errors.Errorf("expected %d signatures, got %d",
			len(allParticipantPublicKeys), len(allParticipantPartialSignatures))
	}

	for idx, pubNonce := range allParticipantPublicNonces {
		if len(pubNonce) != 66 {
			return nil, errors.Errorf("expected 66-byte nonce at index %d, got %d",
				idx, len(pubNonce))
		}
	}

	for idx, partialSig := range allParticipantPartialSignatures {
		if len(partialSig) != 32 {
			return nil, errors.Errorf(
				"expected 32-byte partial signature at index %d, got %d",
				idx, len(partialSig))
		}
	}

	return muSig2CombineSignatures(
		musigVersion,
		data,
		signerPublicKeys,
		allParticipantPublicNonces,
		allParticipantPartialSignatures,
		tweak,
	)
}
