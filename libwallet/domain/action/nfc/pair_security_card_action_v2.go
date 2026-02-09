package nfc

import (
	"crypto/ecdh"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"github.com/muun/libwallet/domain/model/security_card"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/storage"
)

type PairSecurityCardActionV2 struct {
	keyValueStorage *storage.KeyValueStorage
	muunCard        *nfc.MuunCardV2
	houstonService  service.HoustonService
}

func NewPairSecurityCardActionV2(
	storage *storage.KeyValueStorage,
	muunCard *nfc.MuunCardV2,
	houstonService service.HoustonService,
) *PairSecurityCardActionV2 {
	return &PairSecurityCardActionV2{keyValueStorage: storage, muunCard: muunCard, houstonService: houstonService}
}

func (ac *PairSecurityCardActionV2) Run() (*security_card.SecurityCardPaired, error) {
	challengePair, err := ac.houstonService.ChallengeSecurityCardPair()
	if err != nil {
		return nil, fmt.Errorf("error requesting challenge to server: %w", err)
	}

	serverPublicKey, err := hex.DecodeString(challengePair.ServerPublicKeyInHex)
	if err != nil {
		return nil, fmt.Errorf("error decoding server key: %w", err)
	}

	clientPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("error generating client private key: %w", err)
	}

	clientPublicKey := clientPrivateKey.PublicKey().Bytes()

	pairingResponse, err := ac.muunCard.Pair(serverPublicKey, clientPublicKey)
	if err != nil {
		var cardError *nfc.CardError
		if errors.As(err, &cardError) {
			switch {
			case cardError.Code == nfc.ErrSlotOccupied:
				return nil, &NoSlotsAvailableError{
					Message: "error during pairing with card",
					Cause:   err,
				}
			case cardError.Code == nfc.ErrAppletIdNotFound:
				return nil, &MuunAppletNotFoundError{
					Message: "muun applet not found",
					Cause:   err,
				}
			}
		}
		return nil, fmt.Errorf("error during pairing with card: %w", err)
	}

	registerSecurityCardJson, err := service.MapRegisterSecurityCardJson(
		pairingResponse,
		clientPublicKey,
	)

	if err != nil {
		return nil, err
	}

	registerSecurityResponse, err := ac.houstonService.RegisterSecurityCard(*registerSecurityCardJson)
	if err != nil {
		var houstonError *service.HoustonResponseError
		if errors.As(err, &houstonError) {
			switch {
			case houstonError.ErrorCode == service.ErrInvalidMac:
				return nil, &InvalidMacError{
					Message: "mac verification failed",
					Cause:   houstonError,
				}
			case houstonError.ErrorCode == service.ErrInvalidSignature:
				return nil, &InvalidMacError{
					Message: "error validating signature",
					Cause:   houstonError,
				}
			case houstonError.ErrorCode == service.ErrChallengeExpired:
				return nil, &ChallengeExpiredError{
					Message: "challenge has expired",
					Cause:   houstonError,
				}
			}
		}
		return nil, fmt.Errorf("server error registering security card: %w", err)
	}

	return service.MapSecurityCardPaired(registerSecurityResponse), nil
}
