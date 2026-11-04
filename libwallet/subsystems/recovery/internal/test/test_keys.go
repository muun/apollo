package test

import (
	"crypto/rand"

	"github.com/btcsuite/btcd/btcec/v2"

	adapted_mock "github.com/muun/libwallet/app_provided_data/adapted/mock"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/legacy/recoverycode"
	"github.com/muun/libwallet/platform/test/testkeys"
)

// TestKeys holds all cryptographic keys needed for testing recovery and challenge key actions.
type TestKeys struct {
	UserKey         *libwallet.HDPrivateKey
	CosignerKey     *libwallet.HDPrivateKey
	RecoveryCodeKey *btcec.PrivateKey
	RecoveryCode    string
}

// ExpectProvidedBy lets keyProvider hand out the user private key and the cosigner public key.
// The actions only read keys from it, so the calls are allowed but not required.
func (k *TestKeys) ExpectProvidedBy(keyProvider *adapted_mock.MockKeyProvider) {
	testkeys.ExpectUserPrivateKey(keyProvider, k.UserKey.ToModel()).Maybe()
	keyProvider.EXPECT().CosignerPublicKey().
		Return(k.CosignerKey.ToModel().PublicKey(), nil).Maybe()
}

// GenerateTestKeys creates a fresh set of test keys using Regtest network.
func GenerateTestKeys() *TestKeys {
	userKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Regtest())
	if err != nil {
		panic("failed to generate user key: " + err.Error())
	}

	cosignerKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Regtest())
	if err != nil {
		panic("failed to generate cosigner key: " + err.Error())
	}

	rc := recoverycode.Generate()
	rcKey, err := recoverycode.ConvertToKey(rc, "")
	if err != nil {
		panic("failed to convert recovery code to key: " + err.Error())
	}

	return &TestKeys{
		UserKey:         userKey,
		CosignerKey:     cosignerKey,
		RecoveryCodeKey: rcKey,
		RecoveryCode:    rc,
	}
}

func randomBytes(count int) []byte {
	buf := make([]byte, count)
	_, err := rand.Read(buf)
	if err != nil {
		panic("couldn't read random bytes")
	}
	return buf
}
