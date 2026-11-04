package test

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
)

func validServerPublicKey() []byte {
	return append([]byte{0x04}, bytes.Repeat([]byte{0x11}, model.ServerPublicKeyLengthV3-1)...)
}

func validMac() []byte {
	return bytes.Repeat([]byte{0x22}, model.MACLengthV3)
}

func TestNewSecurityCardSignChallengeV3(t *testing.T) {
	reason := []byte("send 1000 sats")

	challenge, err := model.NewSecurityCardSignChallengeV3(
		validServerPublicKey(),
		7,
		3,
		reason,
		validMac(),
	)

	require.NoError(t, err)
	assert.Equal(t, validServerPublicKey(), challenge.ServerPublicKey)
	assert.Equal(t, uint32(7), challenge.CallCounter)
	assert.Equal(t, uint16(3), challenge.Index)
	assert.Equal(t, reason, challenge.Reason)
	assert.Equal(t, validMac(), challenge.MAC)
}

func TestNewSecurityCardSignChallengeV3AcceptsAnEmptyReason(t *testing.T) {
	challenge, err := model.NewSecurityCardSignChallengeV3(
		validServerPublicKey(),
		1,
		0,
		nil,
		validMac(),
	)

	require.NoError(t, err)
	assert.Empty(t, challenge.Reason)
}

func TestNewSecurityCardSignChallengeV3RejectsAServerKeyOfTheWrongSize(t *testing.T) {
	for _, serverPublicKey := range [][]byte{
		nil,
		validServerPublicKey()[:model.ServerPublicKeyLengthV3-1],
		append(validServerPublicKey(), 0x33),
	} {
		challenge, err := model.NewSecurityCardSignChallengeV3(
			serverPublicKey,
			1,
			0,
			nil,
			validMac(),
		)

		require.Error(t, err)
		assert.Nil(t, challenge)
	}
}

func TestNewSecurityCardSignChallengeV3RejectsAMacOfTheWrongSize(t *testing.T) {
	for _, mac := range [][]byte{nil, validMac()[:model.MACLengthV3-1], append(validMac(), 0x33)} {
		challenge, err := model.NewSecurityCardSignChallengeV3(
			validServerPublicKey(),
			1,
			0,
			nil,
			mac,
		)

		require.Error(t, err)
		assert.Nil(t, challenge)
	}
}
