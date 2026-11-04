package action

import (
	"context"
	"encoding/hex"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston"
	service_model "github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/subsystems/security_card/internal/data"
	sc_repository "github.com/muun/libwallet/subsystems/security_card/internal/data/repository"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

type PairSubmitSolvedChallengeAction interface {
	Run(ctx context.Context, signed *SignedPairChallenge) (*sc_model.SecurityCardPaired, error)
}

type pairSubmitSolvedChallengeAction struct {
	protocolRepository *sc_repository.ProtocolRepository
	houstonService     houston.HoustonService
}

func NewPairSubmitSolvedChallengeAction(
	protocolRepository *sc_repository.ProtocolRepository,
	houstonService houston.HoustonService,
) PairSubmitSolvedChallengeAction {
	return &pairSubmitSolvedChallengeAction{
		protocolRepository: protocolRepository,
		houstonService:     houstonService,
	}
}

// Run submits the signed pair challenge to Houston for verification and
// registration, then clears the persisted pending challenge from local
// storage (whether the submit succeeded or Houston rejected the attempt
// — either way the same challenge can't be reused). On success it records
// the tapped card as the paired one, as the sign flow does after it pairs,
// and returns the paired card flags.
func (ac *pairSubmitSolvedChallengeAction) Run(
	_ context.Context,
	signed *SignedPairChallenge,
) (*sc_model.SecurityCardPaired, error) {
	defer ac.protocolRepository.ClearPendingPairChallenge()

	pairingResponse := signed.PairingResponse
	submitJSON := service_model.PairSubmitSignedChallengeJSON{
		CardPubKeyInHex: hex.EncodeToString(pairingResponse.CardPublicKey),
		Index:           pairingResponse.Index,
		MetadataInHex:   hex.EncodeToString(pairingResponse.Metadata),
		MacInHex:        hex.EncodeToString(pairingResponse.MAC),
	}

	submitResponse, err := ac.houstonService.PairSubmitSignedChallenge(submitJSON)
	if err != nil {
		if mapped := mapHoustonCardError(err); mapped != nil {
			return nil, mapped
		}
		return nil, errors.Errorf("server error registering security card: %w", err)
	}

	// Houston now holds this card's pairing and no other. A record still
	// naming another card would keep the sign flow from pairing that one
	// again, and it would no longer sign.
	if err := ac.protocolRepository.SavePairedCard(signed.TappedCard); err != nil {
		return nil, err
	}

	return data.MapSecurityCardPairedV3(submitResponse), nil
}
