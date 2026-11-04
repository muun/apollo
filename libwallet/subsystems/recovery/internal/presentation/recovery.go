package presentation

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/chaincfg"

	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/action/challenge_setup"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/action/encrypted_keys"
	recovery_model "github.com/muun/libwallet/subsystems/recovery/presentation/model"
)

type Recovery struct {
	startChallengeSetup             challenge_setup.StartChallengeSetupAction
	finishChallengeSetup            challenge_setup.FinishChallengeSetupAction
	decryptExtendedKey              encrypted_keys.DecryptExtendedKeyAction
	populateEncryptedCosignerKey    encrypted_keys.PopulateEncryptedCosignerKeyAction
	mayRetrieveEncryptedCosignerKey encrypted_keys.MayRetrieveEncryptedCosignerKeyAction
}

func NewRecovery(
	startChallengeSetup challenge_setup.StartChallengeSetupAction,
	finishChallengeSetup challenge_setup.FinishChallengeSetupAction,
	decryptExtendedKey encrypted_keys.DecryptExtendedKeyAction,
	populateEncryptedCosignerKey encrypted_keys.PopulateEncryptedCosignerKeyAction,
	mayRetrieveEncryptedCosignerKey encrypted_keys.MayRetrieveEncryptedCosignerKeyAction,
) Recovery {
	return Recovery{
		startChallengeSetup:             startChallengeSetup,
		finishChallengeSetup:            finishChallengeSetup,
		decryptExtendedKey:              decryptExtendedKey,
		populateEncryptedCosignerKey:    populateEncryptedCosignerKey,
		mayRetrieveEncryptedCosignerKey: mayRetrieveEncryptedCosignerKey,
	}
}

func (r *Recovery) StartChallengeSetup(
	challengeSetupJSON model.ChallengeSetupJSON,
) (model.SetupChallengeResponseJSON, error) {
	return r.startChallengeSetup.Run(challengeSetupJSON)
}

func (r *Recovery) FinishChallengeSetup(recoveryCodePublicKey *btcec.PublicKey) error {
	return r.finishChallengeSetup.Run(recoveryCodePublicKey)
}

func (r *Recovery) DecryptExtendedKey(
	recoveryCodePrivateKey *btcec.PrivateKey,
	encryptedExtendedKey string,
	network *chaincfg.Params,
) (*hd_keys.HDPrivateKey, error) {
	return r.decryptExtendedKey.Run(
		recoveryCodePrivateKey, encryptedExtendedKey, network,
	)
}

func (r *Recovery) PopulateEncryptedCosignerKey(
	recoveryCodePublicKey *btcec.PublicKey,
) error {
	return r.populateEncryptedCosignerKey.Run(recoveryCodePublicKey)
}

func (r *Recovery) MayRetrieveEncryptedCosignerKey() (
	*recovery_model.EncryptedCosignerKeyWithStatus, error,
) {
	return r.mayRetrieveEncryptedCosignerKey.Run()
}
