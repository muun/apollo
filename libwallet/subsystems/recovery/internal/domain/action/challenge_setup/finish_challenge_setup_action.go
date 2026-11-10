package challenge_setup

import (
	"encoding/hex"
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"

	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/subsystems/recovery/internal/domain/action/encrypted_keys"
)

type FinishChallengeSetupAction interface {
	Run(recoveryCodePublicKey *btcec.PublicKey) error
}

type finishChallengeSetupAction struct {
	houstonService                      houston.HoustonService
	keyValueStorage                     *storage.KeyValueStorage
	computeAndStoreEncryptedCosignerKey encrypted_keys.ComputeAndStoreEncryptedCosignerKeyAction
}

func NewFinishChallengeSetupAction(
	houstonService houston.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	computeAndStoreEncryptedCosignerKey encrypted_keys.ComputeAndStoreEncryptedCosignerKeyAction,
) FinishChallengeSetupAction {
	return &finishChallengeSetupAction{
		houstonService,
		keyValueStorage,
		computeAndStoreEncryptedCosignerKey,
	}
}

func (action *finishChallengeSetupAction) Run(recoveryCodePublicKey *btcec.PublicKey) error {

	challengeSetupVerifyJSON := model.ChallengeSetupVerifyJSON{
		ChallengeType: "RECOVERY_CODE",
		PublicKey:     hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	}

	verifiableCosignerKeyJSON, err :=
		action.houstonService.ChallengeSetupFinishWithVerifiableCosignerKey(
			challengeSetupVerifyJSON,
		)
	if err != nil {
		return err
	}

	// If an error occurs during verification we log it, but we do not return it.
	err = action.computeAndStoreEncryptedCosignerKey.Run(
		recoveryCodePublicKey,
		&verifiableCosignerKeyJSON,
	)
	if err != nil {
		slog.Error(
			"An error occurred during encrypted cosigner key verification",
			slog.Any("error", err),
		)
	}
	return nil
}
