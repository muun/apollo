package presentation

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/domain/action/challenge_keys"
	"github.com/muun/libwallet/domain/action/recovery"
	"github.com/muun/libwallet/domain/model/encrypted_key_v3"
	"github.com/muun/libwallet/presentation/api"
	"github.com/muun/libwallet/recoverycode"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
	"github.com/test-go/testify/assert"
	"io"
	"net/http"
	"testing"
	"time"
)

func TestEncryptedMuunKeyAfterFinishSetupRecoveryCode_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildStorageSchema())

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

	createFirstSessionOkJson := createFirstSession(t, userPrivateKey.PublicKey())
	muunPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	walletServer.keyProvider = NewMockKeyProvider(userPrivateKey, muunPublicKey, 0)
	computeAndStoreEncryptedMuunKeyAction := recovery.NewComputeAndStoreEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.finishChallengeSetup = challenge_keys.NewFinishChallengeSetupAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		computeAndStoreEncryptedMuunKeyAction,
	)

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

	mayRetrieveEncryptedMuunKey := recovery.NewMayRetrieveEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
	)
	encryptedMuunKeyWithStatus, err := mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedMuunKeyWithStatus.Status == recovery.HasNoEncryptedMuunKey {
		t.Fatal("Encrypted muun key should be available")
	}

	decryptAndAssertDecryptedMuunKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedMuunKeyWithStatus.EncryptedMuunKey,
		muunPublicKey,
	)
}

func TestPollForVerifiedEncryptedMuunKey_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildStorageSchema())

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

	createFirstSessionOkJson := createFirstSession(t, userPrivateKey.PublicKey())
	muunPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	walletServer.keyProvider = NewMockKeyProvider(userPrivateKey, muunPublicKey, 0)
	computeAndStoreEncryptedMuunKeyAction := recovery.NewComputeAndStoreEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.finishChallengeSetup = challenge_keys.NewFinishChallengeSetupAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		computeAndStoreEncryptedMuunKeyAction,
	)
	walletServer.populateEncryptedMuunKey = recovery.NewPopulateEncryptedMuunKeyAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)

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
		result, err := walletServer.houstonService.VerifiableMuunKey()
		if err != nil {
			return false, err
		}
		return result.Proof != nil, nil
	})
	if err != nil {
		t.Fatalf("Timed out: %s", err)
	}

	// We now poll with the PopulateEncryptedMuunKey endpoint

	populateRequest := api.PopulateEncryptedMuunKeyRequest_builder{
		RecoveryCodePublicKeyHex: hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	}.Build()

	_, err = walletServer.PopulateEncryptedMuunKey(context.Background(), populateRequest)
	if err != nil {
		t.Fatal(err)
	}

	// Now we should have a verified encrypted muun key
	mayRetrieveEncryptedMuunKey := recovery.NewMayRetrieveEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
	)
	encryptedMuunKeyWithStatus, err := mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedMuunKeyWithStatus.Status != recovery.HasVerifiedEncryptedMuunKey {
		t.Fatal("The user should have a verified muun key at this point.")
	}

	// We poll again
	_, err = walletServer.PopulateEncryptedMuunKey(context.Background(), populateRequest)
	if err != nil {
		t.Fatal(err)
	}

	encryptedMuunKeyWithStatusAgain, err := mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		t.Fatal(err)
	}

	if encryptedMuunKeyWithStatus.Status != recovery.HasVerifiedEncryptedMuunKey {
		t.Fatal("The user should still have a verified muun key at this point.")
	}

	// Polling should not modify the encrypted muun key
	if *encryptedMuunKeyWithStatus.EncryptedMuunKey != *encryptedMuunKeyWithStatusAgain.EncryptedMuunKey {
		t.Fatal("The verified muun key should not change when polling")
	}

	decryptAndAssertDecryptedMuunKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedMuunKeyWithStatus.EncryptedMuunKey,
		muunPublicKey,
	)
}

