package testkeys

import (
	"github.com/stretchr/testify/mock"

	adapted_mock "github.com/muun/libwallet/app_provided_data/adapted/mock"
	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
)

// ExpectUserPrivateKey makes keyProvider hand fn a fresh copy of userKey and
// wipe it after, as in production, so a key escaping fn is zeroed.
func ExpectUserPrivateKey(
	keyProvider *adapted_mock.MockKeyProvider,
	userKey *hd_keys.HDPrivateKey,
) *adapted_mock.MockKeyProvider_WithUserPrivateKey_Call {
	return keyProvider.EXPECT().WithUserPrivateKey(mock.Anything).RunAndReturn(
		func(fn func(*hd_keys.HDPrivateKey) error) error {
			key, err := hd_keys.NewHDPrivateKeyFromString(
				userKey.Base58(), userKey.Path(), userKey.Network(),
			)
			if err != nil {
				return err
			}
			defer key.Zero()
			return fn(key)
		},
	)
}
