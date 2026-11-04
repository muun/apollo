package action

import (
	"context"
	"encoding/hex"
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/houston"
	service_model "github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/base/domain/cryptography/musig"
	"github.com/muun/libwallet/platform/observability/tracing"
	sc_data "github.com/muun/libwallet/subsystems/security_card/internal/data"
	sc_repository "github.com/muun/libwallet/subsystems/security_card/internal/data/repository"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/nfc"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

// SignMessageSecurityCardActionV3 drives the full V3 challenge-response
// flow end to end: it asks Houston for a challenge (server ephemeral key,
// reason, per-card MAC + call counter), taps the card to sign it, and
// submits the card's response back to Houston: its ephemeral pubkey, the
// MAC, and the commitment signature, which is the part Houston verifies as
// the card's approval. Along with it goes the client's own signature over the
// challenge, which ties the approval to this wallet.
// It pairs first whenever the recorded paired card is not the one tapped.
//
// PROVISIONAL: it runs against the local SimulatedHoustonService and signs an
// empty-reason challenge, since reason generation is future Houston work.
// Replace it with the real V3 sign flow once Houston supports one.
type SignMessageSecurityCardActionV3 interface {
	// Run signs a challenge with the tapped card, pairing first when needed,
	// and reports the firmware version the card named itself with.
	Run(ctx context.Context) (*sc_model.SecurityCardSigned, error)
}

type signMessageSecurityCardActionV3 struct {
	muunCard                 *nfc.MuunCardV3
	houstonService           houston.HoustonService
	protocolRepository       *sc_repository.ProtocolRepository
	pairSecurityCardActionV3 PairSecurityCardActionV3
	keyProvider              adapted.KeyProvider
}

func NewSignMessageSecurityCardActionV3(
	muunCard *nfc.MuunCardV3,
	houstonService houston.HoustonService,
	protocolRepository *sc_repository.ProtocolRepository,
	pairSecurityCardActionV3 PairSecurityCardActionV3,
	keyProvider adapted.KeyProvider,
) SignMessageSecurityCardActionV3 {
	return &signMessageSecurityCardActionV3{
		muunCard:                 muunCard,
		houstonService:           houstonService,
		protocolRepository:       protocolRepository,
		pairSecurityCardActionV3: pairSecurityCardActionV3,
		keyProvider:              keyProvider,
	}
}

func (ac *signMessageSecurityCardActionV3) Run(
	ctx context.Context,
) (*sc_model.SecurityCardSigned, error) {
	// The client approves the challenge with the key Houston derives from the
	// user's base public key, which is what tells Houston the approval comes
	// from this wallet. The key is loaded before the card is touched, so a key
	// that cannot be loaded stops the flow before it pairs or spends a
	// challenge.
	var clientApprovalECPrivateKey *btcec.PrivateKey
	err := ac.keyProvider.WithUserPrivateKey(func(userHDPrivateKey *hd_keys.HDPrivateKey) error {
		clientApprovalHDPrivateKey, err := userHDPrivateKey.DeriveTo(
			hd_keys.SecurityCardClientApprovalKeyPath,
		)
		if err != nil {
			return err
		}
		defer clientApprovalHDPrivateKey.Zero()
		clientApprovalECPrivateKey, err = clientApprovalHDPrivateKey.ECPrivKey()
		if err != nil {
			return errors.Errorf("convert client approval key: %w", err)
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	defer clientApprovalECPrivateKey.Zero()

	// The selection that opens the session is also what says which firmware
	// answered, so it comes first.
	session, err := ac.muunCard.Connect(ctx)
	if err != nil {
		return nil, mapCardSignError(err)
	}

	// The card as it named itself, which is what a pairing records.
	selectResponse := session.SelectResponse()
	tappedCard := selectResponse.PairedCard()
	firmwareVersion := selectResponse.FirmwareVersionName()

	// The traces name the card every attempt was made with, whether it signs
	// or not.
	tracing.AddTag(ctx, "security_card:firmware_version", firmwareVersion)
	tracing.AddTag(ctx, "security_card:short_device_id", selectResponse.ShortDeviceIDInHex())

	// The card has named its firmware, so from here on every failure carries it
	// for the apps to report.
	tappedCardError := func(err error) error {
		return &sc_model.TappedCardError{FirmwareVersion: firmwareVersion, Cause: err}
	}

	// Re-pair when what is recorded is not this card: nothing paired yet,
	// another card swapped in, or this one upgraded, which drops the pairing
	// it held. A firmware that reports no id cannot tell its cards apart, so
	// another card of that firmware passes for the paired one here and is
	// refused by the card itself when it signs.
	pairedCard, err := ac.protocolRepository.LoadPairedCard()
	if err != nil {
		return nil, tappedCardError(err)
	}
	mustPair := pairedCard == nil || *pairedCard != *tappedCard
	tracing.AddTag(ctx, "security_card:pairs_first", mustPair)
	if mustPair {
		slog.Info(
			"Pairing security card automatically",
			"tappedCard", *tappedCard,
			"pairedCard", pairedCard,
		)
		if _, err := ac.pairSecurityCardActionV3.Run(ctx); err != nil {
			// What the user can act on passes through as itself; the rest is
			// an internal failure of a pairing they never asked for.
			var cardOutOfPairingsErr *sc_model.CardOutOfPairingsError
			if errors.As(err, &cardOutOfPairingsErr) {
				return nil, tappedCardError(err)
			}
			return nil, tappedCardError(&sc_model.PairInternalError{
				Message: "automating V3 pairing failed",
				Cause:   err,
			})
		}
		err = ac.protocolRepository.SavePairedCard(tappedCard)
		if err != nil {
			return nil, tappedCardError(err)
		}
	}

	// Empty reason: the mock ignores the action and generates no reason
	// yet (real action/reason generation is future Houston work).
	request := service_model.SignRequestChallengeJSON{}

	slog.Info("Requesting security card sign challenge", "cardVersion", "V3")
	signRequestChallengeResponseJSON, err := ac.houstonService.SignRequestChallenge(request)
	if err != nil {
		return nil, tappedCardError(
			errors.Errorf("error requesting a challenge from houston: %w", err),
		)
	}

	// The payload is the tapped card's: the one that names the id the card
	// reported at SELECT. A card of a firmware that reports none owns the
	// payload that names none, since every other payload belongs to a card
	// that does report one. Two such payloads are two cards this client
	// cannot tell apart, so the flow stops rather than guess between them.
	payloads := signRequestChallengeResponseJSON.PerCardPayloads
	var payload *service_model.SignChallengePerCardPayloadJSON
	for i := range payloads {
		if payloads[i].ShortDeviceIDInHex != tappedCard.ShortDeviceIDInHex {
			continue
		}
		if payload != nil {
			return nil, tappedCardError(errors.New(
				"sign challenge carried more than one payload for the tapped card",
			))
		}
		payload = &payloads[i]
	}
	if payload == nil {
		return nil, tappedCardError(
			errors.New("sign challenge carried no payload for the tapped card"),
		)
	}

	challenge, err := sc_data.MapSecurityCardSignChallengeV3(
		signRequestChallengeResponseJSON,
		*payload,
	)
	if err != nil {
		return nil, tappedCardError(err)
	}

	// The client's approval covers the challenge alone, so it is signed
	// before the tap.
	clientApprovalKeyBytes := clientApprovalECPrivateKey.Serialize()
	clientSig, err := musig.SignSchnorr(clientApprovalKeyBytes, challenge.ClientApprovalDigest())
	// Last use of the key: wipe it now rather than after the tap and the submit.
	clear(clientApprovalKeyBytes)
	clientApprovalECPrivateKey.Zero()
	if err != nil {
		return nil, tappedCardError(err)
	}

	slog.Info(
		"Tapping security card to sign",
		"cardVersion", "V3",
		"callCounter", challenge.CallCounter,
		"index", challenge.Index,
		"reasonLength", len(challenge.Reason),
	)
	signChallengeResponse, err := session.SignChallenge(ctx, challenge)
	if err != nil {
		return nil, tappedCardError(mapCardSignError(err))
	}
	slog.Info("Submitting security card sign response", "cardVersion", "V3")

	submitJSON := service_model.SignSubmitSignedChallengeJSON{
		ShortDeviceIDInHex: payload.ShortDeviceIDInHex,
		CardPubKeyInHex:    hex.EncodeToString(signChallengeResponse.CardPublicKey),
		MacInHex:           hex.EncodeToString(signChallengeResponse.MAC),
		CommitSigInHex:     hex.EncodeToString(signChallengeResponse.CommitmentSig),
		ClientSigInHex:     hex.EncodeToString(clientSig),
	}

	err = ac.houstonService.SignSubmitSignedChallenge(submitJSON)
	if err != nil {
		if mapped := mapHoustonCardError(err); mapped != nil {
			return nil, tappedCardError(mapped)
		}
		return nil, tappedCardError(errors.Errorf("error submitting signed challenge: %w", err))
	}

	return sc_model.NewSecurityCardSigned(firmwareVersion), nil
}

// mapCardSignError translates card-transport/protocol errors from the
// sign flow into the action-level error types the presentation layer
// discriminates on.
func mapCardSignError(err error) error {
	var cardError *nfc.CardError
	if errors.As(err, &cardError) {
		switch cardError.Code {
		case nfc.ErrAppletIDNotFound:
			return &sc_model.MuunAppletNotFoundError{
				Message: "muun applet not found",
				Cause:   err,
			}
		case nfc.ErrCardMustPairAgain:
			return &sc_model.CardMustPairAgainError{
				Message: "card spent its challenge budget",
				Cause:   err,
			}
		case nfc.ErrUnknownFirmware:
			return &sc_model.UnsupportedCardVersionError{
				Message: "card runs a firmware this client does not know",
				Cause:   err,
			}
		}
	}
	return errors.Errorf("error signing challenge with card: %w", err)
}
