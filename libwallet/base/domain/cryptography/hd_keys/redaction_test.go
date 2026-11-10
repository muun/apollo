package hd_keys_test

import (
	"fmt"
	"testing"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/platform/secret"
	"github.com/muun/libwallet/platform/test/testsecret"
)

func TestHDPrivateKeyPrintsWithoutKey(t *testing.T) {
	key, err := hd_keys.NewHDPrivateKeyFromSeed(
		[]byte("seed of thirty-two bytes........"),
		&chaincfg.MainNetParams,
	)
	require.NoError(t, err)
	ecKey, err := key.ECPrivKey()
	require.NoError(t, err)

	expected := "HDPrivateKey(" + secret.Placeholder + ")"
	require.Equal(t, expected, key.String())
	require.Equal(t, expected, key.LogValue().String())
	require.Equal(t, expected, fmt.Sprintf("%+v", key))
	testsecret.RequireRedacted(t, key, key.Base58())
	testsecret.RequireRedacted(t, key, testsecret.PrintedForms(ecKey.Serialize())...)
}
