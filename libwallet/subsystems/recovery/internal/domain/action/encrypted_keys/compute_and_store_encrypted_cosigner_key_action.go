package encrypted_keys

import (
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/model/verifiable_cosigner_key"
)

type ComputeAndStoreEncryptedCosignerKeyAction interface {
	Run(
		recoveryCodePublicKey *btcec.PublicKey,
		verifiableCosignerKeyJSON *model.VerifiableCosignerKeyJSON,
	) error
}

type computeAndStoreEncryptedCosignerKeyAction struct {
	keyValueStorage     *storage.KeyValueStorage
	keyProvider         adapted.KeyProvider
	cosignerKeyFinisher FinishCosignerKeyEncryptionAction
}

func NewComputeAndStoreEncryptedCosignerKeyAction(
	keyValueStorage *storage.KeyValueStorage,
	keyProvider adapted.KeyProvider,
	cosignerKeyFinisher FinishCosignerKeyEncryptionAction,
) ComputeAndStoreEncryptedCosignerKeyAction {
	return &computeAndStoreEncryptedCosignerKeyAction{
		keyValueStorage:     keyValueStorage,
		keyProvider:         keyProvider,
		cosignerKeyFinisher: cosignerKeyFinisher,
	}
}

// Verify and store the resulting encrypted cosigner key. This action overwrites existing keys.
func (a *computeAndStoreEncryptedCosignerKeyAction) Run(
	recoveryCodePublicKey *btcec.PublicKey,
	verifiableCosignerKeyJSON *model.VerifiableCosignerKeyJSON,
) error {
	slog.Warn("ComputeAndStoreEncryptedCosignerKeyAction.Run: start")

	cosignerHDPublicKey, err := a.keyProvider.CosignerPublicKey()
	if err != nil {
		return errors.Errorf("error obtaining cosigner key from KeyProvider: %w", err)
	}

	verifiableCosignerKey, err := verifiable_cosigner_key.NewVerifiableCosignerKeyFromJSON(
		verifiableCosignerKeyJSON,
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
		slog.Warn("ComputeAndStoreEncryptedCosignerKeyAction.Run: store verified key")

		return a.keyValueStorage.Save(
			storage.VerifiedEncryptedCosignerKey,
			verification.EncryptedCosignerKey)
	}

	slog.Warn("ComputeAndStoreEncryptedCosignerKeyAction.Run: store unverified key")

	return a.keyValueStorage.Save(
		storage.UnverifiedEncryptedCosignerKey,
		verification.EncryptedCosignerKey,
	)
}
