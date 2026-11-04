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

// CombineSignatures3Of3 combines the three partial signatures of a 3-of-3
// MuSig2 v100 session into the final signature. Final signature is ensured to
// be valid.
func CombineSignatures3Of3(
	data []byte,
	signer1PublicKeyBytes []byte,
	signer2PublicKeyBytes []byte,
	signer3PublicKeyBytes []byte,
	allPublicNonces [][]byte,
	partialSignatures [][]byte,
	tweak *MuSig2Tweaks,
) ([]byte, error) {

	return CombineSignatures(
		Musig2v100,
		data,
		[][]byte{
			signer1PublicKeyBytes,
			signer2PublicKeyBytes,
			signer3PublicKeyBytes,
		},
		allPublicNonces,
		partialSignatures,
		tweak,
	)
}

// VerifyPartialSignature3Of3 checks one signer's partial signature of a
// 3-of-3 MuSig2 v100 session.
func VerifyPartialSignature3Of3(
	data []byte,
	signerPublicKeyBytes []byte,
	signerPublicNonceBytes []byte,
	otherSigner1PublicKeyBytes []byte,
	otherSigner2PublicKeyBytes []byte,
	otherSigner1PublicNonceBytes []byte,
	otherSigner2PublicNonceBytes []byte,
	partialSignatureBytes []byte,
	tweak *MuSig2Tweaks,
) error {
	return VerifyPartialSignature(
		Musig2v100,
		data,
		signerPublicKeyBytes,
		signerPublicNonceBytes,
		[][]byte{
			signerPublicKeyBytes,
			otherSigner1PublicKeyBytes,
			otherSigner2PublicKeyBytes,
		},
		[][]byte{
			signerPublicNonceBytes,
			otherSigner1PublicNonceBytes,
			otherSigner2PublicNonceBytes,
		},
		partialSignatureBytes,
		tweak,
	)
}
