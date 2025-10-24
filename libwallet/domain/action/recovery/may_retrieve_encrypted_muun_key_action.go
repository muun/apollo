package recovery

import (
	"github.com/muun/libwallet/storage"
)

type EncryptedMuunKeyStatus int

const (
	HasVerifiedEncryptedMuunKey EncryptedMuunKeyStatus = iota
	OnlyHasUnverifiedEncryptedMuunKey
	HasNoEncryptedMuunKey
)

type MayRetrieveEncryptedMuunKeyAction struct {
	keyValueStorage *storage.KeyValueStorage
}

type EncryptedMuunKeyWithStatus struct {
	EncryptedMuunKey *string
	Status           EncryptedMuunKeyStatus
}

func NewMayRetrieveEncryptedMuunKeyAction(
	keyValueStorage *storage.KeyValueStorage,
) *MayRetrieveEncryptedMuunKeyAction {
	return &MayRetrieveEncryptedMuunKeyAction{keyValueStorage: keyValueStorage}
}

// Try to retrieve the encrypted Muun key from the key value storage,
// without incurring a Houston API call if it is not found in storage.
func (a *MayRetrieveEncryptedMuunKeyAction) Run() (*EncryptedMuunKeyWithStatus, error) {
	keys, err := a.keyValueStorage.GetBatch([]string{
		storage.UnverifiedEncryptedMuunKey,
		storage.VerifiedEncryptedMuunKey,
	})

	if err != nil {
		return nil, err
	}

	if key, ok := keys[storage.VerifiedEncryptedMuunKey]; ok && key != nil {
		encryptedMuunKey := key.(string)
		return &EncryptedMuunKeyWithStatus{EncryptedMuunKey: &encryptedMuunKey, Status: HasVerifiedEncryptedMuunKey}, nil
	} else if key, ok := keys[storage.UnverifiedEncryptedMuunKey]; ok && key != nil {
		encryptedMuunKey := key.(string)
		return &EncryptedMuunKeyWithStatus{EncryptedMuunKey: &encryptedMuunKey, Status: OnlyHasUnverifiedEncryptedMuunKey}, nil
	} else {
		return &EncryptedMuunKeyWithStatus{EncryptedMuunKey: nil, Status: HasNoEncryptedMuunKey}, nil
	}
}
