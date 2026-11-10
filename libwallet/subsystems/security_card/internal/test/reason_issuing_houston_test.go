package test

import (
	"encoding/hex"

	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/houston/simulator"
)

// reasonIssuingHouston hands out the mock's sign challenges with a reason of
// its own, re-MACed so the card accepts them, since the mock issues none. It
// keeps the challenge and the submit, so a test can check what the client
// signed against what it was given. The submit stops here: the mock would
// refuse an approval over a reason it did not issue.
type reasonIssuingHouston struct {
	*simulator.SimulatedHoustonService
	reason    []byte
	challenge *model.SignRequestChallengeResponseJSON
	submit    *model.SignSubmitSignedChallengeJSON
}

func (h *reasonIssuingHouston) SignRequestChallenge(
	request model.SignRequestChallengeJSON,
) (model.SignRequestChallengeResponseJSON, error) {
	response, err := h.SimulatedHoustonService.SignRequestChallenge(request)
	if err != nil {
		return response, err
	}

	serverPublicKey, err := hex.DecodeString(response.ServerPubKeyInHex)
	if err != nil {
		return response, err
	}
	response.ReasonInHex = hex.EncodeToString(h.reason)
	for i := range response.PerCardPayloads {
		payload := &response.PerCardPayloads[i]
		payload.MacInHex = hex.EncodeToString(h.ComputeSignChallengeMAC(
			serverPublicKey, payload.CallCounter, payload.Index, h.reason,
		))
	}

	h.challenge = &response
	return response, nil
}

func (h *reasonIssuingHouston) SignSubmitSignedChallenge(
	request model.SignSubmitSignedChallengeJSON,
) error {
	h.submit = &request
	return nil
}
