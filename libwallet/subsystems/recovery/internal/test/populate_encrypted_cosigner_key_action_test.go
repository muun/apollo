package test

import (
	"testing"

	"github.com/go-errors/errors"

	adapted_mock "github.com/muun/libwallet/app_provided_data/adapted/mock"
	houston_mock "github.com/muun/libwallet/base/data/houston/mock"
	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/platform/test/teststorage"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/action/encrypted_keys"
)

func TestPopulateEncryptedCosignerKeyAction(t *testing.T) {

	t.Run("stores key in correct slot when storage is empty", func(t *testing.T) {
		testCases := []struct {
			desc         string
			withProof    bool
			expectedSlot string
		}{
			{"verified key when proof is provided", true, storage.VerifiedEncryptedCosignerKey},
			{
				"unverified key when no proof is provided",
				false,
				storage.UnverifiedEncryptedCosignerKey,
			},
		}
		for _, tc := range testCases {
			t.Run(tc.desc, func(t *testing.T) {
				// Setup
				keys := GenerateTestKeys()
				kvStorage := teststorage.NewTestKeyValueStorage(t)
				keyProvider := adapted_mock.NewMockKeyProvider(t)
				keys.ExpectProvidedBy(keyProvider)
				vmkJSON := BuildVerifiableCosignerKeyJSON(
					keys,
					tc.withProof,
				)
				houston := houston_mock.NewMockHoustonService(t)
				houston.EXPECT().VerifiableCosignerKey().Return(*vmkJSON, nil).Maybe()
				cosignerKeyFinisher := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
				action := encrypted_keys.NewPopulateEncryptedCosignerKeyAction(
					houston, kvStorage, keyProvider, cosignerKeyFinisher,
				)

				// Test
				err := action.Run(keys.RecoveryCodeKey.PubKey())
				if err != nil {
					t.Fatalf("Run() error = %v", err)
				}

				// Verify
				got, err := kvStorage.Get(tc.expectedSlot)
				if err != nil {
					t.Fatalf("Get(%s) error = %v", tc.expectedSlot, err)
				}
				if got == nil {
					t.Fatalf("expected key to be stored in %s", tc.expectedSlot)
				}
			})
		}
	})

	t.Run("returns early when verified key already exists", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		keys.ExpectProvidedBy(keyProvider)
		err := kvStorage.Save(storage.VerifiedEncryptedCosignerKey, "existing-verified-key")
		if err != nil {
			t.Fatalf("Save() error = %v", err)
		}

		// Houston should NOT be called: the mock fails the test on any call

		houston := houston_mock.NewMockHoustonService(t)
		cosignerKeyFinisher := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
		action := encrypted_keys.NewPopulateEncryptedCosignerKeyAction(
			houston, kvStorage, keyProvider, cosignerKeyFinisher,
		)

		// Test
		err = action.Run(keys.RecoveryCodeKey.PubKey())
		if err != nil {
			t.Fatalf("Run() error = %v, expected early return", err)
		}

		// Verify key is unchanged
		got, _ := kvStorage.Get(storage.VerifiedEncryptedCosignerKey)
		if got.(string) != "existing-verified-key" {
			t.Fatal("verified key should not have been modified")
		}
	})

	t.Run("does not overwrite existing unverified key with another unverified", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		keys.ExpectProvidedBy(keyProvider)
		err := kvStorage.Save(storage.UnverifiedEncryptedCosignerKey, "existing-unverified-key")
		if err != nil {
			t.Fatalf("Save() error = %v", err)
		}
		vmkJSON := BuildVerifiableCosignerKeyJSON(
			keys,
			false,
		)
		houston := houston_mock.NewMockHoustonService(t)
		houston.EXPECT().VerifiableCosignerKey().Return(*vmkJSON, nil).Maybe()
		cosignerKeyFinisher := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
		action := encrypted_keys.NewPopulateEncryptedCosignerKeyAction(
			houston, kvStorage, keyProvider, cosignerKeyFinisher,
		)

		// Test
		err = action.Run(keys.RecoveryCodeKey.PubKey())
		if err != nil {
			t.Fatalf("Run() error = %v", err)
		}

		// Verify key is unchanged
		got, _ := kvStorage.Get(storage.UnverifiedEncryptedCosignerKey)
		if got.(string) != "existing-unverified-key" {
			t.Fatal("unverified key should not have been overwritten")
		}
	})

	t.Run("upgrades unverified to verified when proof becomes available", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		keys.ExpectProvidedBy(keyProvider)
		err := kvStorage.Save(storage.UnverifiedEncryptedCosignerKey, "existing-unverified-key")
		if err != nil {
			t.Fatalf("Save() error = %v", err)
		}
		vmkJSON := BuildVerifiableCosignerKeyJSON(
			keys,
			true,
		)
		houston := houston_mock.NewMockHoustonService(t)
		houston.EXPECT().VerifiableCosignerKey().Return(*vmkJSON, nil).Maybe()
		cosignerKeyFinisher := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
		action := encrypted_keys.NewPopulateEncryptedCosignerKeyAction(
			houston, kvStorage, keyProvider, cosignerKeyFinisher,
		)

		// Test
		err = action.Run(keys.RecoveryCodeKey.PubKey())
		if err != nil {
			t.Fatalf("Run() error = %v", err)
		}

		// Verify verified key was stored
		got, _ := kvStorage.Get(storage.VerifiedEncryptedCosignerKey)
		if got == nil {
			t.Fatal("expected verified key to be stored after upgrade")
		}

		// Verify unverified key was NOT deleted (production code writes verified but keeps
		// unverified)
		unverified, _ := kvStorage.Get(storage.UnverifiedEncryptedCosignerKey)
		if unverified == nil || unverified.(string) != "existing-unverified-key" {
			t.Fatal("unverified key should remain in storage after upgrade")
		}
	})

	t.Run("propagates houston error", func(t *testing.T) {
		// Setup
		keys := GenerateTestKeys()
		kvStorage := teststorage.NewTestKeyValueStorage(t)
		keyProvider := adapted_mock.NewMockKeyProvider(t)
		keys.ExpectProvidedBy(keyProvider)
		houston := houston_mock.NewMockHoustonService(t)
		houston.EXPECT().VerifiableCosignerKey().
			Return(model.VerifiableCosignerKeyJSON{}, errors.New("houston error"))
		cosignerKeyFinisher := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
		action := encrypted_keys.NewPopulateEncryptedCosignerKeyAction(
			houston, kvStorage, keyProvider, cosignerKeyFinisher,
		)

		// Test
		err := action.Run(keys.RecoveryCodeKey.PubKey())
		if err == nil {
			t.Fatal("expected error from Houston to propagate")
		}
	})
}
