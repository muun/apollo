package verifiable_cosigner_key

import (
	"encoding/base64"
	"encoding/hex"
	"log/slog"
	"math/big"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/domain/cryptography/bitcoin_hpke"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/librs"
	recovery_model "github.com/muun/libwallet/subsystems/recovery/presentation/model"
)

// Declared here, next to its caller, so that this package does not depend on the action
// package that implements it.
type CosignerKeyEncryptionFinisher interface {
	Run(
		recoveryCodePublicKey *btcec.PublicKey,
		firstHalfKey *btcec.PrivateKey,
		chainCode []byte,
		secondHalfKeyEncryptedToRecoveryCode *bitcoin_hpke.EncryptedMessage,
	) (string, error)
}

type VerifiableCosignerKey struct {
	FirstHalfKeyEncryptedToClient        *bitcoin_hpke.EncryptedMessage
	SecondHalfKeyEncryptedToRecoveryCode *bitcoin_hpke.EncryptedMessage
	Proof                                *string
}

func NewVerifiableCosignerKey(
	firstHalf *bitcoin_hpke.EncryptedMessage,
	secondHalf *bitcoin_hpke.EncryptedMessage,
	proof *string,
) *VerifiableCosignerKey {
	return &VerifiableCosignerKey{
		FirstHalfKeyEncryptedToClient:        firstHalf,
		SecondHalfKeyEncryptedToRecoveryCode: secondHalf,
		Proof:                                proof,
	}
}

func NewVerifiableCosignerKeyFromJSON(
	verifiableCosignerKeyJSON *model.VerifiableCosignerKeyJSON,
) (*VerifiableCosignerKey, error) {

	firstHalfKeyEncryptedToClientBytes, err := hex.DecodeString(
		verifiableCosignerKeyJSON.FirstHalfKeyEncryptedToClient,
	)
	if err != nil {
		return nil, err
	}

	firstHalfKeyEncryptedToClient, err := bitcoin_hpke.ParseEncryptedMessage(
		firstHalfKeyEncryptedToClientBytes,
	)
	if err != nil {
		return nil, err
	}

	secondHalfKeyEncryptedToRecoveryCodeBytes, err := hex.DecodeString(
		verifiableCosignerKeyJSON.SecondHalfKeyEncryptedToRecoveryCode,
	)
	if err != nil {
		return nil, err
	}

	secondHalfKeyEncryptedToRecoveryCode, err := bitcoin_hpke.ParseEncryptedMessage(
		secondHalfKeyEncryptedToRecoveryCodeBytes,
	)
	if err != nil {
		return nil, err
	}

	return NewVerifiableCosignerKey(
		firstHalfKeyEncryptedToClient,
		secondHalfKeyEncryptedToRecoveryCode,
		verifiableCosignerKeyJSON.Proof,
	), nil

}

// Verify returning an EncryptedCosignerKeyWithVerificationFlag.
func (vk *VerifiableCosignerKey) Verify(
	cosignerPublicKey *hd_keys.HDPublicKey,
	userPrivateKey *btcec.PrivateKey,
	recoveryCodePublicKey *btcec.PublicKey,
	finisher CosignerKeyEncryptionFinisher,
) (*EncryptedCosignerKeyWithVerificationFlag, error) {

	cosignerBtcecPubKey, err := cosignerPublicKey.ECPubKey()
	if err != nil {
		return nil, err
	}

	firstHalfKeyBytes, err := vk.FirstHalfKeyEncryptedToClient.SingleShotDecrypt(
		userPrivateKey,
		[]byte(recovery_model.CosignerFirstHalfToClient),
		[]byte(""),
	)
	if err != nil {
		return nil, err
	}
	if len(firstHalfKeyBytes) != 32 {
		return nil, errors.Errorf("firstHalfKeyBytes should be 32 bytes")
	}

	firstHalfKey, firstHalfPubKey := btcec.PrivKeyFromBytes(firstHalfKeyBytes)

	var secondHalfPubkey = SubtractPublicKeys(cosignerBtcecPubKey, firstHalfPubKey)

	var verified bool
	if vk.Proof == nil {
		// If no proof is provided we produce an unverified encryptedCosignerKey
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

	encryptedCosignerKey, err := finisher.Run(
		recoveryCodePublicKey,
		firstHalfKey,
		cosignerPublicKey.ChainCode(),
		vk.SecondHalfKeyEncryptedToRecoveryCode,
	)
	if err != nil {
		return nil, err
	}

	return NewEncryptedCosignerKeyWithVerificationFlag(encryptedCosignerKey, verified), nil
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

	result := string(
		librs.Plonky2ServerKeyVerify(
			proofBytes,
			recoveryCodePublicKey.SerializeUncompressed(),
			secondHalfKeyEncryptedToRecoveryCode.GetEncapsulatedKey().SerializeUncompressed(),
			ciphertext,
			secondHalfPubkey.SerializeUncompressed(),
		),
	)
	if result != "ok" {
		slog.Error("error calling librs.Plonky2ServerKeyVerify", slog.Any("error", err))
		return false
	}

	return true
}

// SubtractPublicKeys computes the subtraction A - B.
func SubtractPublicKeys(A, B *btcec.PublicKey) *btcec.PublicKey {
	// Recall that -B is given by (B.X, -B.Y). Note also that since B is on the curve, B.Y cannot be
	// zero and therefore P-B.Y is already reduced modulo P. Thus there is no need to reduce modulo
	// P in the line below.
	rX, rY := btcec.S256().Add(A.X(), A.Y(), B.X(), new(big.Int).Sub(btcec.S256().P, B.Y()))
	var X, Y btcec.FieldVal
	X.SetByteSlice(rX.FillBytes(make([]byte, 32)))
	Y.SetByteSlice(rY.FillBytes(make([]byte, 32)))
	return btcec.NewPublicKey(&X, &Y)
}
