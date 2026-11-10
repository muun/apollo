package test

import (
	"encoding/hex"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/subsystems/security_card/internal/data"
)

const (
	// An uncompressed 65-byte point and a 32-byte MAC, the sizes the card's wire format fixes.
	validServerPubKeyInHex = "04" +
		"1111111111111111111111111111111111111111111111111111111111111111" +
		"1111111111111111111111111111111111111111111111111111111111111111"
	validMacInHex = "2222222222222222222222222222222222222222222222222222222222222222"
)

func signChallengeResponse() model.SignRequestChallengeResponseJSON {
	return model.SignRequestChallengeResponseJSON{
		ServerPubKeyInHex: validServerPubKeyInHex,
		ReasonInHex:       hex.EncodeToString([]byte("send 1000 sats")),
	}
}

func signChallengePayload() model.SignChallengePerCardPayloadJSON {
	return model.SignChallengePerCardPayloadJSON{
		Index:       3,
		MacInHex:    validMacInHex,
		CallCounter: 7,
	}
}

func TestMapSecurityCardSignChallengeV3(t *testing.T) {
	challenge, err := data.MapSecurityCardSignChallengeV3(
		signChallengeResponse(),
		signChallengePayload(),
	)

	require.NoError(t, err)
	assert.Equal(t, validServerPubKeyInHex, hex.EncodeToString(challenge.ServerPublicKey))
	assert.Equal(t, validMacInHex, hex.EncodeToString(challenge.MAC))
	assert.Equal(t, []byte("send 1000 sats"), challenge.Reason)
	// Distinct values: the counter and the index are easy to swap.
	assert.Equal(t, uint32(7), challenge.CallCounter)
	assert.Equal(t, uint16(3), challenge.Index)
}

func TestMapSecurityCardSignChallengeV3RejectsMalformedHex(t *testing.T) {
	testCases := []struct {
		name     string
		response model.SignRequestChallengeResponseJSON
		payload  model.SignChallengePerCardPayloadJSON
	}{
		{
			name: "server public key",
			response: model.SignRequestChallengeResponseJSON{
				ServerPubKeyInHex: "not hex",
				ReasonInHex:       signChallengeResponse().ReasonInHex,
			},
			payload: signChallengePayload(),
		},
		{
			name: "reason",
			response: model.SignRequestChallengeResponseJSON{
				ServerPubKeyInHex: validServerPubKeyInHex,
				ReasonInHex:       "not hex",
			},
			payload: signChallengePayload(),
		},
		{
			name:     "mac",
			response: signChallengeResponse(),
			payload: model.SignChallengePerCardPayloadJSON{
				Index:       3,
				MacInHex:    "not hex",
				CallCounter: 7,
			},
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			challenge, err := data.MapSecurityCardSignChallengeV3(
				testCase.response,
				testCase.payload,
			)

			require.Error(t, err)
			assert.Nil(t, challenge)
		})
	}
}

func TestMapSecurityCardSignChallengeV3RejectsFieldsOfTheWrongSize(t *testing.T) {
	response := signChallengeResponse()
	response.ServerPubKeyInHex = strings.TrimSuffix(validServerPubKeyInHex, "11")

	challenge, err := data.MapSecurityCardSignChallengeV3(response, signChallengePayload())

	require.Error(t, err)
	assert.Nil(t, challenge)
}
