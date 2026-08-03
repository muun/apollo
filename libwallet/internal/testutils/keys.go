package testutils

import (
	"crypto/rand"

	"github.com/btcsuite/btcd/btcec/v2"

	"github.com/muun/libwallet"
	"github.com/muun/libwallet/recoverycode"
)

// TestKeys holds all cryptographic keys needed for testing recovery and challenge key actions.
type TestKeys struct {
	UserKey         *libwallet.HDPrivateKey
	MuunKey         *libwallet.HDPrivateKey
	RecoveryCodeKey *btcec.PrivateKey
	RecoveryCode    string
}

// GenerateTestKeys creates a fresh set of test keys using Regtest network.
func GenerateTestKeys() *TestKeys {
	userKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Regtest())
	if err != nil {
		panic("failed to generate user key: " + err.Error())
	}

	muunKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Regtest())
	if err != nil {
		panic("failed to generate muun key: " + err.Error())
	}

	rc := recoverycode.Generate()
	rcKey, err := recoverycode.ConvertToKey(rc, "")
	if err != nil {
		panic("failed to convert recovery code to key: " + err.Error())
	}

	return &TestKeys{
		UserKey:         userKey,
		MuunKey:         muunKey,
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
