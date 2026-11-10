package musig

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

// ComputeCosignerPartialSignature computes the first part of the 2-2 signature.
// Returns a valid partial signature.
//
// Deprecated: only used in V5 and V6. New code uses MuSig2 v100 only; use
// ComputePartialSignature2Of2 instead.
func ComputeCosignerPartialSignature(
	musigVersion MusigVersion,
	data []byte,
	userPublicKeyBytes []byte,
	cosignerPrivateKeyBytes []byte,
	rawUserPublicNonce []byte,
	cosignerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	cosignerPrivateKey := secp256k1.PrivKeyFromBytes(cosignerPrivateKeyBytes)
	cosignerPublicKeyBytes := cosignerPrivateKey.PubKey().SerializeCompressed()

	return ComputePartialSignature(
		musigVersion,
		data,
		cosignerPrivateKeyBytes,
		[][]byte{userPublicKeyBytes, cosignerPublicKeyBytes},
		[][]byte{rawUserPublicNonce},
		cosignerSessionID,
		tweak,
	)
}

// ComputeUserPartialSignature computes the last part of the 2-2 signature.
// Final signature is ensured to be valid.
//
// Deprecated: only used in V5 and V6. New code uses MuSig2 v100 only; use
// ComputePartialSignature2Of2 with CombineSignatures2Of2 instead.
func ComputeUserPartialSignature(
	musigVersion MusigVersion,
	data []byte,
	userPrivateKeyBytes []byte,
	cosignerPublicKeyBytes []byte,
	cosignerPartialSigBytes []byte,
	cosignerPublicNonceBytes []byte,
	userSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	userPrivateKey := secp256k1.PrivKeyFromBytes(userPrivateKeyBytes)
	userPublicKeyBytes := userPrivateKey.PubKey().SerializeCompressed()

	userPartialSig, err := ComputePartialSignature(
		musigVersion,
		data,
		userPrivateKeyBytes,
		[][]byte{userPublicKeyBytes, cosignerPublicKeyBytes},
		[][]byte{cosignerPublicNonceBytes},
		userSessionID,
		tweak,
	)
	if err != nil {
		return nil, err
	}

	userNonce, err := MuSig2GenerateNonce(musigVersion, userSessionID, userPublicKeyBytes)
	if err != nil {
		return nil, err
	}

	return CombineSignatures(
		musigVersion,
		data,
		[][]byte{userPublicKeyBytes, cosignerPublicKeyBytes},
		[][]byte{userNonce.PubNonce[:], cosignerPublicNonceBytes},
		[][]byte{userPartialSig, cosignerPartialSigBytes},
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

// CombineSignatures2Of2 combines the two partial signatures of a 2-of-2
// MuSig2 v100 session into the final signature. Final signature is ensured to
// be valid.
func CombineSignatures2Of2(
	data []byte,
	signer1PublicKeyBytes []byte,
	signer2PublicKeyBytes []byte,
	signer1PublicNonceBytes []byte,
	signer2PublicNonceBytes []byte,
	signer1PartialSigBytes []byte,
	signer2PartialSigBytes []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {
	return CombineSignatures(
		Musig2v100,
		data,
		[][]byte{signer1PublicKeyBytes, signer2PublicKeyBytes},
		[][]byte{signer1PublicNonceBytes, signer2PublicNonceBytes},
		[][]byte{signer1PartialSigBytes, signer2PartialSigBytes},
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

	signerAPartialSig, err := ComputePartialSignature2Of2(
		data,
		signerAKey.Serialize(),
		signerBPublicKeyBytes,
		signerBNonce.PubNonce[:],
		signerASessionID[:],
		tweak,
	)
	if err != nil {
		return nil, err
	}

	return CombineSignatures2Of2(
		data,
		signerAPublicKeyBytes,
		signerBPublicKeyBytes,
		signerANonce.PubNonce[:],
		signerBNonce.PubNonce[:],
		signerAPartialSig,
		signerBPartialSig,
		tweak,
	)
}
