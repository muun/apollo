package adapted

import (
	"github.com/muun/libwallet/app_provided_data"
)

// fakeKeyBridge fakes the KeyProvider bridge. Only the user key path and
// public key are used by the tests; the other methods panic if called.
type fakeKeyBridge struct {
	path      string
	pathErr   error
	publicKey *app_provided_data.KeyData
}

func (b *fakeKeyBridge) FetchUserKeyPath() (string, error) {
	return b.path, b.pathErr
}

func (b *fakeKeyBridge) FetchUserPublicKey() (*app_provided_data.KeyData, error) {
	return b.publicKey, nil
}

func (b *fakeKeyBridge) FetchCosignerKey() (*app_provided_data.KeyData, error) {
	panic("unimplemented")
}

func (b *fakeKeyBridge) FetchEncryptedCosignerPrivateKey() (string, error) {
	panic("unimplemented")
}

func (b *fakeKeyBridge) FetchMaxDerivedIndex() int {
	panic("unimplemented")
}
