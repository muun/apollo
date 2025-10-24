package recovery

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/domain/model/encrypted_key_v3"
	"github.com/muun/libwallet/storage"
)

type GetEncryptedUserKeyAction struct {
	keyValueStorage *storage.KeyValueStorage
	keyProvider     keys.KeyProvider
}

func NewGetEncryptedUserKeyAction(
	keyValueStorage *storage.KeyValueStorage,
	keyProvider keys.KeyProvider,
) *GetEncryptedUserKeyAction {
	return &GetEncryptedUserKeyAction{
		keyValueStorage: keyValueStorage,
		keyProvider:     keyProvider,
	}
}

// Compute the user encrypted key. If we already computed it, it is retrieved from storage so that the contents of the
// emergency kit do not change every time.
func (a *GetEncryptedUserKeyAction) Run(recoveryCodePublicKey *btcec.PublicKey) (string, error) {

	userExtendedPrivateKey, err := a.keyProvider.UserPrivateKey()
	if err != nil {
		return "", err
	}

	rawEncryptedKey, err := a.keyValueStorage.Get(storage.EncryptedUserKey)
	if err != nil {
		return "", err
	}

	if rawEncryptedKey != nil {
		return rawEncryptedKey.(string), nil
	}

	encryptedKey, err := encrypted_key_v3.EncryptUserKey(userExtendedPrivateKey, recoveryCodePublicKey)
	if err != nil {
		return "", err
	}

	err = a.keyValueStorage.Save(storage.EncryptedUserKey, encryptedKey)
	if err != nil {
		return "", err
	}

	return encryptedKey, nil
}
