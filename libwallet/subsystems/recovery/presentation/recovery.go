package presentation

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/chaincfg"

	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	recovery_model "github.com/muun/libwallet/subsystems/recovery/presentation/model"
)

type Recovery interface {
	// Challenge setup.

	StartChallengeSetup(
		challengeSetupJSON model.ChallengeSetupJSON,
	) (model.SetupChallengeResponseJSON, error)
	FinishChallengeSetup(recoveryCodePublicKey *btcec.PublicKey) error

	// Encrypted keys.

	DecryptExtendedKey(
		recoveryCodePrivateKey *btcec.PrivateKey,
		encryptedExtendedKey string,
		network *chaincfg.Params,
	) (*hd_keys.HDPrivateKey, error)

	// Cosigner key management.

	PopulateEncryptedCosignerKey(recoveryCodePublicKey *btcec.PublicKey) error
	MayRetrieveEncryptedCosignerKey() (
		*recovery_model.EncryptedCosignerKeyWithStatus, error,
	)
}
