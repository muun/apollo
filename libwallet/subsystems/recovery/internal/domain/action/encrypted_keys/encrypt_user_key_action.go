package encrypted_keys

import (
	"github.com/btcsuite/btcd/btcec/v2"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/model/encrypted_key_v3"
)

type EncryptUserKeyAction interface {
	Run(
		userPrivateKey *hd_keys.HDPrivateKey,
		recoveryCodePublicKey *btcec.PublicKey,
	) (string, error)
}

type encryptUserKeyAction struct{}

func NewEncryptUserKeyAction() EncryptUserKeyAction {
	return &encryptUserKeyAction{}
}

func (a *encryptUserKeyAction) Run(
	userPrivateKey *hd_keys.HDPrivateKey,
	recoveryCodePublicKey *btcec.PublicKey,
) (string, error) {
	return encrypted_key_v3.EncryptUserKey(userPrivateKey, recoveryCodePublicKey)
}
