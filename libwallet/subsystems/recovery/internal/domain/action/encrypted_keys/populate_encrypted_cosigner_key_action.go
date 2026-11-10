package encrypted_keys

import (
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/model/verifiable_cosigner_key"
	recovery_model "github.com/muun/libwallet/subsystems/recovery/presentation/model"
)

type PopulateEncryptedCosignerKeyAction interface {
	Run(recoveryCodePublicKey *btcec.PublicKey) error
}

type populateEncryptedCosignerKeyAction struct {
	houstonService                  houston.HoustonService
	keyValueStorage                 *storage.KeyValueStorage
	keyProvider                     adapted.KeyProvider
	cosignerKeyFinisher             FinishCosignerKeyEncryptionAction
	mayRetrieveEncryptedCosignerKey MayRetrieveEncryptedCosignerKeyAction
}

func NewPopulateEncryptedCosignerKeyAction(
	houstonService houston.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	keyProvider adapted.KeyProvider,
	cosignerKeyFinisher FinishCosignerKeyEncryptionAction,
) PopulateEncryptedCosignerKeyAction {
	return &populateEncryptedCosignerKeyAction{
		houstonService:                  houstonService,
		keyValueStorage:                 keyValueStorage,
		keyProvider:                     keyProvider,
		cosignerKeyFinisher:             cosignerKeyFinisher,
		mayRetrieveEncryptedCosignerKey: NewMayRetrieveEncryptedCosignerKeyAction(keyValueStorage),
	}
}

// Populate the encrypted cosigner key in storage. If we already have an unverified cosigner key in
// storage, go to houston and try to get a key that can be verified. This action does not overwrite
// existing keys.
func (a *populateEncryptedCosignerKeyAction) Run(recoveryCodePublicKey *btcec.PublicKey) error {
	slog.Warn("PopulateEncryptedCosignerKeyAction.Run: start")

	cosignerHDPublicKey, err := a.keyProvider.CosignerPublicKey()
	if err != nil {
		return errors.Errorf("error obtaining cosigner key from KeyProvider: %w", err)
	}

	currentStatus, err := a.getCurrentStatus()
	if err != nil {
		return err
	}

	if *currentStatus == recovery_model.HasVerifiedEncryptedCosignerKey {
		// TODO remove this log once it is not necessary anymore
		slog.Warn("PopulateEncryptedCosignerKeyAction.Run: verified key is present, return early")
		return nil
	}

	// we proceed, hoping to obtain a verified key
	verifiableCosignerKeyJSON, err := a.houstonService.VerifiableCosignerKey()
	if err != nil {
		return err
	}

	verifiableCosignerKey, err := verifiable_cosigner_key.NewVerifiableCosignerKeyFromJSON(
		&verifiableCosignerKeyJSON,
	)
	if err != nil {
		return err
	}

	var verification *verifiable_cosigner_key.EncryptedCosignerKeyWithVerificationFlag
	err = a.keyProvider.WithUserPrivateKey(func(userHDPrivateKey *hd_keys.HDPrivateKey) error {
		userEcPrivateKey, err := userHDPrivateKey.ECPrivKey()
		if err != nil {
			return errors.Errorf("error obtaining user ec private key: %w", err)
		}
		defer userEcPrivateKey.Zero()

		verification, err = verifiableCosignerKey.Verify(
			cosignerHDPublicKey,
			userEcPrivateKey,
			recoveryCodePublicKey,
			a.cosignerKeyFinisher,
		)
		return err
	})
	if err != nil {
		return err
	}

	if verification.Verified {
		slog.Warn("PopulateEncryptedCosignerKeyAction.Run: store verified key")
		return a.keyValueStorage.Save(
			storage.VerifiedEncryptedCosignerKey,
			verification.EncryptedCosignerKey)
	}

	if *currentStatus == recovery_model.OnlyHasUnverifiedEncryptedCosignerKey {
		// Do not overwrite the existing unverified key
		slog.Warn("PopulateEncryptedCosignerKeyAction.Run: unverified key is present, return")
		return nil
	}

	slog.Warn("PopulateEncryptedCosignerKeyAction.Run: store unverified key")
	return a.keyValueStorage.Save(
		storage.UnverifiedEncryptedCosignerKey,
		verification.EncryptedCosignerKey,
	)
}

func (a *populateEncryptedCosignerKeyAction) getCurrentStatus() (
	*recovery_model.EncryptedCosignerKeyStatus, error,
) {
	encryptedCosignerKeyWithStatus, err := a.mayRetrieveEncryptedCosignerKey.Run()
	if err != nil {
		return nil, err
	}

	return &encryptedCosignerKeyWithStatus.Status, nil
}
