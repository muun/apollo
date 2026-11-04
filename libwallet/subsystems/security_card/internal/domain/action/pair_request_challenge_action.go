package action

import (
	"context"
	"encoding/hex"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston"
	sc_repository "github.com/muun/libwallet/subsystems/security_card/internal/data/repository"
)

type PairRequestChallengeAction interface {
	Run(ctx context.Context) error
}

type pairRequestChallengeAction struct {
	protocolRepository *sc_repository.ProtocolRepository
	houstonService     houston.HoustonService
}

func NewPairRequestChallengeAction(
	protocolRepository *sc_repository.ProtocolRepository,
	houstonService houston.HoustonService,
) PairRequestChallengeAction {
	return &pairRequestChallengeAction{
		protocolRepository: protocolRepository,
		houstonService:     houstonService,
	}
}

func (ac *pairRequestChallengeAction) Run(_ context.Context) error {
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
