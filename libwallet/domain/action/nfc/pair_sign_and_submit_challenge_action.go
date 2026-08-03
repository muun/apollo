package nfc

import (
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/domain/model/security_card"
)

// PairProgress identifies a non-terminal milestone reached during
// PairSignAndSubmitChallengeAction.Run. The action calls onProgress at
// each transition so the caller can react (e.g. relay it through a
// streaming RPC).
type PairProgress int

const (
	// PairProgressRefreshingChallenge fires just before the action
	// triggers the self-heal path because the persisted challenge was
	// missing or stale.
	PairProgressRefreshingChallenge PairProgress = iota
	// PairProgressChallengeSigned fires right after the signing device
	// produces its response, before the action calls Houston to register.
	PairProgressChallengeSigned
)

// PairSignAndSubmitChallengeAction orchestrates the full pair flow by
// composing four atomic actions in sequence:
// - pairLoadPersistedChallenge (cache check)
// - pairRequestChallenge       (refresh on miss)
// - pairSignChallenge          (NFC tap)
// - pairSubmitSolvedChallenge  (Houston validation and register)
type PairSignAndSubmitChallengeAction struct {
	pairLoadPersistedChallenge *PairLoadPersistedChallengeAction
	pairRequestChallenge       *PairRequestChallengeAction
	pairSignChallenge          *PairSignChallengeAction
	pairSubmitSolvedChallenge  *PairSubmitSolvedChallengeAction
}

func NewPairSignAndSubmitChallengeAction(
	pairLoadPersistedChallenge *PairLoadPersistedChallengeAction,
	pairRequestChallenge *PairRequestChallengeAction,
	pairSignChallenge *PairSignChallengeAction,
	pairSubmitSolvedChallenge *PairSubmitSolvedChallengeAction,
) *PairSignAndSubmitChallengeAction {
	return &PairSignAndSubmitChallengeAction{
		pairLoadPersistedChallenge: pairLoadPersistedChallenge,
		pairRequestChallenge:       pairRequestChallenge,
		pairSignChallenge:          pairSignChallenge,
		pairSubmitSolvedChallenge:  pairSubmitSolvedChallenge,
	}
}

// Run drives the orchestration. The optional onProgress callback is
// invoked at each non-terminal milestone (see PairProgress) and may
// return an error to abort the flow early — useful when the caller
// streams progress over a connection that can fail (e.g. a gRPC stream
// whose client disconnected), so we don't keep doing work that has no
// chance of being delivered.
func (ac *PairSignAndSubmitChallengeAction) Run(
	onProgress func(PairProgress) error,
) (*security_card.SecurityCardPaired, error) {
	if onProgress == nil {
		onProgress = func(PairProgress) error { return nil }
	}

	freshChallenge, err := ac.pairLoadPersistedChallenge.Run()
	if err != nil {
		return nil, err
	}

	if freshChallenge == nil {
		if err := onProgress(PairProgressRefreshingChallenge); err != nil {
			return nil, err
		}

		if err := ac.pairRequestChallenge.Run(); err != nil {
			return nil, err
		}

		freshChallenge, err = ac.pairLoadPersistedChallenge.Run()
		if err != nil {
			return nil, err
		}
		if freshChallenge == nil {
			return nil, errors.Errorf("pair challenge missing after refresh")
		}
	}

	signedChallenge, err := ac.pairSignChallenge.Run(freshChallenge)
	if err != nil {
		return nil, err
	}

	if err := onProgress(PairProgressChallengeSigned); err != nil {
		return nil, err
	}

	return ac.pairSubmitSolvedChallenge.Run(signedChallenge)
}
