package action

import (
	"context"
	"encoding/hex"
	"log/slog"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/houston"
	service_model "github.com/muun/libwallet/base/data/houston/model"
	sc_data "github.com/muun/libwallet/subsystems/security_card/internal/data"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

// PairSecurityCardActionV3 drives the full V3 pairing flow end to end:
// it asks Houston for a pairing challenge (a single ephemeral server
// public key), taps the card to run the two-DH pairing, and submits the
// card's response (ephemeral pubkey + slot index + raw metadata + MAC)
// back to Houston for verification and registration.
//
// PROVISIONAL: it runs against the local SimulatedHoustonService. Replace it
// with the real V3 pairing flow once Houston supports one.
type PairSecurityCardActionV3 interface {
	// Run pairs the tapped card with Houston and returns what Houston reports about it.
	Run(ctx context.Context) (*sc_model.SecurityCardPaired, error)
}

type pairSecurityCardActionV3 struct {
	muunCard       *nfc.MuunCardV3
	houstonService houston.HoustonService
}

func NewPairSecurityCardActionV3(
	muunCard *nfc.MuunCardV3,
	houstonService houston.HoustonService,
) PairSecurityCardActionV3 {
	return &pairSecurityCardActionV3{
		muunCard:       muunCard,
		houstonService: houstonService,
	}
}

func (ac *pairSecurityCardActionV3) Run(
	ctx context.Context,
) (*sc_model.SecurityCardPaired, error) {
	slog.Info("Requesting security card pairing challenge", "cardVersion", "V3")
	challengePair, err := ac.houstonService.PairRequestChallenge()
	if err != nil {
		return nil, errors.Errorf("error requesting challenge to server: %w", err)
	}

	serverPublicKey, err := hex.DecodeString(challengePair.ServerPubKeyInHex)
	if err != nil {
		return nil, errors.Errorf("error decoding server key: %w", err)
	}

	slog.Info("Tapping security card to pair", "cardVersion", "V3")
	session, err := ac.muunCard.Connect(ctx)
	if err != nil {
		return nil, mapCardPairError(err)
	}

	pairingResponse, err := session.Pair(ctx, serverPublicKey)
	if err != nil {
		return nil, mapCardPairError(err)
	}
	slog.Info("Submitting security card pairing response", "cardVersion", "V3")

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

	return sc_data.MapSecurityCardPairedV3(submitResponse), nil
}

// mapCardPairError translates card-transport/protocol errors into the
// action-level error types the presentation layer discriminates on.
func mapCardPairError(err error) error {
	var cardError *nfc.CardError
	if errors.As(err, &cardError) {
		switch cardError.Code {
		case nfc.ErrCardOutOfPairings:
			return &sc_model.CardOutOfPairingsError{
				Message: "card spent its pairing budget",
				Cause:   err,
			}
		case nfc.ErrAppletIDNotFound:
			return &sc_model.MuunAppletNotFoundError{
				Message: "muun applet not found",
				Cause:   err,
			}
		case nfc.ErrUnknownFirmware:
			return &sc_model.UnsupportedCardVersionError{
				Message: "card runs a firmware this client does not know",
				Cause:   err,
			}
		}
	}
	return errors.Errorf("error during pairing with card: %w", err)
}
