package data

import (
	"encoding/hex"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston/model"
	domain_model "github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

func MapSecurityCardPairedV3(
	in model.PairSubmitSignedChallengeResponseJSON,
) *sc_model.SecurityCardPaired {
	return sc_model.NewSecurityCardPaired(
		in.IsKnownProvider,
		in.IsCardAlreadyUsed,
	)
}

func MapSecurityCardSignChallengeV3(
	in model.SignRequestChallengeResponseJSON,
	payload model.SignChallengePerCardPayloadJSON,
) (*domain_model.SecurityCardSignChallengeV3, error) {

	serverPublicKey, err := hex.DecodeString(in.ServerPubKeyInHex)
	if err != nil {
		return nil, errors.Errorf(
			"error decoding server public key: %w", err,
		)
	}

	reason, err := hex.DecodeString(in.ReasonInHex)
	if err != nil {
		return nil, errors.Errorf(
			"error decoding reason: %w", err,
		)
	}

	mac, err := hex.DecodeString(payload.MacInHex)
	if err != nil {
		return nil, errors.Errorf(
			"error decoding challenge mac: %w", err,
		)
	}

	return domain_model.NewSecurityCardSignChallengeV3(
		serverPublicKey,
		payload.CallCounter,
		payload.Index,
		reason,
		mac,
	)
}
