package test

import (
	"encoding/hex"
	"testing"

	"github.com/go-errors/errors"
	"github.com/stretchr/testify/mock"

	adapted_mock "github.com/muun/libwallet/app_provided_data/adapted/mock"
	houston_mock "github.com/muun/libwallet/base/data/houston/mock"
	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/platform/test/teststorage"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/action/challenge_setup"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/action/encrypted_keys"
)

func TestFinishChallengeSetupAction(t *testing.T) {

	t.Run("calls houston and stores encrypted cosigner key", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		keys.ExpectProvidedBy(keyProvider)
		vmkJSON := BuildVerifiableCosignerKeyJSON(
			keys,
			true,
		)
		houston := houston_mock.NewMockHoustonService(t)
		wantRequest := model.ChallengeSetupVerifyJSON{
			ChallengeType: "RECOVERY_CODE",
			PublicKey:     hex.EncodeToString(keys.RecoveryCodeKey.PubKey().SerializeCompressed()),
		}
		houston.EXPECT().ChallengeSetupFinishWithVerifiableCosignerKey(wantRequest).
			Return(*vmkJSON, nil)
		computeAction := encrypted_keys.NewComputeAndStoreEncryptedCosignerKeyAction(
			kvStorage,
			keyProvider,
			encrypted_keys.NewFinishCosignerKeyEncryptionAction(),
		)
		action := challenge_setup.NewFinishChallengeSetupAction(houston, kvStorage, computeAction)

		// Test
		err := action.Run(keys.RecoveryCodeKey.PubKey())
		if err != nil {
			t.Fatalf("Run() error = %v", err)
		}

		// Verify encrypted cosigner key was stored
		got, err := kvStorage.Get(storage.VerifiedEncryptedCosignerKey)
		if err != nil {
			t.Fatalf("Get() error = %v", err)
		}
		if got == nil {
			t.Fatal("expected verified encrypted cosigner key to be stored")
		}
	})

	t.Run("swallows verification error", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		keys.ExpectProvidedBy(keyProvider)
		houston := houston_mock.NewMockHoustonService(t)
		houston.EXPECT().ChallengeSetupFinishWithVerifiableCosignerKey(mock.Anything).
			Return(BuildInvalidVerifiableCosignerKeyJSON(), nil)
		computeAction := encrypted_keys.NewComputeAndStoreEncryptedCosignerKeyAction(
			kvStorage,
			keyProvider,
			encrypted_keys.NewFinishCosignerKeyEncryptionAction(),
		)
		action := challenge_setup.NewFinishChallengeSetupAction(houston, kvStorage, computeAction)

		// Test — should NOT return error even though verification fails
		err := action.Run(keys.RecoveryCodeKey.PubKey())
		if err != nil {
			t.Fatalf("Run() error = %v, expected error to be swallowed", err)
		}
	})

	t.Run("propagates houston error", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		houston := houston_mock.NewMockHoustonService(t)
		houston.EXPECT().ChallengeSetupFinishWithVerifiableCosignerKey(mock.Anything).
			Return(model.VerifiableCosignerKeyJSON{}, errors.New("houston network error"))
		computeAction := encrypted_keys.NewComputeAndStoreEncryptedCosignerKeyAction(
			kvStorage,
			keyProvider,
			encrypted_keys.NewFinishCosignerKeyEncryptionAction(),
		)
		action := challenge_setup.NewFinishChallengeSetupAction(houston, kvStorage, computeAction)

		// Test
		err := action.Run(keys.RecoveryCodeKey.PubKey())
		if err == nil {
			t.Fatal("expected Houston error to propagate")
		}
	})
}
