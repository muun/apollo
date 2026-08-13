package challenge_keys

import (
	"encoding/hex"
	"testing"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/domain/action/recovery"
	"github.com/muun/libwallet/internal/testutils"
	"github.com/muun/libwallet/storage"
)

func TestFinishChallengeSetupAction(t *testing.T) {

	t.Run("calls houston and stores encrypted muun key", func(t *testing.T) {
		// Setup
		keys := testutils.GenerateTestKeys()
		kvStorage := testutils.NewTestKeyValueStorage(t)
		keyProvider := testutils.NewMockKeyProvider(keys)
		vmkJSON := testutils.BuildVerifiableMuunKeyJson(
			keys,
			true,
		)
		houston := &testutils.MockHoustonService{
			FinishWithVerifiableResult: *vmkJSON,
		}
		computeAction := recovery.NewComputeAndStoreEncryptedMuunKeyAction(kvStorage, keyProvider)
		action := NewFinishChallengeSetupAction(houston, kvStorage, computeAction)

		// Test
		err := action.Run(keys.RecoveryCodeKey.PubKey())
		if err != nil {
			t.Fatalf("Run() error = %v", err)
		}

		// Verify houston was called with correct challenge type and public key
		if houston.CapturedChallengeSetupVerify == nil {
			t.Fatal("expected Houston to be called")
		}

		wantType := "RECOVERY_CODE"
		if houston.CapturedChallengeSetupVerify.ChallengeType != wantType {
			t.Fatalf("ChallengeType = %s, want %s",
				houston.CapturedChallengeSetupVerify.ChallengeType, wantType)
		}

		wantPubKey := hex.EncodeToString(keys.RecoveryCodeKey.PubKey().SerializeCompressed())
		if houston.CapturedChallengeSetupVerify.PublicKey != wantPubKey {
			t.Fatalf("PublicKey = %s, want %s",
				houston.CapturedChallengeSetupVerify.PublicKey, wantPubKey)
		}

		// Verify encrypted muun key was stored
		got, err := kvStorage.Get(storage.VerifiedEncryptedMuunKey)
		if err != nil {
			t.Fatalf("Get() error = %v", err)
		}
		if got == nil {
			t.Fatal("expected verified encrypted muun key to be stored")
		}
	})

	t.Run("swallows verification error", func(t *testing.T) {
		// Setup
		keys := testutils.GenerateTestKeys()
		kvStorage := testutils.NewTestKeyValueStorage(t)
		keyProvider := testutils.NewMockKeyProvider(keys)
		houston := &testutils.MockHoustonService{
			FinishWithVerifiableResult: testutils.BuildInvalidVerifiableMuunKeyJson(),
		}
		computeAction := recovery.NewComputeAndStoreEncryptedMuunKeyAction(kvStorage, keyProvider)
		action := NewFinishChallengeSetupAction(houston, kvStorage, computeAction)

		// Test — should NOT return error even though verification fails
		err := action.Run(keys.RecoveryCodeKey.PubKey())
		if err != nil {
			t.Fatalf("Run() error = %v, expected error to be swallowed", err)
		}
	})

	t.Run("propagates houston error", func(t *testing.T) {
		// Setup
		keys := testutils.GenerateTestKeys()
		kvStorage := testutils.NewTestKeyValueStorage(t)
		keyProvider := testutils.NewMockKeyProvider(keys)
		houston := &testutils.MockHoustonService{
			FinishWithVerifiableErr: errors.New("houston network error"),
		}
		computeAction := recovery.NewComputeAndStoreEncryptedMuunKeyAction(kvStorage, keyProvider)
		action := NewFinishChallengeSetupAction(houston, kvStorage, computeAction)

		// Test
		err := action.Run(keys.RecoveryCodeKey.PubKey())
		if err == nil {
			t.Fatal("expected Houston error to propagate")
		}
	})
}
