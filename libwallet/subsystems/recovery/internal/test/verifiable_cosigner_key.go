package test

import (
	"encoding/hex"

	"github.com/btcsuite/btcd/btcec/v2"

	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/domain/cryptography/bitcoin_hpke"
	recovery_model "github.com/muun/libwallet/subsystems/recovery/presentation/model"
)

// BuildVerifiableCosignerKeyJSON creates a valid VerifiableCosignerKeyJSON for testing.
// The cosigner key is split into two halves: firstHalf encrypted to the user's public key,
// secondHalf encrypted to the recovery code's public key. If withProof is true, includes
// "mock_proof" which bypasses ZK verification in test mode.
func BuildVerifiableCosignerKeyJSON(
	testKeys *TestKeys,
	withProof bool,
) *model.VerifiableCosignerKeyJSON {
	// Split the cosigner private key: cosignerPrivKey = firstHalf + secondHalf
	firstHalfKey, err := btcec.NewPrivateKey()
	if err != nil {
		panic("failed to generate first half key: " + err.Error())
	}

	cosignerECPrivateKey, err := testKeys.CosignerKey.ECPrivateKey()
	if err != nil {
		panic("failed to get cosigner EC private key: " + err.Error())
	}

	secondHalfKeyBytes := new(btcec.ModNScalar).
		Set(&firstHalfKey.Key).
		Negate().
		Add(&cosignerECPrivateKey.Key).
		Bytes()

	// Encrypt first half to user's public key (this is what Verify() will decrypt)
	userECPubKey, err := testKeys.UserKey.PublicKey().ECPubKey()
	if err != nil {
		panic("failed to get user EC public key: " + err.Error())
	}

	firstHalfEncToClient, err := bitcoin_hpke.SingleShotEncrypt(
		firstHalfKey.Serialize(),
		userECPubKey,
		[]byte(recovery_model.CosignerFirstHalfToClient),
		[]byte(""),
	)
	if err != nil {
		panic("failed to encrypt first half to client: " + err.Error())
	}

	// Encrypt second half to recovery code's public key
	rcPubKey := testKeys.RecoveryCodeKey.PubKey()

	secondHalfEncToRC, err := bitcoin_hpke.SingleShotEncrypt(
		secondHalfKeyBytes[:],
		rcPubKey,
		[]byte(recovery_model.CosignerSecondHalfToRecoveryCode),
		[]byte(""),
	)
	if err != nil {
		panic("failed to encrypt second half to recovery code: " + err.Error())
	}

	var proof *string
	if withProof {
		p := "mock_proof"
		proof = &p
	}

	return &model.VerifiableCosignerKeyJSON{
		FirstHalfKeyEncryptedToClient:        hex.EncodeToString(firstHalfEncToClient.Serialize()),
		SecondHalfKeyEncryptedToRecoveryCode: hex.EncodeToString(secondHalfEncToRC.Serialize()),
		Proof:                                proof,
	}
}

// BuildInvalidVerifiableCosignerKeyJSON returns a VerifiableCosignerKeyJSON with invalid hex data
// that will cause parsing to fail inside ComputeAndStoreEncryptedCosignerKeyAction.
func BuildInvalidVerifiableCosignerKeyJSON() model.VerifiableCosignerKeyJSON {
	return model.VerifiableCosignerKeyJSON{
		FirstHalfKeyEncryptedToClient:        "not-valid-hex",
		SecondHalfKeyEncryptedToRecoveryCode: "not-valid-hex",
		Proof:                                nil,
	}
}
