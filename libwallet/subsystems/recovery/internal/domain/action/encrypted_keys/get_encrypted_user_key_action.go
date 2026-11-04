package encrypted_keys

import (
	"github.com/btcsuite/btcd/btcec/v2"

	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
)

type GetEncryptedUserKeyAction interface {
	Run(recoveryCodePublicKey *btcec.PublicKey) (string, error)
}

type getEncryptedUserKeyAction struct {
	keyValueStorage  *storage.KeyValueStorage
	keyProvider      adapted.KeyProvider
	userKeyEncryptor EncryptUserKeyAction
}

func NewGetEncryptedUserKeyAction(
	keyValueStorage *storage.KeyValueStorage,
	keyProvider adapted.KeyProvider,
	userKeyEncryptor EncryptUserKeyAction,
) GetEncryptedUserKeyAction {
	return &getEncryptedUserKeyAction{
		keyValueStorage:  keyValueStorage,
		keyProvider:      keyProvider,
		userKeyEncryptor: userKeyEncryptor,
	}
}

func (a *getEncryptedUserKeyAction) Run(
	recoveryCodePublicKey *btcec.PublicKey,
) (string, error) {
	rawEncryptedKey, err := a.keyValueStorage.Get(storage.EncryptedUserKey)
	if err != nil {
		return "", err
	}

	if rawEncryptedKey != nil {
		return rawEncryptedKey.(string), nil
	}

	var encryptedKey string
	err = a.keyProvider.WithUserPrivateKey(func(userPrivateKey *hd_keys.HDPrivateKey) error {
		var encryptErr error
		encryptedKey, encryptErr = a.userKeyEncryptor.Run(userPrivateKey, recoveryCodePublicKey)
		return encryptErr
	})
	if err != nil {
		return "", err
	}

	err = a.keyValueStorage.Save(storage.EncryptedUserKey, encryptedKey)
	if err != nil {
		return "", err
	}

	return encryptedKey, nil
}
