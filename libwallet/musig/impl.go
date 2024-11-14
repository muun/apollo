package musig

// This file contains Muun specific function that interact with the MuSig2
// contexts. Code specific to differentiate MuSig versions should not exist in
// this file.

import (
	"crypto/rand"
	"errors"

	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

// RandomSessionId returns a safe random session id. Session IDs must not be
// repeated otherwise private keys are compromised.
func RandomSessionId() [32]byte {
	var buf [32]byte
	_, err := rand.Read(buf[:])
	if err != nil {
		panic("couldn't read random bytes")
	}

	return buf
}

func VerifySignature(
	musigVersion MusigVersion,
	data []byte,
	publicKey []byte,
	signature []byte,
) (bool, error) {

	pubKey, err := ParsePubKey(musigVersion, publicKey)
	if err != nil {
		return false, err
	}

	sig, err := schnorr.ParseSignature(signature)
	if err != nil {
		return false, err
	}

	return sig.Verify(data, pubKey), nil
}

// Computes the first part of the 2-2 signature.
// Returns a valid partial signature.
func ComputeMuunPartialSignature(
	musigVersion MusigVersion,
	data []byte,
	userPublicKeyBytes []byte,
	muunPrivateKeyBytes []byte,
	rawUserPublicNonce []byte,
	muunSessionId []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	muunPrivateKey := secp256k1.PrivKeyFromBytes(muunPrivateKeyBytes)
	muunPublicKey := muunPrivateKey.PubKey()
	muunPublicKeyBytes := muunPublicKey.SerializeCompressed()

	signerPublicKeys, err := MuSig2ParsePubKeys(musigVersion, [][]byte{
		userPublicKeyBytes,
		muunPublicKeyBytes,
	})
	if err != nil {
		return nil, err
	}

	// As we'd like the local nonce we send over to be generated
	// deterministically, we'll provide a random sessionId as the primary
	// randomness source.
	muunNonce, err := MuSig2GenerateNonce(musigVersion, muunSessionId, muunPublicKeyBytes)
	if err != nil {
		return nil, err
	}

	// Create a signing context and session with the given private key and
	// list of all known signer public keys.
	_, session, err := MuSig2CreateContext(
		musigVersion,
		muunPrivateKey,
		signerPublicKeys,
		tweak,
		muunNonce,
	)
	if err != nil {
		return nil, err
	}

	// Add all nonces we might've learned so far.
	haveAllNonces := false
	if haveAllNonces, err = session.RegisterPubNonce(
		[66]byte(rawUserPublicNonce)); err != nil {
		return nil, err
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

// Computes the last part of the 2-2 signature.
// Final signature is ensured to be valid.
func ComputeUserPartialSignature(
	musigVersion MusigVersion,
	data []byte,
	userPrivateKeyBytes []byte,
	muunPublicKeyBytes []byte,
	muunPartialSigBytes []byte,
	muunPublicNonceBytes []byte,
	userSessionId []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	userPrivateKey := secp256k1.PrivKeyFromBytes(userPrivateKeyBytes)
	userPublicKey := userPrivateKey.PubKey()
	userPublicKeyBytes := userPublicKey.SerializeCompressed()

	pubKeys := [][]byte{
		userPublicKeyBytes,
		muunPublicKeyBytes,
	}
	signerPublicKeys, err := MuSig2ParsePubKeys(musigVersion, pubKeys)
	if err != nil {
		return nil, err
	}

	// As we'd like the local nonce we send over to be generated
	// deterministically, we'll provide a random sessionId as the primary
	// randomness source.
	userNonce, err := MuSig2GenerateNonce(musigVersion, userSessionId, userPublicKeyBytes)
	if err != nil {
		return nil, err
	}

	// Create a signing context and session with the given private key and
	// list of all known signer public keys.
	_, session, err := MuSig2CreateContext(
		musigVersion,
		userPrivateKey,
		signerPublicKeys,
		tweak,
		userNonce,
	)
	if err != nil {
		return nil, err
	}

	// Add all nonces we might've learned so far.
	haveAllNonces := false
	if haveAllNonces, err = session.RegisterPubNonce(
		[66]byte(muunPublicNonceBytes)); err != nil {
		return nil, err
	}
	if !haveAllNonces {
		return nil, errors.New("some nonces are missing")
	}

	_, err = MuSig2Sign(session, ([32]byte)(data))
	if err != nil {
		return nil, err
	}

	muunSig, err := DeserializePartialSignature(muunPartialSigBytes)
	if err != nil {
		return nil, err
	}

	haveAllSigs, err := MuSig2CombineSig(session, muunSig)
	if err != nil {
		return nil, err
	}
	if !haveAllSigs {
		return nil, errors.New("some signatures are still missing")
	}

	// FinalSig() also validates the signature
	sig := session.FinalSig()

	return sig.Serialize()[:], nil
}
