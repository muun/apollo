package testutils

import (
	"encoding/hex"

	"github.com/btcsuite/btcd/btcec/v2"

	"github.com/muun/libwallet/cryptography/bitcoin_hpke"
	"github.com/muun/libwallet/domain/model/encrypted_key_v3"
	"github.com/muun/libwallet/service/model"
)

// BuildVerifiableMuunKeyJson creates a valid VerifiableMuunKeyJson for testing.
// The muun key is split into two halves: firstHalf encrypted to the user's public key,
// secondHalf encrypted to the recovery code's public key. If withProof is true, includes
// "mock_proof" which bypasses ZK verification in test mode.
func BuildVerifiableMuunKeyJson( //nolint:staticcheck // TODO: func BuildVerifiableMuunKeyJson should be BuildVerifiableMuunKeyJSON
	testKeys *TestKeys,
	withProof bool,
) *model.VerifiableMuunKeyJson {
	// Split the muun private key: muunPrivKey = firstHalf + secondHalf
	firstHalfKey, err := btcec.NewPrivateKey()
	if err != nil {
		panic("failed to generate first half key: " + err.Error())
	}

	muunECPrivateKey, err := testKeys.MuunKey.ECPrivateKey()
	if err != nil {
		panic("failed to get muun EC private key: " + err.Error())
	}

	secondHalfKeyBytes := new(btcec.ModNScalar).
		Set(&firstHalfKey.Key).
		Negate().
		Add(&muunECPrivateKey.Key).
		Bytes()

	// Encrypt first half to user's public key (this is what Verify() will decrypt)
	userECPubKey, err := testKeys.UserKey.PublicKey().ECPubKey()
	if err != nil {
		panic("failed to get user EC public key: " + err.Error())
	}

	firstHalfEncToClient, err := bitcoin_hpke.SingleShotEncrypt(
		firstHalfKey.Serialize(),
		userECPubKey,
		[]byte(encrypted_key_v3.MuunFirstHalfToClient),
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
		[]byte(encrypted_key_v3.MuunSecondHalfToRecoveryCode),
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

	return &model.VerifiableMuunKeyJson{
		FirstHalfKeyEncryptedToClient:        hex.EncodeToString(firstHalfEncToClient.Serialize()),
		SecondHalfKeyEncryptedToRecoveryCode: hex.EncodeToString(secondHalfEncToRC.Serialize()),
		Proof:                                proof,
	}
}

// BuildInvalidVerifiableMuunKeyJson returns a VerifiableMuunKeyJson with invalid hex data
// that will cause parsing to fail inside ComputeAndStoreEncryptedMuunKeyAction.
func BuildInvalidVerifiableMuunKeyJson() model.VerifiableMuunKeyJson { //nolint:staticcheck // TODO: func BuildInvalidVerifiableMuunKeyJson should be BuildInvalidVerifiableMuunKeyJSON
	return model.VerifiableMuunKeyJson{
		FirstHalfKeyEncryptedToClient:        "not-valid-hex",
		SecondHalfKeyEncryptedToRecoveryCode: "not-valid-hex",
		Proof:                                nil,
	}
}
