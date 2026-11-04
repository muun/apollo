package server

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"io"
	"net/http"
	"testing"
	"time"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/go-errors/errors"
	"github.com/test-go/testify/assert"

	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/storage"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/legacy/recoverycode"
	"github.com/muun/libwallet/server/api"
	"github.com/muun/libwallet/subsystems/recovery"
	recovery_model "github.com/muun/libwallet/subsystems/recovery/presentation/model"
)

func TestEncryptedCosignerKeyAfterFinishSetupRecoveryCode_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildKVMigrationPlan())

	recoveryCode := recoverycode.Generate()
	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}

	userPrivateKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	encryptedPrivateKey, err := libwallet.KeyEncrypt(userPrivateKey, recoveryCode)
	if err != nil {
		t.Fatal(err)
	}

	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	createFirstSessionOkJson := createFirstSession( //nolint:staticcheck // TODO: var createFirstSessionOkJson should be createFirstSessionOkJSON
		t,
		userPrivateKey.PublicKey(),
	)
	cosignerPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	walletServer.keyProvider = newMockKeyProvider(t, userPrivateKey, cosignerPublicKey)
	recoverySubsystem := recovery.NewRecovery(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.recovery = &recoverySubsystem

	_, err = walletServer.StartChallengeSetup(
		context.Background(),
		api.ChallengeSetupRequest_builder{
			Type:                "RECOVERY_CODE",
			PublicKey:           hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
			Salt:                "dfb80ea8c30959e8",
			EncryptedPrivateKey: encryptedPrivateKey,
			Version:             2,
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	_, err = walletServer.FinishRecoveryCodeSetup(
		context.Background(),
		api.FinishRecoveryCodeSetupRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	encryptedCosignerKeyWithStatus, err := recoverySubsystem.MayRetrieveEncryptedCosignerKey()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedCosignerKeyWithStatus.Status == recovery_model.HasNoEncryptedCosignerKey {
		t.Fatal("Encrypted cosigner key should be available")
	}

	decryptAndAssertDecryptedCosignerKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedCosignerKeyWithStatus.EncryptedCosignerKey,
		cosignerPublicKey,
	)
}

func TestPollForVerifiedEncryptedCosignerKey_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildKVMigrationPlan())

	recoveryCode := recoverycode.Generate()
	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}

	userPrivateKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	encryptedPrivateKey, err := libwallet.KeyEncrypt(userPrivateKey, recoveryCode)
	if err != nil {
		t.Fatal(err)
	}

	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	createFirstSessionOkJson := createFirstSession( //nolint:staticcheck // TODO: var createFirstSessionOkJson should be createFirstSessionOkJSON
		t,
		userPrivateKey.PublicKey(),
	)
	cosignerPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	walletServer.keyProvider = newMockKeyProvider(t, userPrivateKey, cosignerPublicKey)
	recoverySubsystem := recovery.NewRecovery(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.recovery = &recoverySubsystem

	_, err = walletServer.StartChallengeSetup(
		context.Background(),
		api.ChallengeSetupRequest_builder{
			Type:                "RECOVERY_CODE",
			PublicKey:           hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
			Salt:                "dfb80ea8c30959e8",
			EncryptedPrivateKey: encryptedPrivateKey,
			Version:             2,
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	_, err = walletServer.FinishRecoveryCodeSetup(
		context.Background(),
		api.FinishRecoveryCodeSetupRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	err = WaitForCondition(1*time.Second, func() (bool, error) {
		result, err := walletServer.houstonService.VerifiableCosignerKey()
		if err != nil {
			return false, err
		}
		return result.Proof != nil, nil
	})
	if err != nil {
		t.Fatalf("Timed out: %s", err)
	}

	// We now poll with the PopulateEncryptedCosignerKey endpoint

	populateRequest := api.PopulateEncryptedCosignerKeyRequest_builder{
		RecoveryCodePublicKeyHex: hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	}.Build()

	_, err = walletServer.PopulateEncryptedCosignerKey(context.Background(), populateRequest)
	if err != nil {
		t.Fatal(err)
	}

	// Now we should have a verified encrypted cosigner key
	encryptedCosignerKeyWithStatus, err := recoverySubsystem.MayRetrieveEncryptedCosignerKey()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedCosignerKeyWithStatus.Status != recovery_model.HasVerifiedEncryptedCosignerKey {
		t.Fatal("The user should have a verified cosigner key at this point.")
	}

	// We poll again
	_, err = walletServer.PopulateEncryptedCosignerKey(context.Background(), populateRequest)
	if err != nil {
		t.Fatal(err)
	}

	encryptedCosignerKeyWithStatusAgain, err :=
		recoverySubsystem.MayRetrieveEncryptedCosignerKey()
	if err != nil {
		t.Fatal(err)
	}

	if encryptedCosignerKeyWithStatus.Status != recovery_model.HasVerifiedEncryptedCosignerKey {
		t.Fatal("The user should still have a verified cosigner key at this point.")
	}

	// Polling should not modify the encrypted cosigner key
	if *encryptedCosignerKeyWithStatus.EncryptedCosignerKey !=
		*encryptedCosignerKeyWithStatusAgain.EncryptedCosignerKey {
		t.Fatal("The verified cosigner key should not change when polling")
	}

	decryptAndAssertDecryptedCosignerKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedCosignerKeyWithStatus.EncryptedCosignerKey,
		cosignerPublicKey,
	)
}

func TestPollForVerifiedEncryptedCosignerKeyWithDelay_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildKVMigrationPlan())

	recoveryCode := recoverycode.Generate()
	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}

	userPrivateKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	encryptedPrivateKey, err := libwallet.KeyEncrypt(userPrivateKey, recoveryCode)
	if err != nil {
		t.Fatal(err)
	}

	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	createFirstSessionOkJson := createFirstSession( //nolint:staticcheck // TODO: var createFirstSessionOkJson should be createFirstSessionOkJSON
		t,
		userPrivateKey.PublicKey(),
	)
	cosignerPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}
	walletServer.keyProvider = newMockKeyProvider(t, userPrivateKey, cosignerPublicKey)
	recoverySubsystem := recovery.NewRecovery(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.recovery = &recoverySubsystem

	delayProverJob(t, recoveryCodePublicKey)

	_, err = walletServer.StartChallengeSetup(
		context.Background(),
		api.ChallengeSetupRequest_builder{
			Type:                "RECOVERY_CODE",
			PublicKey:           hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
			Salt:                "dfb80ea8c30959e8",
			EncryptedPrivateKey: encryptedPrivateKey,
			Version:             2,
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	_, err = walletServer.FinishRecoveryCodeSetup(
		context.Background(),
		api.FinishRecoveryCodeSetupRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	encryptedCosignerKeyWithStatus, err := recoverySubsystem.MayRetrieveEncryptedCosignerKey()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedCosignerKeyWithStatus.Status !=
		recovery_model.OnlyHasUnverifiedEncryptedCosignerKey {
		t.Fatal("The user should only have an unverified key at this point.")
	}

	// We now poll with the PopulateEncryptedCosignerKey endpoint
	_, err = walletServer.PopulateEncryptedCosignerKey(
		context.Background(),
		api.PopulateEncryptedCosignerKeyRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	encryptedCosignerKeyWithStatusAgain, err :=
		recoverySubsystem.MayRetrieveEncryptedCosignerKey()
	if err != nil {
		t.Fatal(err)
	}

	if encryptedCosignerKeyWithStatusAgain.Status !=
		recovery_model.OnlyHasUnverifiedEncryptedCosignerKey {
		t.Fatal("The user should only have an unverified key at this point.")
	}

	// Polling should not modify the encrypted cosigner key
	if *encryptedCosignerKeyWithStatus.EncryptedCosignerKey !=
		*encryptedCosignerKeyWithStatusAgain.EncryptedCosignerKey {
		t.Fatal("The unverified cosigner key should not change when polling")
	}

	decryptAndAssertDecryptedCosignerKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedCosignerKeyWithStatus.EncryptedCosignerKey,
		cosignerPublicKey,
	)
}

func TestVerifiedCosignerKeyForExistingUsers_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildKVMigrationPlan())

	recoveryCode := recoverycode.Generate()
	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}

	userPrivateKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	encryptedPrivateKey, err := libwallet.KeyEncrypt(userPrivateKey, recoveryCode)
	if err != nil {
		t.Fatal(err)
	}

	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	createFirstSessionOkJson := createFirstSession( //nolint:staticcheck // TODO: var createFirstSessionOkJson should be createFirstSessionOkJSON
		t,
		userPrivateKey.PublicKey(),
	)
	cosignerPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	walletServer.keyProvider = newMockKeyProvider(t, userPrivateKey, cosignerPublicKey)
	recoverySubsystem := recovery.NewRecovery(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.recovery = &recoverySubsystem

	_, err = walletServer.StartChallengeSetup(
		context.Background(),
		api.ChallengeSetupRequest_builder{
			Type:                "RECOVERY_CODE",
			PublicKey:           hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
			Salt:                "dfb80ea8c30959e8",
			EncryptedPrivateKey: encryptedPrivateKey,
			Version:             2,
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	// Use the legacy finish endpoint
	err = walletServer.houstonService.ChallengeKeySetupFinish(model.ChallengeSetupVerifyJSON{
		ChallengeType: "RECOVERY_CODE",
		PublicKey:     hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	})
	if err != nil {
		t.Fatal(err)
	}

	// The user should not have an encrypted cosigner key at this point
	encryptedCosignerKeyWithStatus, err := recoverySubsystem.MayRetrieveEncryptedCosignerKey()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedCosignerKeyWithStatus.Status != recovery_model.HasNoEncryptedCosignerKey {
		t.Fatal("The user should not have an encrypted cosigner key at this point.")
	}

	err = WaitForCondition(1*time.Second, func() (bool, error) {
		result, err := walletServer.houstonService.VerifiableCosignerKey()
		if err != nil {
			return false, err
		}
		return result.Proof != nil, nil
	})
	if err != nil {
		t.Fatalf("Timed out: %s", err)
	}

	// We poll with the PopulateEncryptedCosignerKey endpoint simulating a migration
	_, err = walletServer.PopulateEncryptedCosignerKey(
		context.Background(),
		api.PopulateEncryptedCosignerKeyRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	encryptedCosignerKeyWithStatus, err = recoverySubsystem.MayRetrieveEncryptedCosignerKey()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedCosignerKeyWithStatus.Status != recovery_model.HasVerifiedEncryptedCosignerKey {
		t.Fatal("The user should have a verified cosigner key at this point.")
	}

	decryptAndAssertDecryptedCosignerKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedCosignerKeyWithStatus.EncryptedCosignerKey,
		cosignerPublicKey,
	)
}

func TestUnverifiedEncryptedCosignerKeyForExistingUsers_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildKVMigrationPlan())

	recoveryCode := recoverycode.Generate()
	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}

	userPrivateKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	encryptedPrivateKey, err := libwallet.KeyEncrypt(userPrivateKey, recoveryCode)
	if err != nil {
		t.Fatal(err)
	}

	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	createFirstSessionOkJson := createFirstSession( //nolint:staticcheck // TODO: var createFirstSessionOkJson should be createFirstSessionOkJSON
		t,
		userPrivateKey.PublicKey(),
	)
	cosignerPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest(),
	)
	if err != nil {
		t.Fatal(err)
	}

	walletServer.keyProvider = newMockKeyProvider(t, userPrivateKey, cosignerPublicKey)
	recoverySubsystem := recovery.NewRecovery(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.recovery = &recoverySubsystem

	delayProverJob(t, recoveryCodePublicKey)

	_, err = walletServer.StartChallengeSetup(
		context.Background(),
		api.ChallengeSetupRequest_builder{
			Type:                "RECOVERY_CODE",
			PublicKey:           hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
			Salt:                "dfb80ea8c30959e8",
			EncryptedPrivateKey: encryptedPrivateKey,
			Version:             2,
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	// Use the legacy finish endpoint
	err = walletServer.houstonService.ChallengeKeySetupFinish(model.ChallengeSetupVerifyJSON{
		ChallengeType: "RECOVERY_CODE",
		PublicKey:     hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	})
	if err != nil {
		t.Fatal(err)
	}

	// The user should not have an encrypted cosigner key at this point
	encryptedCosignerKeyWithStatus, err := recoverySubsystem.MayRetrieveEncryptedCosignerKey()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedCosignerKeyWithStatus.Status != recovery_model.HasNoEncryptedCosignerKey {
		t.Fatal("The user should not have an encrypted cosigner key at this point.")
	}

	// We poll with the PopulateEncryptedCosignerKey endpoint simulating a migration
	_, err = walletServer.PopulateEncryptedCosignerKey(
		context.Background(),
		api.PopulateEncryptedCosignerKeyRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	// Now the user should have an unverified encrypted cosigner key
	encryptedCosignerKeyWithStatus, err = recoverySubsystem.MayRetrieveEncryptedCosignerKey()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedCosignerKeyWithStatus.Status !=
		recovery_model.OnlyHasUnverifiedEncryptedCosignerKey {
		t.Fatal("The user should only have an unverified key at this point.")
	}

	decryptAndAssertDecryptedCosignerKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedCosignerKeyWithStatus.EncryptedCosignerKey,
		cosignerPublicKey,
	)
}

func decryptAndAssertDecryptedCosignerKeyIsCorrect(
	t *testing.T,
	recoveryCodePrivateKey *btcec.PrivateKey,
	encryptedCosignerKey string,
	expectedCosignerPublicKey *libwallet.HDPublicKey,
) {

	decryptedCosignerPrivateKey, err := walletServer.recovery.DecryptExtendedKey(
		recoveryCodePrivateKey,
		encryptedCosignerKey,
		libwallet.Regtest().ToParams())
	if err != nil {
		t.Fatal(err)
	}

	// Comparing cosignerPublicKey.String() to decryptedCosignerKey.PublicKey().String() won't work because
	// we set the parentFingerprint to zero when reconstructing the key.
	//
	// We can instead compare the public keys and the chaincodes and also compare the keys after
	// deriving at some path.

	if !bytes.Equal(
		expectedCosignerPublicKey.Raw(),
		decryptedCosignerPrivateKey.PublicKey().Raw(),
	) {
		t.Fatal("decrypted public key does not match original public key")
	}

	if !bytes.Equal(
		expectedCosignerPublicKey.ChainCode(),
		decryptedCosignerPrivateKey.ChainCode(),
	) {
		t.Fatal("decrypted chain code does not match original chain code")
	}

	somePath := "m/schema:1'/recovery:1'/a:2/b:3/c:5"
	derivedPrivateKey, err := decryptedCosignerPrivateKey.DeriveTo(somePath)
	if err != nil {
		t.Fatal(err)
	}
	derivedPublicKey, err := expectedCosignerPublicKey.DeriveTo(somePath)
	if err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, derivedPublicKey.String(), derivedPrivateKey.PublicKey().String())
}

func WaitForCondition(timeout time.Duration, condition func() (bool, error)) error {
	end := time.Now().Add(timeout)

	var err error

	for time.Now().Before(end) {
		holds, innerErr := condition()

		if holds {
			return nil
		}

		if innerErr != nil {
			err = innerErr
		}

		time.Sleep(100 * time.Millisecond)
	}

	if err != nil {
		return errors.Errorf("timed out waiting for condition, failed with error %w", err)
	} else {
		return errors.Errorf("timed out waiting for condition")
	}
}

// 127.0.0.1 instead of localhost to avoid problems with network interfaces in local env
const proverUrl = "http://127.0.0.1:8130" //nolint:staticcheck // TODO: const proverUrl should be proverURL

func delayProverJob(t *testing.T, recoveryCodePublicKey *btcec.PublicKey) {
	// This requests prevents the proof from completing, this exercising the unhappy path
	requestUrl := proverUrl + "/testing/delay-job?pattern=" + hex.EncodeToString( //nolint:staticcheck // TODO: var requestUrl should be requestURL
		recoveryCodePublicKey.SerializeCompressed(),
	)
	req, err := http.NewRequest( //nolint:noctx // TODO: use http.NewRequestWithContext
		"POST",
		requestUrl,
		nil,
	)
	if err != nil {
		t.Fatal(err)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 || resp.StatusCode < 200 {
		r, _ := io.ReadAll(resp.Body)
		t.Fatalf("request failed with status code %d, resp: %s", resp.StatusCode, string(r))
	}

}

func randomBytes(count int) []byte {
	buf := make([]byte, count)
	_, err := rand.Read(buf)
	if err != nil {
		panic("couldn't read random bytes")
	}

	return buf
}
