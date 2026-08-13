package nfc

import (
	"encoding/hex"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/data/security_cards"
	"github.com/muun/libwallet/domain/model/security_card"
)

type PairLoadPersistedChallengeAction struct {
	protocolRepository *security_cards.ProtocolRepository
}

func NewPairLoadPersistedChallengeAction(
	protocolRepository *security_cards.ProtocolRepository,
) *PairLoadPersistedChallengeAction {
	return &PairLoadPersistedChallengeAction{protocolRepository: protocolRepository}
}

// Run returns a FreshPairChallenge with the decoded server public key,
// or nil if no usable challenge is available in local storage. A nil
// result means either there is no persisted challenge or the persisted
// one is past its TTL — both cases tell the caller it must trigger a
// refresh before proceeding.
func (ac *PairLoadPersistedChallengeAction) Run() (*security_card.FreshPairChallenge, error) {
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
	return security_card.NewFreshPairChallenge(serverPubKey), nil
}
