package encrypted_keys

import (
	"github.com/btcsuite/btcd/btcec/v2"

	"github.com/muun/libwallet/base/domain/cryptography/bitcoin_hpke"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/model/encrypted_key_v3"
)

type FinishCosignerKeyEncryptionAction interface {
	Run(
		recoveryCodePublicKey *btcec.PublicKey,
		firstHalfKey *btcec.PrivateKey,
		chainCode []byte,
		secondHalfKeyEncryptedToRecoveryCode *bitcoin_hpke.EncryptedMessage,
	) (string, error)
}

type finishCosignerKeyEncryptionAction struct{}

func NewFinishCosignerKeyEncryptionAction() FinishCosignerKeyEncryptionAction {
	return &finishCosignerKeyEncryptionAction{}
}

func (a *finishCosignerKeyEncryptionAction) Run(
	recoveryCodePublicKey *btcec.PublicKey,
	firstHalfKey *btcec.PrivateKey,
	chainCode []byte,
	secondHalfKeyEncryptedToRecoveryCode *bitcoin_hpke.EncryptedMessage,
) (string, error) {
	return encrypted_key_v3.FinishCosignerKeyEncryption(
		recoveryCodePublicKey, firstHalfKey, chainCode,
		secondHalfKeyEncryptedToRecoveryCode,
	)
}
