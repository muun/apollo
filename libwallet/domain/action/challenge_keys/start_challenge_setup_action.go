package challenge_keys

import (
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
)

type StartChallengeSetupAction struct {
	HoustonService service.HoustonService
}

func NewStartChallengeSetupAction(houstonService service.HoustonService) *StartChallengeSetupAction {
	return &StartChallengeSetupAction{houstonService}
}

func (action *StartChallengeSetupAction) Run(challengeSetupJson model.ChallengeSetupJson) (model.SetupChallengeResponseJson, error) {
	return action.HoustonService.ChallengeKeySetupStart(challengeSetupJson)
}
