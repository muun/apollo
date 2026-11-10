package libwallet

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
)

func TestHDPrivateKeyFromModelZero(t *testing.T) {
	model, err := hd_keys.NewHDPrivateKeyFromSeed(randomBytes(16), Regtest().network)
	require.NoError(t, err)
	facade := NewHDPrivateKeyFromModel(model)
	publicKey := facade.PublicKey()
	publicKeyBefore := publicKey.String()
	samePath, err := facade.DeriveTo(facade.Path)
	require.NoError(t, err)
	samePathBefore := samePath.Base58()

	model.Zero()

	require.Equal(t, publicKeyBefore, publicKey.String(), "Zero touched the facade's public key")
	require.Equal(t, samePathBefore, samePath.Base58(),
		"Zero touched the facade's key derived to its own path")
}
