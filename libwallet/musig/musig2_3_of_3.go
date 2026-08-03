package musig

import (
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

// ComputePartialSignature3Of3 computes one signer's part of the 3-of-3
// signature using MuSig2 v100. Returns a valid partial signature.
func ComputePartialSignature3Of3(
	data []byte,
	currentSignerPrivateKeyBytes []byte,
	otherSigner1PublicKeyBytes []byte,
	otherSigner2PublicKeyBytes []byte,
	otherSigner1PublicNonceBytes []byte,
	otherSigner2PublicNonceBytes []byte,
	currentSignerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	currentSignerPrivateKey := secp256k1.PrivKeyFromBytes(currentSignerPrivateKeyBytes)
	currentSignerPublicKeyBytes := currentSignerPrivateKey.PubKey().SerializeCompressed()

	return ComputePartialSignature(
		Musig2v100,
		data,
		currentSignerPrivateKeyBytes,
		[][]byte{
			currentSignerPublicKeyBytes,
			otherSigner1PublicKeyBytes,
			otherSigner2PublicKeyBytes,
		},
		[][]byte{otherSigner1PublicNonceBytes, otherSigner2PublicNonceBytes},
		currentSignerSessionID,
		tweak,
	)
}

// ComputeFinalSignature3Of3 computes the last part of the 3-of-3 signature
// using MuSig2 v100. Final signature is ensured to be valid.
func ComputeFinalSignature3Of3(
	data []byte,
	currentSignerPrivateKeyBytes []byte,
	otherSigner1PublicKeyBytes []byte,
	otherSigner2PublicKeyBytes []byte,
	otherSigner1PublicNonceBytes []byte,
	otherSigner2PublicNonceBytes []byte,
	otherSigner1PartialSigBytes []byte,
	otherSigner2PartialSigBytes []byte,
	currentSignerSessionID []byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	currentSignerPrivateKey := secp256k1.PrivKeyFromBytes(currentSignerPrivateKeyBytes)
	currentSignerPublicKeyBytes := currentSignerPrivateKey.PubKey().SerializeCompressed()

	return ComputeFinalSignature(
		Musig2v100,
		data,
		currentSignerPrivateKeyBytes,
		[][]byte{
			currentSignerPublicKeyBytes,
			otherSigner1PublicKeyBytes,
			otherSigner2PublicKeyBytes,
		},
		[][]byte{otherSigner1PublicNonceBytes, otherSigner2PublicNonceBytes},
		[][]byte{otherSigner1PartialSigBytes, otherSigner2PartialSigBytes},
		currentSignerSessionID,
		tweak,
	)
}
