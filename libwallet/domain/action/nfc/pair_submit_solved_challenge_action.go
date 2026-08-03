package nfc

import (
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/data/security_cards"
	"github.com/muun/libwallet/domain/model/security_card"
	"github.com/muun/libwallet/service"
)

type PairSubmitSolvedChallengeAction struct {
	protocolRepository *security_cards.ProtocolRepository
	houstonService     service.HoustonService
}

func NewPairSubmitSolvedChallengeAction(
	protocolRepository *security_cards.ProtocolRepository,
	houstonService service.HoustonService,
) *PairSubmitSolvedChallengeAction {
	return &PairSubmitSolvedChallengeAction{
		protocolRepository: protocolRepository,
		houstonService:     houstonService,
	}
}

// Run submits the signed pair challenge to Houston for verification and
// registration, then clears the persisted pending challenge from local
// storage (whether the submit succeeded or Houston rejected the attempt
// — either way the same challenge can't be reused). On success returns
// the paired card metadata + flags.
func (ac *PairSubmitSolvedChallengeAction) Run(
	signed *SignedPairChallenge,
) (*security_card.SecurityCardPaired, error) {
	defer ac.protocolRepository.ClearPendingPairChallenge()

	registerJSON, err := service.MapRegisterSecurityCardJson(
		signed.PairingResponse,
		signed.ClientPublicKey,
	)
	if err != nil {
		return nil, err
	}

	registerResponse, err := ac.houstonService.RegisterSecurityCard(*registerJSON)
	if err != nil {
		var houstonError *service.HoustonResponseError
		if errors.As(err, &houstonError) {
			switch houstonError.ErrorCode {
			case service.ErrInvalidMac:
				return nil, &InvalidMacError{
					Message: "mac verification failed",
					Cause:   houstonError,
				}
			case service.ErrInvalidSignature:
				return nil, &InvalidMacError{
					Message: "error validating signature",
					Cause:   houstonError,
				}
			case service.ErrChallengeExpired:
				return nil, &ChallengeExpiredError{
					Message: "challenge has expired",
					Cause:   houstonError,
				}
			}
		}
		return nil, errors.Errorf("server error registering security card: %w", err)
	}

	return service.MapSecurityCardPaired(registerResponse), nil
}