func TestPollForVerifiedEncryptedMuunKeyWithDelay_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildStorageSchema())

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

	createFirstSessionOkJson := createFirstSession(t, userPrivateKey.PublicKey())
	muunPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}
	walletServer.keyProvider = NewMockKeyProvider(userPrivateKey, muunPublicKey, 0)
	computeAndStoreEncryptedMuunKeyAction := recovery.NewComputeAndStoreEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.finishChallengeSetup = challenge_keys.NewFinishChallengeSetupAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		computeAndStoreEncryptedMuunKeyAction,
	)
	walletServer.populateEncryptedMuunKey = recovery.NewPopulateEncryptedMuunKeyAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)

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

	mayRetrieveEncryptedMuunKey := recovery.NewMayRetrieveEncryptedMuunKeyAction(walletServer.keyValueStorage)
	encryptedMuunKeyWithStatus, err := mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedMuunKeyWithStatus.Status != recovery.OnlyHasUnverifiedEncryptedMuunKey {
		t.Fatal("The user should only have an unverified key at this point.")
	}

	// We now poll with the PopulateEncryptedMuunKey endpoint
	_, err = walletServer.PopulateEncryptedMuunKey(
		context.Background(),
		api.PopulateEncryptedMuunKeyRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	encryptedMuunKeyWithStatusAgain, err := mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		t.Fatal(err)
	}

	if encryptedMuunKeyWithStatusAgain.Status != recovery.OnlyHasUnverifiedEncryptedMuunKey {
		t.Fatal("The user should only have an unverified key at this point.")
	}

	// Polling should not modify the encrypted muun key
	if *encryptedMuunKeyWithStatus.EncryptedMuunKey != *encryptedMuunKeyWithStatusAgain.EncryptedMuunKey {
		t.Fatal("The unverified muun key should not change when polling")
	}

	decryptAndAssertDecryptedMuunKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedMuunKeyWithStatus.EncryptedMuunKey,
		muunPublicKey,
	)
}

func TestVerifiedMuunKeyForExistingUsers_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildStorageSchema())

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

	createFirstSessionOkJson := createFirstSession(t, userPrivateKey.PublicKey())
	muunPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	walletServer.keyProvider = NewMockKeyProvider(userPrivateKey, muunPublicKey, 0)
	computeAndStoreEncryptedMuunKeyAction := recovery.NewComputeAndStoreEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.finishChallengeSetup = challenge_keys.NewFinishChallengeSetupAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		computeAndStoreEncryptedMuunKeyAction,
	)
	walletServer.populateEncryptedMuunKey = recovery.NewPopulateEncryptedMuunKeyAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)

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
	err = walletServer.houstonService.ChallengeKeySetupFinish(model.ChallengeSetupVerifyJson{
		ChallengeType: "RECOVERY_CODE",
		PublicKey:     hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	})
	if err != nil {
		t.Fatal(err)
	}

	// The user should not have an encrypted muun key at this point
	mayRetrieveEncryptedMuunKey := recovery.NewMayRetrieveEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
	)
	encryptedMuunKeyWithStatus, err := mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedMuunKeyWithStatus.Status != recovery.HasNoEncryptedMuunKey {
		t.Fatal("The user should not have an encrypted muun key at this point.")
	}

	err = WaitForCondition(1*time.Second, func() (bool, error) {
		result, err := walletServer.houstonService.VerifiableMuunKey()
		if err != nil {
			return false, err
		}
		return result.Proof != nil, nil
	})
	if err != nil {
		t.Fatalf("Timed out: %s", err)
	}

	// We poll with the PopulateEncryptedMuunKey endpoint simulating a migration
	_, err = walletServer.PopulateEncryptedMuunKey(
		context.Background(),
		api.PopulateEncryptedMuunKeyRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	encryptedMuunKeyWithStatus, err = mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedMuunKeyWithStatus.Status != recovery.HasVerifiedEncryptedMuunKey {
		t.Fatal("The user should have a verified muun key at this point.")
	}

	decryptAndAssertDecryptedMuunKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedMuunKeyWithStatus.EncryptedMuunKey,
		muunPublicKey,
	)
}

