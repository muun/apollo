package encrypted_keys

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/chaincfg"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/model/encrypted_key_v3"
)

type DecryptExtendedKeyAction interface {
	Run(
		recoveryCodePrivateKey *btcec.PrivateKey,
		encryptedExtendedKey string,
		network *chaincfg.Params,
	) (*hd_keys.HDPrivateKey, error)
}

type decryptExtendedKeyAction struct{}

func NewDecryptExtendedKeyAction() DecryptExtendedKeyAction {
	return &decryptExtendedKeyAction{}
}

func (a *decryptExtendedKeyAction) Run(
	recoveryCodePrivateKey *btcec.PrivateKey,
	encryptedExtendedKey string,
	network *chaincfg.Params,
) (*hd_keys.HDPrivateKey, error) {
	return encrypted_key_v3.DecryptExtendedKey(
		recoveryCodePrivateKey, encryptedExtendedKey, network,
	)
}
