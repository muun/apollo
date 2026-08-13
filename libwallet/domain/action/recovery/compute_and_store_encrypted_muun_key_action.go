package recovery

import (
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/domain/model/verifiable_muun_key"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
)

type ComputeAndStoreEncryptedMuunKeyAction struct {
	keyValueStorage *storage.KeyValueStorage
	keyProvider     keys.KeyProvider
}

func NewComputeAndStoreEncryptedMuunKeyAction(
	keyValueStorage *storage.KeyValueStorage,
	keyProvider keys.KeyProvider,
) *ComputeAndStoreEncryptedMuunKeyAction {
	return &ComputeAndStoreEncryptedMuunKeyAction{
		keyValueStorage: keyValueStorage,
		keyProvider:     keyProvider,
	}
}

// Verify and store the resulting encrypted muun key. This action overwrites existing keys.
func (a *ComputeAndStoreEncryptedMuunKeyAction) Run(
	recoveryCodePublicKey *btcec.PublicKey,
	verifiableMuunKeyJson *model.VerifiableMuunKeyJson, //nolint:staticcheck // TODO: method parameter verifiableMuunKeyJson should be verifiableMuunKeyJSON
) error {
	slog.Warn("ComputeAndStoreEncryptedMuunKeyAction.Run: start")

	userHDPrivateKey, err := a.keyProvider.UserPrivateKey()
	if err != nil {
		return errors.Errorf("error getting user key from KeyProvider: %w", err)
	}

	userEcPrivateKey, err := userHDPrivateKey.ECPrivateKey()
	if err != nil {
		return errors.Errorf("error obtaining user ec private key: %w", err)
	}

	muunHDPublicKey, err := a.keyProvider.MuunPublicKey()
	if err != nil {
		return errors.Errorf("error obtaining muun key from KeyProvider: %w", err)
	}

	verifiableMuunKey, err := verifiable_muun_key.VerifiableMuunKeyFromJson(verifiableMuunKeyJson)
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
		slog.Warn("ComputeAndStoreEncryptedMuunKeyAction.Run: store verified key")

		return a.keyValueStorage.Save(
			storage.VerifiedEncryptedMuunKey,
			encryptedMuunKeyWithVerificationFlag.EncryptedMuunKey)
	}

	slog.Warn("ComputeAndStoreEncryptedMuunKeyAction.Run: store unverified key")

	return a.keyValueStorage.Save(
		storage.UnverifiedEncryptedMuunKey,
		encryptedMuunKeyWithVerificationFlag.EncryptedMuunKey,
	)
}
