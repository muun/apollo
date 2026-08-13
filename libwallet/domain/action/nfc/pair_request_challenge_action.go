package nfc

import (
	"encoding/hex"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/data/security_cards"
	"github.com/muun/libwallet/service"
)

type PairRequestChallengeAction struct {
	protocolRepository *security_cards.ProtocolRepository
	houstonService     service.HoustonService
}

func NewPairRequestChallengeAction(
	protocolRepository *security_cards.ProtocolRepository,
	houstonService service.HoustonService,
) *PairRequestChallengeAction {
	return &PairRequestChallengeAction{
		protocolRepository: protocolRepository,
		houstonService:     houstonService,
	}
}

func (ac *PairRequestChallengeAction) Run() error {
	challengePair, err := ac.houstonService.PairRequestChallenge()
	if err != nil {
		return errors.Errorf("error requesting pair challenge from server: %w", err)
	}

	_, err = hex.DecodeString(challengePair.ServerPubKeyInHex)
	if err != nil {
		return errors.Errorf("server returned malformed pair challenge pub key: %w", err)
	}

	return ac.protocolRepository.SavePendingPairChallenge(challengePair.ServerPubKeyInHex)
}
