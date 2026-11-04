package libwallet

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/platform/secret"
	"github.com/muun/libwallet/platform/test/testsecret"
)

func TestChallengePrivateKeyPrintsWithoutKey(t *testing.T) {
	challengeKey := NewChallengePrivateKey([]byte("password"), []byte("saltsalt"))
	keyForms := testsecret.PrintedForms(challengeKey.key.Serialize())

	expected := "ChallengePrivateKey(" + secret.Placeholder + ")"
	require.Equal(t, expected, fmt.Sprintf("%s", challengeKey))
	require.Equal(t, expected, challengeKey.LogValue().String())
	testsecret.RequireRedacted(t, challengeKey, keyForms...)
}

func TestHDPrivateKeyPrintsWithoutKey(t *testing.T) {
	hdKey, err := NewHDPrivateKey([]byte("seed of thirty-two bytes........"), Mainnet())
	require.NoError(t, err)

	expected := "HDPrivateKey(" + secret.Placeholder + ")"
	require.Equal(t, expected, hdKey.String())
	require.Equal(t, expected, fmt.Sprintf("%s", hdKey))
	require.Equal(t, expected, hdKey.LogValue().String())
	testsecret.RequireRedacted(t, hdKey, hdKey.Base58())
}
