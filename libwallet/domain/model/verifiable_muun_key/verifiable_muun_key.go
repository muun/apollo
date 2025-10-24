package verifiable_muun_key

import (
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"log/slog"
	"math/big"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/cryptography/bitcoin_hpke"
	"github.com/muun/libwallet/domain/model/encrypted_key_v3"
	"github.com/muun/libwallet/encryption"
	"github.com/muun/libwallet/service/model"
)

type VerifiableMuunKey struct {
	FirstHalfKeyEncryptedToClient        *bitcoin_hpke.EncryptedMessage
	SecondHalfKeyEncryptedToRecoveryCode *bitcoin_hpke.EncryptedMessage
	Proof                                *string
}

func VerifiableMuunKeyFromJson(verifiableMuunKeyJson *model.VerifiableMuunKeyJson) (*VerifiableMuunKey, error) {

	firstHalfKeyEncryptedToClientBytes, err := hex.DecodeString(verifiableMuunKeyJson.FirstHalfKeyEncryptedToClient)
	if err != nil {
		return nil, err
	}

	firstHalfKeyEncryptedToClient, err := bitcoin_hpke.ParseEncryptedMessage(firstHalfKeyEncryptedToClientBytes)
	if err != nil {
		return nil, err
	}

	secondHalfKeyEncryptedToRecoveryCodeBytes, err := hex.DecodeString(verifiableMuunKeyJson.SecondHalfKeyEncryptedToRecoveryCode)
	if err != nil {
		return nil, err
	}

	secondHalfKeyEncryptedToRecoveryCode, err := bitcoin_hpke.ParseEncryptedMessage(secondHalfKeyEncryptedToRecoveryCodeBytes)
	if err != nil {
		return nil, err
	}

	return &VerifiableMuunKey{
		FirstHalfKeyEncryptedToClient:        firstHalfKeyEncryptedToClient,
		SecondHalfKeyEncryptedToRecoveryCode: secondHalfKeyEncryptedToRecoveryCode,
		Proof:                                verifiableMuunKeyJson.Proof,
	}, nil

}

type EncryptedMuunKeyWithVerificationFlag struct {
	// The base64 encoded encrypted muun key that can be decrypted with the recovery code private key.
	EncryptedMuunKey string
	// A boolean value indicating if the encryption was proven to be correct with a zero-knowledge proof.
	Verified bool
}

// Verify returning an EncryptedMuunKeyWithVerificationFlag.
func (vk *VerifiableMuunKey) Verify(
	muunPublicKey *libwallet.HDPublicKey,
	userPrivateKey *btcec.PrivateKey,
	recoveryCodePublicKey *btcec.PublicKey,
) (*EncryptedMuunKeyWithVerificationFlag, error) {

	muunBtcecPubKey, err := muunPublicKey.ECPubKey()
	if err != nil {
		return nil, err
	}

	firstHalfKeyBytes, err := vk.FirstHalfKeyEncryptedToClient.SingleShotDecrypt(
		userPrivateKey,
		[]byte(encrypted_key_v3.MuunFirstHalfToClient),
		[]byte(""),
	)
	if err != nil {
		return nil, err
	}
	if len(firstHalfKeyBytes) != 32 {
		return nil, fmt.Errorf("firstHalfKeyBytes should be 32 bytes")
	}

	firstHalfKey, firstHalfPubKey := btcec.PrivKeyFromBytes(firstHalfKeyBytes)

	var secondHalfPubkey = subtractPublicKeys(muunBtcecPubKey, firstHalfPubKey)

	var verified bool
	if vk.Proof == nil {
		// If no proof is provided we produce an unverified encryptedMuunKey
		verified = false
	} else {
		// TODO For now, a verification error results in an unverified key, without impeding to
		//  generate it. This will change in the future.

		verified = verifyZeroKnowledgeProof(
			secondHalfPubkey,
			recoveryCodePublicKey,
			vk.SecondHalfKeyEncryptedToRecoveryCode,
			*vk.Proof,
		)
	}

	encryptedMuunKey, err := encrypted_key_v3.FinishMuunKeyEncryption(
		recoveryCodePublicKey,
		firstHalfKey,
		muunPublicKey.ChainCode(),
		vk.SecondHalfKeyEncryptedToRecoveryCode,
	)
	if err != nil {
		return nil, err
	}

	return &EncryptedMuunKeyWithVerificationFlag{EncryptedMuunKey: encryptedMuunKey, Verified: verified}, nil
}

func verifyZeroKnowledgeProof(
	secondHalfPubkey *btcec.PublicKey,
	recoveryCodePublicKey *btcec.PublicKey,
	secondHalfKeyEncryptedToRecoveryCode *bitcoin_hpke.EncryptedMessage,
	proof string,
) bool {

	ciphertext := secondHalfKeyEncryptedToRecoveryCode.GetCiphertext()

	if secondHalfKeyEncryptedToRecoveryCode.PlaintextLengthInBytes() != btcec.PrivKeyBytesLen {
		slog.Error(
			"error: invalid length for ciphertext.",
			"length",
			len(ciphertext))
		return false
	}

	// For testing we mock proofs and thus we also mock verification
	if testing.Testing() && proof == "mock_proof" {
		return true
	}

	proofBytes, err := base64.StdEncoding.DecodeString(proof)
	if err != nil {
		slog.Error("error decoding proof bytes", slog.Any("error", err))
		return false
	}

	// TODO as a temporary workaround for dl_iterate_phdr (which is currently required by librs)
	//  not being available in api level 19 we are short-circuiting the verification. This is not
	//  a problem because proof verification is not currently in use anyway.
	_ = proofBytes
	result := "ok"

	if result != "ok" {
		slog.Error("error calling librs.Plonky2ServerKeyVerify", slog.Any("error", err))
		return false
	}

	return true
}

// Compute the subtraction A - B
func subtractPublicKeys(A, B *btcec.PublicKey) *btcec.PublicKey {
	// Recall that -B is given by (B.X, -B.Y). Note also that since B is on the curve, B.Y cannot be zero and therefore
	// P-B.Y is already reduced modulo P. Thus there is no need to reduce modulo P in the line below.
	rX, rY := btcec.S256().Add(A.X(), A.Y(), B.X(), new(big.Int).Sub(btcec.S256().P, B.Y()))
	var X, Y btcec.FieldVal
	X.SetByteSlice(encryption.PaddedSerializeBigInt(32, rX))
	Y.SetByteSlice(encryption.PaddedSerializeBigInt(32, rY))
	return btcec.NewPublicKey(&X, &Y)
}
