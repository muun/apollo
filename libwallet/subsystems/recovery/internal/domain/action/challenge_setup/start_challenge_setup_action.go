package challenge_setup

import (
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/houston/model"
)

type StartChallengeSetupAction interface {
	Run(
		challengeSetupJSON model.ChallengeSetupJSON,
	) (model.SetupChallengeResponseJSON, error)
}

type startChallengeSetupAction struct {
	houstonService houston.HoustonService
}

func NewStartChallengeSetupAction(
	houstonService houston.HoustonService,
) StartChallengeSetupAction {
	return &startChallengeSetupAction{houstonService}
}

func (action *startChallengeSetupAction) Run(
	challengeSetupJSON model.ChallengeSetupJSON,
) (model.SetupChallengeResponseJSON, error) {
	return action.houstonService.ChallengeKeySetupStart(challengeSetupJSON)
}
