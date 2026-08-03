package musig

// This file contains Muun specific function that interact with the MuSig2
// contexts. Code specific to differentiate MuSig versions should not exist in
// this file.

import (
	"crypto/rand"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
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

	signerPrivateKey := secp256k1.PrivKeyFromBytes(signerPrivateKeyBytes)
	signerPublicKeyBytes := signerPrivateKey.PubKey().SerializeCompressed()

	signerPublicKeys, err := MuSig2ParsePubKeys(musigVersion, allParticipantPublicKeys)
	if err != nil {
		return nil, err
	}

	// As we'd like the local nonce we send over to be generated
	// deterministically, we'll provide a random sessionID as the primary
	// randomness source.
	signerNonce, err := MuSig2GenerateNonce(musigVersion, signerSessionID, signerPublicKeyBytes)
	if err != nil {
		return nil, err
	}

	// Create a signing context and session with the given private key and
	// list of all known participant public keys.
	_, session, err := MuSig2CreateContext(
		musigVersion,
		signerPrivateKey,
		signerPublicKeys,
		tweak,
		signerNonce,
	)
	if err != nil {
		return nil, err
	}

	// Add all nonces from the other participants.
	haveAllNonces := false
	for _, otherNonce := range otherParticipantPublicNonces {
		haveAllNonces, err = session.RegisterPubNonce([66]byte(otherNonce))
		if err != nil {
			return nil, err
		}
	}
	if !haveAllNonces {
		return nil, errors.New("some nonces are missing")
	}

	sig, err := MuSig2Sign(session, ([32]byte)(data))
	if err != nil {
		return nil, err
	}

	ret, err := SerializePartialSignature(sig)
	if err != nil {
		return nil, err
	}

	return ret[:], nil
}

// ComputeFinalSignature produces the final aggregate signature for an N-of-N
// MuSig2 session. The calling signer contributes its own partial signature and
// combines it with otherParticipantPartialSignatures (the partials of every other participant).
// The returned signature is validated.
func ComputeFinalSignature(
	musigVersion MusigVersion,
	data []byte,
	signerPrivateKeyBytes []byte,
	allParticipantPublicKeys [][]byte,
	otherParticipantPublicNonces [][]byte,
	otherParticipantPartialSignatures [][]byte,
	signerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	signerPrivateKey := secp256k1.PrivKeyFromBytes(signerPrivateKeyBytes)
	signerPublicKeyBytes := signerPrivateKey.PubKey().SerializeCompressed()

	signerPublicKeys, err := MuSig2ParsePubKeys(musigVersion, allParticipantPublicKeys)
	if err != nil {
		return nil, err
	}

	// As we'd like the local nonce we send over to be generated
	// deterministically, we'll provide a random sessionID as the primary
	// randomness source.
	signerNonce, err := MuSig2GenerateNonce(musigVersion, signerSessionID, signerPublicKeyBytes)
	if err != nil {
		return nil, err
	}

	// Create a signing context and session with the given private key and
	// list of all known participant public keys.
	_, session, err := MuSig2CreateContext(
		musigVersion,
		signerPrivateKey,
		signerPublicKeys,
		tweak,
		signerNonce,
	)
	if err != nil {
		return nil, err
	}

	// Add all nonces from the other participants.
	haveAllNonces := false
	for _, otherNonce := range otherParticipantPublicNonces {
		haveAllNonces, err = session.RegisterPubNonce([66]byte(otherNonce))
		if err != nil {
			return nil, err
		}
	}
	if !haveAllNonces {
		return nil, errors.New("some nonces are missing")
	}

	if _, err = MuSig2Sign(session, ([32]byte)(data)); err != nil {
		return nil, err
	}

	// Combine the partial signatures from the other participants.
	haveAllSigs := false
	for _, otherPartialSig := range otherParticipantPartialSignatures {
		partialSig, err := DeserializePartialSignature(otherPartialSig)
		if err != nil {
			return nil, err
		}
		haveAllSigs, err = MuSig2CombineSig(session, partialSig)
		if err != nil {
			return nil, err
		}
	}
	if !haveAllSigs {
		return nil, errors.New("some signatures are still missing")
	}

	// FinalSig() also validates the signature
	sig := session.FinalSig()

	return sig.Serialize()[:], nil
}
