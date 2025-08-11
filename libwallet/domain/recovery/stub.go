package recovery

import (
	"encoding/hex"
	"github.com/muun/libwallet/service/model"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/muun/libwallet/service"
)

func FinishRecoveryCodeSetupStub(houstonService *service.HoustonService, extendedServerCosigningPublicKey hdkeychain.ExtendedKey, recoveryCodePublicKey btcec.PublicKey) error {
	return houstonService.ChallengeKeySetupFinish(model.ChallengeSetupVerifyJson{
		ChallengeType: "RECOVERY_CODE",
		PublicKey:     hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	})

	// TODO: use the new `/finish-with-cosigning-key` endpoint and verify using the serverCosigningKey
}
