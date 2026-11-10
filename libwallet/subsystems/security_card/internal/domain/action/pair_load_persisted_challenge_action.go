package action

import (
	"context"
	"encoding/hex"

	"github.com/go-errors/errors"

	sc_repository "github.com/muun/libwallet/subsystems/security_card/internal/data/repository"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
)

type PairLoadPersistedChallengeAction interface {
	Run(ctx context.Context) (*model.FreshPairChallenge, error)
}

type pairLoadPersistedChallengeAction struct {
	protocolRepository *sc_repository.ProtocolRepository
}

func NewPairLoadPersistedChallengeAction(
	protocolRepository *sc_repository.ProtocolRepository,
) PairLoadPersistedChallengeAction {
	return &pairLoadPersistedChallengeAction{protocolRepository: protocolRepository}
}

// Run returns a FreshPairChallenge with the decoded server public key,
// or nil if no usable challenge is available in local storage. A nil
// result means either there is no persisted challenge or the persisted
// one is past its TTL — both cases tell the caller it must trigger a
// refresh before proceeding.
func (ac *pairLoadPersistedChallengeAction) Run(
	_ context.Context,
) (*model.FreshPairChallenge, error) {
	pending, err := ac.protocolRepository.LoadPendingPairChallenge()
	if err != nil {
		return nil, err
	}
	if pending == nil || pending.IsStale() {
		return nil, nil
	}

	serverPubKey, err := hex.DecodeString(pending.ServerPubKeyInHex)
	if err != nil {
		return nil, errors.Errorf("error decoding server public key: %w", err)
	}
	return model.NewFreshPairChallenge(serverPubKey), nil
}
