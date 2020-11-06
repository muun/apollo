package libwallet

import (
	"testing"
)

func TestKeyCrypter(t *testing.T) {

	key, _ := NewHDPrivateKey(randomBytes(16), Regtest())
	key, _ = key.DeriveTo("m/123'/1")
	testPassphrase := "asdasdasd"

	t.Run("simple encrypt", func(t *testing.T) {
		_, err := KeyEncrypt(key, testPassphrase)
		if err != nil {
			t.Errorf("KeyEncrypt() error = %v", err)
			return
		}
	})

	t.Run("encrypt & decrypt", func(t *testing.T) {

		encrypted, err := KeyEncrypt(key, testPassphrase)
		if err != nil {
			t.Fatalf("KeyEncrypt() error = %v", err)
		}

		decrypted, err := KeyDecrypt(encrypted, testPassphrase, Regtest())
		if err != nil {
			t.Fatalf("KeyEncrypt() error = %v", err)
		}

		if decrypted.Key.String() != key.String() {
			t.Errorf("KeyEncrypt() expected key %v got %v", key, decrypted.Key)
		}

		if decrypted.Path != key.Path {
			t.Errorf("KeyEncrypt() expected path %v got %v", key.Path, decrypted.Path)
		}
	})

	t.Run("bad passphrase", func(t *testing.T) {

		encrypted, err := KeyEncrypt(key, testPassphrase)
		if err != nil {
			t.Fatalf("KeyEncrypt() error = %v", err)
		}

		_, err = KeyDecrypt(encrypted, testPassphrase+"foo", Regtest())
		if err == nil {
			t.Fatalf("expected decryption error")
		}
	})
}
