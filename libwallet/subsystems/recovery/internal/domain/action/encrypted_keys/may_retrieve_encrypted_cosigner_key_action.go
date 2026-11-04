package encrypted_keys

import (
	"github.com/muun/libwallet/base/data/storage"
	recovery_model "github.com/muun/libwallet/subsystems/recovery/presentation/model"
)

type MayRetrieveEncryptedCosignerKeyAction interface {
	Run() (*recovery_model.EncryptedCosignerKeyWithStatus, error)
}

type mayRetrieveEncryptedCosignerKeyAction struct {
	keyValueStorage *storage.KeyValueStorage
}

func NewMayRetrieveEncryptedCosignerKeyAction(
	keyValueStorage *storage.KeyValueStorage,
) MayRetrieveEncryptedCosignerKeyAction {
	return &mayRetrieveEncryptedCosignerKeyAction{keyValueStorage: keyValueStorage}
}

func (a *mayRetrieveEncryptedCosignerKeyAction) Run() (
	*recovery_model.EncryptedCosignerKeyWithStatus, error,
) {
	keys, err := a.keyValueStorage.GetBatch([]string{
		storage.UnverifiedEncryptedCosignerKey,
		storage.VerifiedEncryptedCosignerKey,
	})

	if err != nil {
		return nil, err
	}

	if key, ok := keys[storage.VerifiedEncryptedCosignerKey]; ok && key != nil {
		encryptedCosignerKey := key.(string)
		return &recovery_model.EncryptedCosignerKeyWithStatus{
			EncryptedCosignerKey: &encryptedCosignerKey,
			Status:               recovery_model.HasVerifiedEncryptedCosignerKey,
		}, nil
	} else if key, ok := keys[storage.UnverifiedEncryptedCosignerKey]; ok && key != nil {
		encryptedCosignerKey := key.(string)
		return &recovery_model.EncryptedCosignerKeyWithStatus{
			EncryptedCosignerKey: &encryptedCosignerKey,
			Status:               recovery_model.OnlyHasUnverifiedEncryptedCosignerKey,
		}, nil
	} else {
		return &recovery_model.EncryptedCosignerKeyWithStatus{
			EncryptedCosignerKey: nil,
			Status:               recovery_model.HasNoEncryptedCosignerKey,
		}, nil
	}
}
