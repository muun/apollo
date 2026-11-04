package presentation

import (
	"context"

	"github.com/muun/libwallet/subsystems/security_card/internal/domain/action"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

type SecurityCard struct {
	signMessageSecurityCard    action.SignMessageSecurityCardActionV3
	pairRequestChallenge       action.PairRequestChallengeAction
	pairSignAndSubmitChallenge action.PairSignAndSubmitChallengeAction
}

func NewSecurityCard(
	signMessageSecurityCard action.SignMessageSecurityCardActionV3,
	pairRequestChallenge action.PairRequestChallengeAction,
	pairSignAndSubmitChallenge action.PairSignAndSubmitChallengeAction,
) SecurityCard {
	return SecurityCard{
		signMessageSecurityCard:    signMessageSecurityCard,
		pairRequestChallenge:       pairRequestChallenge,
		pairSignAndSubmitChallenge: pairSignAndSubmitChallenge,
	}
}

func (s *SecurityCard) SignMessageSecurityCard(
	ctx context.Context,
) (*sc_model.SecurityCardSigned, error) {
	return s.signMessageSecurityCard.Run(ctx)
}

func (s *SecurityCard) PairRequestChallenge(ctx context.Context) error {
	return s.pairRequestChallenge.Run(ctx)
}

func (s *SecurityCard) PairSignAndSubmitChallenge(
	ctx context.Context,
	onProgress func(sc_model.PairProgress) error,
) (*sc_model.SecurityCardPaired, error) {
	return s.pairSignAndSubmitChallenge.Run(ctx, onProgress)
}
