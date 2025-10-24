package recovery

import (
	"fmt"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/domain/model/verifiable_muun_key"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/storage"
	"log/slog"
)

type PopulateEncryptedMuunKeyAction struct {
	houstonService              service.HoustonService
	keyValueStorage             *storage.KeyValueStorage
	keyProvider                 keys.KeyProvider
	mayRetrieveEncryptedMuunKey *MayRetrieveEncryptedMuunKeyAction
}

func NewPopulateEncryptedMuunKeyAction(
	houstonService service.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	keyProvider keys.KeyProvider,
) *PopulateEncryptedMuunKeyAction {
	return &PopulateEncryptedMuunKeyAction{
		houstonService:              houstonService,
		keyValueStorage:             keyValueStorage,
		keyProvider:                 keyProvider,
		mayRetrieveEncryptedMuunKey: NewMayRetrieveEncryptedMuunKeyAction(keyValueStorage),
	}
}

// Populate the encrypted muun key in storage. If we already have an unverified muun key in
// storage, go to houston and try to get a key that can be verified. This action does not overwrite
// existing keys.
func (a *PopulateEncryptedMuunKeyAction) Run(recoveryCodePublicKey *btcec.PublicKey) error {
	slog.Warn("PopulateEncryptedMuunKeyAction.Run: start")

	userHDPrivateKey, err := a.keyProvider.UserPrivateKey()
	if err != nil {
		return fmt.Errorf("error getting user key from KeyProvider: %w", err)
	}

	userEcPrivateKey, err := userHDPrivateKey.ECPrivateKey()
	if err != nil {
		return fmt.Errorf("error obtaining user ec private key: %w", err)
	}

	muunHDPublicKey, err := a.keyProvider.MuunPublicKey()
	if err != nil {
		return fmt.Errorf("error obtaining muun key from KeyProvider: %w", err)
	}

	currentStatus, err := a.getCurrentStatus()
	if err != nil {
		return err
	}

	if *currentStatus == HasVerifiedEncryptedMuunKey {
		// TODO remove this log once it is not necessary anymore
		slog.Warn("PopulateEncryptedMuunKeyAction.Run: verified key is present, return early")
		return nil
	}

	// we proceed, hoping to obtain a verified key
	verifiableMuunKeyJson, err := a.houstonService.VerifiableMuunKey()
	if err != nil {
		return err
	}

	verifiableMuunKey, err := verifiable_muun_key.VerifiableMuunKeyFromJson(&verifiableMuunKeyJson)
	if err != nil {
		return err
	}

	encryptedMuunKeyWithVerificationFlag, err := verifiableMuunKey.Verify(
		muunHDPublicKey,
		userEcPrivateKey,
		recoveryCodePublicKey,
	)
	if err != nil {
		return err
	}

	if encryptedMuunKeyWithVerificationFlag.Verified {
		slog.Warn("PopulateEncryptedMuunKeyAction.Run: store verified key")
		return a.keyValueStorage.Save(
			storage.VerifiedEncryptedMuunKey,
			encryptedMuunKeyWithVerificationFlag.EncryptedMuunKey)
	}

	if *currentStatus == OnlyHasUnverifiedEncryptedMuunKey {
		// Do not overwrite the existing unverified key
		slog.Warn("PopulateEncryptedMuunKeyAction.Run: unverified key is present, return")
		return nil
	}

	slog.Warn("PopulateEncryptedMuunKeyAction.Run: store unverified key")
	return a.keyValueStorage.Save(
		storage.UnverifiedEncryptedMuunKey,
		encryptedMuunKeyWithVerificationFlag.EncryptedMuunKey,
	)
}

func (a *PopulateEncryptedMuunKeyAction) getCurrentStatus() (*EncryptedMuunKeyStatus, error) {
	encryptedMuunKeyWithStatus, err := a.mayRetrieveEncryptedMuunKey.Run()
	if err != nil {
		return nil, err
	}

	return &encryptedMuunKeyWithStatus.Status, nil
}
