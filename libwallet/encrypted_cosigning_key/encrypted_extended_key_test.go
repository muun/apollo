package encrypted_cosigning_key

import (
	"bytes"
	"crypto/rand"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/recoverycode"
	"testing"
)

func TestEncryptExtendedKey(t *testing.T) {

	recoveryCode := recoverycode.Generate()
	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}
	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	extendedKey, err := libwallet.NewHDPrivateKey(randomBytes(32), libwallet.Mainnet())
	if err != nil {
		t.Fatal(err)
	}

	encryptedExtendedKey, err := EncryptExtendedKey(extendedKey, recoveryCodePublicKey)
	if err != nil {
		t.Fatal(err)
	}

	decryptedExtendedKey, err := DecryptExtendedKey(recoveryCode, encryptedExtendedKey, libwallet.Mainnet())
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(decryptedExtendedKey.PublicKey().Raw(), extendedKey.PublicKey().Raw()) {
		t.Fatal("decrypted public key does not match original public key")
	}

	if !bytes.Equal(decryptedExtendedKey.ChainCode(), extendedKey.ChainCode()) {
		t.Fatal("decrypted chain code does not match original chain code")
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
