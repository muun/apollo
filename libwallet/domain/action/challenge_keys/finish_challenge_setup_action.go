package challenge_keys

import (
	"encoding/hex"
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/muun/libwallet/domain/action/recovery"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
	"log/slog"
)

type FinishChallengeSetupAction struct {
	houstonService                  service.HoustonService
	keyValueStorage                 *storage.KeyValueStorage
	computeAndStoreEncryptedMuunKey *recovery.ComputeAndStoreEncryptedMuunKeyAction
}

func NewFinishChallengeSetupAction(
	houstonService service.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	computeAndStoreEncryptedMuunKey *recovery.ComputeAndStoreEncryptedMuunKeyAction,
) *FinishChallengeSetupAction {
	return &FinishChallengeSetupAction{
		houstonService,
		keyValueStorage,
		computeAndStoreEncryptedMuunKey,
	}
}

func (action *FinishChallengeSetupAction) Run(recoveryCodePublicKey *btcec.PublicKey) error {

	challengeSetupVerifyJson := model.ChallengeSetupVerifyJson{
		ChallengeType: "RECOVERY_CODE",
		PublicKey:     hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	}

	verifiableMuunKeyJson, err := action.houstonService.ChallengeSetupFinishWithVerifiableMuunKey(
		challengeSetupVerifyJson,
	)
	if err != nil {
		return err
	}

	// If an error occurs during verification we log it, but we do not return it.
	err = action.computeAndStoreEncryptedMuunKey.Run(
		recoveryCodePublicKey,
		&verifiableMuunKeyJson,
	)
	if err != nil {
		slog.Error(
			"An error occurred during encrypted muun key verification",
			slog.Any("error", err),
		)
	}
	return nil
}
