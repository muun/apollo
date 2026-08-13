package nfc

import (
	"crypto/ecdh"
	"crypto/rand"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/domain/model/security_card"
	"github.com/muun/libwallet/domain/nfc"
)

type PairSignChallengeAction struct {
	muunCard *nfc.MuunCardV2
}

func NewPairSignChallengeAction(muunCard *nfc.MuunCardV2) *PairSignChallengeAction {
	return &PairSignChallengeAction{muunCard: muunCard}
}

// Run drives the NFC tap. It returns the card's response together with the
// client public key so the downstream submit can pass both to Houston.
func (ac *PairSignChallengeAction) Run(
	fresh *security_card.FreshPairChallenge,
) (*SignedPairChallenge, error) {
	// TODO: remove the client keypair (and the second argument to muunCard.Pair below
	// + the clientPublicKey param of MapRegisterSecurityCardJson) once the card
	// firmware drops pub_client from its MAC input. Today the card still MACs over
	// pub_client (retro-compat with cards in the field) and the mock mirrors that
	// by re-running the same MAC check; the real Houston contract already omits it.
	clientPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		return nil, errors.Errorf("error generating client private key: %w", err)
	}
	clientPublicKey := clientPrivateKey.PublicKey().Bytes()

	pairingResponse, err := ac.muunCard.Pair(fresh.ServerPublicKey, clientPublicKey)
	if err != nil {
		var cardError *nfc.CardError
		if errors.As(err, &cardError) {
			switch cardError.Code {
			case nfc.ErrSlotOccupied:
				return nil, &NoSlotsAvailableError{
					Message: "error during pairing with card",
					Cause:   err,
				}
			case nfc.ErrAppletIdNotFound:
				return nil, &MuunAppletNotFoundError{
					Message: "muun applet not found",
					Cause:   err,
				}
			}
		}
		return nil, errors.Errorf("error during pairing with card: %w", err)
	}

	return &SignedPairChallenge{
		PairingResponse: pairingResponse,
		ClientPublicKey: clientPublicKey,
	}, nil
}