func TestUnverifiedEncryptedMuunKeyForExistingUsers_Integration(t *testing.T) {

	setupKeyValueStorage(t, storage.BuildStorageSchema())

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

	createFirstSessionOkJson := createFirstSession(t, userPrivateKey.PublicKey())
	muunPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest(),
	)
	if err != nil {
		t.Fatal(err)
	}

	walletServer.keyProvider = NewMockKeyProvider(userPrivateKey, muunPublicKey, 0)
	computeAndStoreEncryptedMuunKeyAction := recovery.NewComputeAndStoreEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.finishChallengeSetup = challenge_keys.NewFinishChallengeSetupAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		computeAndStoreEncryptedMuunKeyAction,
	)
	walletServer.populateEncryptedMuunKey = recovery.NewPopulateEncryptedMuunKeyAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)

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
	err = walletServer.houstonService.ChallengeKeySetupFinish(model.ChallengeSetupVerifyJson{
		ChallengeType: "RECOVERY_CODE",
		PublicKey:     hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	})
	if err != nil {
		t.Fatal(err)
	}

	// The user should not have an encrypted muun key at this point
	mayRetrieveEncryptedMuunKey := recovery.NewMayRetrieveEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
	)
	encryptedMuunKeyWithStatus, err := mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedMuunKeyWithStatus.Status != recovery.HasNoEncryptedMuunKey {
		t.Fatal("The user should not have an encrypted muun key at this point.")
	}

	// We poll with the PopulateEncryptedMuunKey endpoint simulating a migration
	_, err = walletServer.PopulateEncryptedMuunKey(
		context.Background(),
		api.PopulateEncryptedMuunKeyRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	// Now the user should have an unverified encrypted muun key
	encryptedMuunKeyWithStatus, err = mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		t.Fatal(err)
	}
	if encryptedMuunKeyWithStatus.Status != recovery.OnlyHasUnverifiedEncryptedMuunKey {
		t.Fatal("The user should only have an unverified key at this point.")
	}

	decryptAndAssertDecryptedMuunKeyIsCorrect(
		t,
		recoveryCodePrivateKey,
		*encryptedMuunKeyWithStatus.EncryptedMuunKey,
		muunPublicKey,
	)
}

func decryptAndAssertDecryptedMuunKeyIsCorrect(
	t *testing.T,
	recoveryCodePrivateKey *btcec.PrivateKey,
	encryptedMuunKey string,
	expectedMuunPublicKey *libwallet.HDPublicKey,
) {

	decryptedMuunPrivateKey, err := encrypted_key_v3.DecryptExtendedKey(
		recoveryCodePrivateKey,
		encryptedMuunKey,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	// Comparing muunPublicKey.String() to decryptedMuunKey.PublicKey().String() won't work because
	// we set the parentFingerprint to zero when reconstructing the key.
	//
	// We can instead compare the public keys and the chaincodes and also compare the keys after
	// deriving at some path.

	if !bytes.Equal(expectedMuunPublicKey.Raw(), decryptedMuunPrivateKey.PublicKey().Raw()) {
		t.Fatal("decrypted public key does not match original public key")
	}

	if !bytes.Equal(expectedMuunPublicKey.ChainCode(), decryptedMuunPrivateKey.ChainCode()) {
		t.Fatal("decrypted chain code does not match original chain code")
	}

	somePath := "m/schema:1'/recovery:1'/a:2/b:3/c:5"
	derivedPrivateKey, err := decryptedMuunPrivateKey.DeriveTo(somePath)
	if err != nil {
		t.Fatal(err)
	}
	derivedPublicKey, err := expectedMuunPublicKey.DeriveTo(somePath)
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
		return fmt.Errorf("timed out waiting for condition, failed with error %w", err)
	} else {
		return fmt.Errorf("timed out waiting for condition")
	}
}

// 127.0.0.1 instead of localhost to avoid problems with network interfaces in local env
const proverUrl = "http://127.0.0.1:8130"

func delayProverJob(t *testing.T, recoveryCodePublicKey *btcec.PublicKey) {
	// This requests prevents the proof from completing, this exercising the unhappy path
	requestUrl := proverUrl + "/testing/delay-job?pattern=" + hex.EncodeToString(
		recoveryCodePublicKey.SerializeCompressed(),
	)
	req, err := http.NewRequest("POST", requestUrl, nil)
	if err != nil {
		t.Fatal(err)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
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
