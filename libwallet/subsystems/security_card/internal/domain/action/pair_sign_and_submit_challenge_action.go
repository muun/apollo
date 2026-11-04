package action

import (
	"context"

	"github.com/go-errors/errors"

	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

// PairSignAndSubmitChallengeAction orchestrates the full pair flow by
// composing four atomic actions in sequence:
// - pairLoadPersistedChallenge (cache check)
// - pairRequestChallenge       (refresh on miss)
// - pairSignChallenge          (NFC tap)
// - pairSubmitSolvedChallenge  (Houston validation and register)
type PairSignAndSubmitChallengeAction interface {
	Run(
		ctx context.Context,
		onProgress func(sc_model.PairProgress) error,
	) (*sc_model.SecurityCardPaired, error)
}

type pairSignAndSubmitChallengeAction struct {
	pairLoadPersistedChallenge PairLoadPersistedChallengeAction
	pairRequestChallenge       PairRequestChallengeAction
	pairSignChallenge          PairSignChallengeAction
	pairSubmitSolvedChallenge  PairSubmitSolvedChallengeAction
}

func NewPairSignAndSubmitChallengeAction(
	pairLoadPersistedChallenge PairLoadPersistedChallengeAction,
	pairRequestChallenge PairRequestChallengeAction,
	pairSignChallenge PairSignChallengeAction,
	pairSubmitSolvedChallenge PairSubmitSolvedChallengeAction,
) PairSignAndSubmitChallengeAction {
	return &pairSignAndSubmitChallengeAction{
		pairLoadPersistedChallenge: pairLoadPersistedChallenge,
		pairRequestChallenge:       pairRequestChallenge,
		pairSignChallenge:          pairSignChallenge,
		pairSubmitSolvedChallenge:  pairSubmitSolvedChallenge,
	}
}

// Run drives the orchestration. The optional onProgress callback is
// invoked at each non-terminal milestone (see sc_model.PairProgress)
// and may return an error to abort the flow early — useful when the
// caller streams progress over a connection that can fail (e.g. a
// gRPC stream whose client disconnected), so we don't keep doing work
// that has no chance of being delivered.
func (ac *pairSignAndSubmitChallengeAction) Run(
	ctx context.Context,
	onProgress func(sc_model.PairProgress) error,
) (*sc_model.SecurityCardPaired, error) {
	if onProgress == nil {
		onProgress = func(sc_model.PairProgress) error {
			return nil
		}
	}

	freshChallenge, err := ac.pairLoadPersistedChallenge.Run(ctx)
	if err != nil {
		return nil, err
	}

	if freshChallenge == nil {
		if err := onProgress(sc_model.PairProgressRefreshingChallenge); err != nil {
			return nil, err
		}

		if err := ac.pairRequestChallenge.Run(ctx); err != nil {
			return nil, err
		}

		freshChallenge, err = ac.pairLoadPersistedChallenge.Run(ctx)
		if err != nil {
			return nil, err
		}
		if freshChallenge == nil {
			return nil, errors.Errorf(
				"pair challenge missing after refresh",
			)
		}
	}

	signedChallenge, err := ac.pairSignChallenge.Run(ctx, freshChallenge)
	if err != nil {
		return nil, err
	}

	if err := onProgress(sc_model.PairProgressChallengeSigned); err != nil {
		return nil, err
	}

	return ac.pairSubmitSolvedChallenge.Run(ctx, signedChallenge)
}
