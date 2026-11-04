package test

import (
	"testing"

	"github.com/go-errors/errors"
	"github.com/stretchr/testify/mock"

	adapted_mock "github.com/muun/libwallet/app_provided_data/adapted/mock"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/platform/test/teststorage"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/action/encrypted_keys"
)

func TestComputeAndStoreEncryptedCosignerKeyAction(t *testing.T) {

	t.Run("routes to correct storage slot based on proof", func(t *testing.T) {
		testCases := []struct {
			desc           string
			withProof      bool
			expectedSlot   string
			unexpectedSlot string
		}{
			{
				"verified key when proof is provided",
				true,
				storage.VerifiedEncryptedCosignerKey,
				storage.UnverifiedEncryptedCosignerKey,
			},
			{
				"unverified key when no proof is provided",
				false,
				storage.UnverifiedEncryptedCosignerKey,
				storage.VerifiedEncryptedCosignerKey,
			},
		}
		for _, tc := range testCases {
			t.Run(tc.desc, func(t *testing.T) {
				// Setup
				keys := GenerateTestKeys()
				kvStorage := teststorage.NewTestKeyValueStorage(t)
				keyProvider := adapted_mock.NewMockKeyProvider(t)
				keys.ExpectProvidedBy(keyProvider)
				cosignerKeyFinisher := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
				action := encrypted_keys.NewComputeAndStoreEncryptedCosignerKeyAction(
					kvStorage, keyProvider, cosignerKeyFinisher,
				)
				vmkJSON := BuildVerifiableCosignerKeyJSON(
					keys,
					tc.withProof,
				)

				// Test
				err := action.Run(keys.RecoveryCodeKey.PubKey(), vmkJSON)
				if err != nil {
					t.Fatalf("Run() error = %v", err)
				}

				// Verify expected slot has data
				got, err := kvStorage.Get(tc.expectedSlot)
				if err != nil {
					t.Fatalf("Get(%s) error = %v", tc.expectedSlot, err)
				}
				if got == nil {
					t.Fatalf("expected key to be stored in %s", tc.expectedSlot)
				}

				// Verify unexpected slot is empty
				got, err = kvStorage.Get(tc.unexpectedSlot)
				if err != nil {
					t.Fatalf("Get(%s) error = %v", tc.unexpectedSlot, err)
				}
				if got != nil {
					t.Fatalf("expected %s to be empty", tc.unexpectedSlot)
				}
			})
		}
	})

	t.Run("overwrites existing key on second run", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		keys.ExpectProvidedBy(keyProvider)
		cosignerKeyFinisher := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
		action := encrypted_keys.NewComputeAndStoreEncryptedCosignerKeyAction(
			kvStorage, keyProvider, cosignerKeyFinisher,
		)

		// First run
		vmkJSON1 := BuildVerifiableCosignerKeyJSON(
			keys,
			true,
		)
		err := action.Run(keys.RecoveryCodeKey.PubKey(), vmkJSON1)
		if err != nil {
			t.Fatalf("first Run() error = %v", err)
		}
		first, _ := kvStorage.Get(storage.VerifiedEncryptedCosignerKey)

		// Second run (HPKE uses ephemeral keys, so ciphertext differs)
		vmkJSON2 := BuildVerifiableCosignerKeyJSON(
			keys,
			true,
		)
		err = action.Run(keys.RecoveryCodeKey.PubKey(), vmkJSON2)
		if err != nil {
			t.Fatalf("second Run() error = %v", err)
		}
		second, _ := kvStorage.Get(storage.VerifiedEncryptedCosignerKey)

		// Verify value changed
		if first.(string) == second.(string) {
			t.Fatal("expected second Run to overwrite the first key with a different value")
		}
	})

	t.Run("propagates user key error", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		keyProvider.EXPECT().CosignerPublicKey().
			Return(keys.CosignerKey.ToModel().PublicKey(), nil).Maybe()
		keyProvider.EXPECT().WithUserPrivateKey(mock.Anything).Return(errors.New("keystore locked"))
		cosignerKeyFinisher := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
		action := encrypted_keys.NewComputeAndStoreEncryptedCosignerKeyAction(
			kvStorage, keyProvider, cosignerKeyFinisher,
		)
		vmkJSON := BuildVerifiableCosignerKeyJSON(
			keys,
			true,
		)

		// Test
		err := action.Run(keys.RecoveryCodeKey.PubKey(), vmkJSON)
		if err == nil {
			t.Fatal("expected error when WithUserPrivateKey fails")
		}
	})

	t.Run("propagates cosigner key error", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		keyProvider.EXPECT().CosignerPublicKey().Return(nil, errors.New("keystore locked"))
		cosignerKeyFinisher := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
		action := encrypted_keys.NewComputeAndStoreEncryptedCosignerKeyAction(
			kvStorage, keyProvider, cosignerKeyFinisher,
		)
		vmkJSON := BuildVerifiableCosignerKeyJSON(
			keys,
			true,
		)

		// Test
		err := action.Run(keys.RecoveryCodeKey.PubKey(), vmkJSON)
		if err == nil {
			t.Fatal("expected error when CosignerPublicKey fails")
		}
	})
}
