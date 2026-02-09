package nfc

import (
	"encoding/hex"
	"errors"
	"fmt"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
	"log/slog"
)

type SignMessageSecurityCardActionV2 struct {
	muunCard                 *nfc.MuunCardV2
	houstonService           service.HoustonService
	keyValueStorage          *storage.KeyValueStorage
	pairSecurityCardActionV2 *PairSecurityCardActionV2
}

func NewSignMessageSecurityCardActionV2(
	muunCard *nfc.MuunCardV2,
	houstonService service.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	pairSecurityCardActionV2 *PairSecurityCardActionV2,
) *SignMessageSecurityCardActionV2 {
	return &SignMessageSecurityCardActionV2{
		muunCard:                 muunCard,
		houstonService:           houstonService,
		keyValueStorage:          keyValueStorage,
		pairSecurityCardActionV2: pairSecurityCardActionV2,
	}
}

func (ac *SignMessageSecurityCardActionV2) Run() error {

	value, err := ac.keyValueStorage.Get(storage.KeySecurityCardPairingSlot)
	if err != nil {
		return fmt.Errorf("error loading security card info: %w", err)
	}
	if value == nil {
		slog.Debug("doing automatic pairing")
		_, err = ac.pairSecurityCardActionV2.Run()
		if err != nil {
			var noSlotsAvailableErr *NoSlotsAvailableError
			if errors.As(err, &noSlotsAvailableErr) {
				return err
			}
			return &PairInternalError{
				Message: "automating first pairing failed",
				Cause:   err,
			}
		}
	}

	// For this version, just hardcode a little reason.
	// It is needed for card firmware compatibility
	reasonBytes := []byte("A")
	reasonInHex := hex.EncodeToString(reasonBytes)
	request := model.ChallengeSecurityCardSignJson{
		ReasonInHex: reasonInHex,
	}
	challengeResponse, err := ac.houstonService.ChallengeSecurityCardSign(request)
	if err != nil {
		return fmt.Errorf("error requesting a challenge from houston: %w", err)
	}

	challenge, err := service.MapSecurityCardSignChallengeResponse(challengeResponse)
	if err != nil {
		return fmt.Errorf("fail to parse sign challenge response from houston: %w", err)
	}

	signChallengeResponse, err := ac.muunCard.SignChallenge(challenge, reasonBytes)
	if err != nil {
		return fmt.Errorf("error signing challenge: %w", err)
	}

	cardPublicKeyInHex := hex.EncodeToString(signChallengeResponse.CardPublicKey)
	macInHex := hex.EncodeToString(signChallengeResponse.MAC)
	securityCardChallengeJson := model.SolveSecurityCardChallengeJson{
		PublicKeyInHex: cardPublicKeyInHex,
		MacInHex:       macInHex,
	}

	err = ac.houstonService.SolveSecurityCardChallenge(securityCardChallengeJson)
	if err != nil {
		var houstonError *service.HoustonResponseError
		if errors.As(err, &houstonError) {
			switch {
			case houstonError.ErrorCode == service.ErrInvalidSignature:
				return &InvalidMacError{
					Message: "error validating signature",
					Cause:   houstonError,
				}
			case houstonError.ErrorCode == service.ErrChallengeExpired:
				return &ChallengeExpiredError{
					Message: "challenge has expired",
					Cause:   houstonError,
				}
			}
		}
		return fmt.Errorf("error signing challenge: %w", err)
	}

	return nil
}
