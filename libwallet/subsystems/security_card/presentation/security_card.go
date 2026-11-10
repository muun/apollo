package presentation

import (
	"context"

	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

type SecurityCard interface {
	SignMessageSecurityCard(ctx context.Context) (*sc_model.SecurityCardSigned, error)
	PairRequestChallenge(ctx context.Context) error
	PairSignAndSubmitChallenge(
		ctx context.Context,
		onProgress func(sc_model.PairProgress) error,
	) (*sc_model.SecurityCardPaired, error)
}
