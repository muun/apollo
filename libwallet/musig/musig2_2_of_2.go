package musig

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

// ComputeMuunPartialSignature computes the first part of the 2-2 signature.
// Returns a valid partial signature.
//
// Deprecated: only used in V5 and V6. New code uses MuSig2 v100 only; use
// ComputePartialSignature2Of2 instead.
func ComputeMuunPartialSignature(
	musigVersion MusigVersion,
	data []byte,
	userPublicKeyBytes []byte,
	muunPrivateKeyBytes []byte,
	rawUserPublicNonce []byte,
	muunSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	muunPrivateKey := secp256k1.PrivKeyFromBytes(muunPrivateKeyBytes)
	muunPublicKeyBytes := muunPrivateKey.PubKey().SerializeCompressed()

	return ComputePartialSignature(
		musigVersion,
		data,
		muunPrivateKeyBytes,
		[][]byte{userPublicKeyBytes, muunPublicKeyBytes},
		[][]byte{rawUserPublicNonce},
		muunSessionID,
		tweak,
	)
}

// ComputeUserPartialSignature computes the last part of the 2-2 signature.
// Final signature is ensured to be valid.
//
// Deprecated: only used in V5 and V6. New code uses MuSig2 v100 only; use
// ComputeFinalSignature2Of2 instead.
func ComputeUserPartialSignature(
	musigVersion MusigVersion,
	data []byte,
	userPrivateKeyBytes []byte,
	muunPublicKeyBytes []byte,
	muunPartialSigBytes []byte,
	muunPublicNonceBytes []byte,
	userSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	userPrivateKey := secp256k1.PrivKeyFromBytes(userPrivateKeyBytes)
	userPublicKeyBytes := userPrivateKey.PubKey().SerializeCompressed()

	return ComputeFinalSignature(
		musigVersion,
		data,
		userPrivateKeyBytes,
		[][]byte{userPublicKeyBytes, muunPublicKeyBytes},
		[][]byte{muunPublicNonceBytes},
		[][]byte{muunPartialSigBytes},
		userSessionID,
		tweak,
	)
}

// ComputePartialSignature2Of2 computes the first part of the 2-of-2 signature
// using MuSig2 v100. Returns a valid partial signature.
func ComputePartialSignature2Of2(
	data []byte,
	currentSignerPrivateKeyBytes []byte,
	otherSignerPublicKeyBytes []byte,
	otherSignerPublicNonceBytes []byte,
	currentSignerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	currentSignerPrivateKey := secp256k1.PrivKeyFromBytes(currentSignerPrivateKeyBytes)
	currentSignerPublicKeyBytes := currentSignerPrivateKey.PubKey().SerializeCompressed()

	return ComputePartialSignature(
		Musig2v100,
		data,
		currentSignerPrivateKeyBytes,
		[][]byte{currentSignerPublicKeyBytes, otherSignerPublicKeyBytes},
		[][]byte{otherSignerPublicNonceBytes},
		currentSignerSessionID,
		tweak,
	)
}

// ComputeFinalSignature2Of2 computes the last part of the 2-of-2 signature
// using MuSig2 v100. Final signature is ensured to be valid.
func ComputeFinalSignature2Of2(
	data []byte,
	currentSignerPrivateKeyBytes []byte,
	otherSignerPublicKeyBytes []byte,
	otherSignerPublicNonceBytes []byte,
	otherSignerPartialSigBytes []byte,
	currentSignerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	currentSignerPrivateKey := secp256k1.PrivKeyFromBytes(currentSignerPrivateKeyBytes)
	currentSignerPublicKeyBytes := currentSignerPrivateKey.PubKey().SerializeCompressed()

	return ComputeFinalSignature(
		Musig2v100,
		data,
		currentSignerPrivateKeyBytes,
		[][]byte{currentSignerPublicKeyBytes, otherSignerPublicKeyBytes},
		[][]byte{otherSignerPublicNonceBytes},
		[][]byte{otherSignerPartialSigBytes},
		currentSignerSessionID,
		tweak,
	)
}

// ComputeFullSignature2Of2 runs a full 2-of-2 MuSig2 session with both private
// keys using MuSig2 v100 and returns the final aggregated signature.
func ComputeFullSignature2Of2(
	data []byte,
	signerAKey, signerBKey *btcec.PrivateKey,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	signerAPublicKeyBytes := signerAKey.PubKey().SerializeCompressed()
	signerBPublicKeyBytes := signerBKey.PubKey().SerializeCompressed()

	signerASessionID := RandomSessionID()
	signerBSessionID := RandomSessionID()

	signerANonce, err := MuSig2GenerateNonce(
		Musig2v100, signerASessionID[:], signerAPublicKeyBytes,
	)
	if err != nil {
		return nil, err
	}
	signerBNonce, err := MuSig2GenerateNonce(
		Musig2v100, signerBSessionID[:], signerBPublicKeyBytes,
	)
	if err != nil {
		return nil, err
	}

	signerBPartialSig, err := ComputePartialSignature2Of2(
		data,
		signerBKey.Serialize(),
		signerAPublicKeyBytes,
		signerANonce.PubNonce[:],
		signerBSessionID[:],
		tweak,
	)
	if err != nil {
		return nil, err
	}

	return ComputeFinalSignature2Of2(
		data,
		signerAKey.Serialize(),
		signerBPublicKeyBytes,
		signerBNonce.PubNonce[:],
		signerBPartialSig,
		signerASessionID[:],
		tweak,
	)
}
