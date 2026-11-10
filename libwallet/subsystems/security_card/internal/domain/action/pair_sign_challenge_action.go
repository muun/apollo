package action

import (
	"context"

	"github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
)

type PairSignChallengeAction interface {
	Run(ctx context.Context, fresh *model.FreshPairChallenge) (*SignedPairChallenge, error)
}

type pairSignChallengeAction struct {
	muunCard *nfc.MuunCardV3
}

func NewPairSignChallengeAction(muunCard *nfc.MuunCardV3) PairSignChallengeAction {
	return &pairSignChallengeAction{muunCard: muunCard}
}

// Run drives the NFC tap. It returns the card's response, so the downstream
// submit can pass it to Houston, and the card as it named itself, so the
// submit can record it.
func (ac *pairSignChallengeAction) Run(
	ctx context.Context,
	fresh *model.FreshPairChallenge,
) (*SignedPairChallenge, error) {
	session, err := ac.muunCard.Connect(ctx)
	if err != nil {
		return nil, mapCardPairError(err)
	}

	pairingResponse, err := session.Pair(ctx, fresh.ServerPublicKey)
	if err != nil {
		return nil, mapCardPairError(err)
	}

	return &SignedPairChallenge{
		PairingResponse: pairingResponse,
		TappedCard:      session.SelectResponse().PairedCard(),
	}, nil
}
