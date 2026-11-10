package recovery

import (
	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/action/challenge_setup"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/action/encrypted_keys"
	"github.com/muun/libwallet/subsystems/recovery/internal/presentation"
)

type Recovery = presentation.Recovery

func NewRecovery(
	houstonService houston.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	keyProvider adapted.KeyProvider,
) Recovery {
	finishCosignerKeyEncryption := encrypted_keys.NewFinishCosignerKeyEncryptionAction()
	computeAndStoreEncryptedCosignerKey :=
		encrypted_keys.NewComputeAndStoreEncryptedCosignerKeyAction(
			keyValueStorage, keyProvider, finishCosignerKeyEncryption,
		)

	return presentation.NewRecovery(
		challenge_setup.NewStartChallengeSetupAction(houstonService),
		challenge_setup.NewFinishChallengeSetupAction(
			houstonService, keyValueStorage, computeAndStoreEncryptedCosignerKey,
		),
		encrypted_keys.NewDecryptExtendedKeyAction(),
		encrypted_keys.NewPopulateEncryptedCosignerKeyAction(
			houstonService, keyValueStorage, keyProvider, finishCosignerKeyEncryption,
		),
		encrypted_keys.NewMayRetrieveEncryptedCosignerKeyAction(keyValueStorage),
	)
}
