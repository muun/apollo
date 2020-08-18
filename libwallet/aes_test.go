package libwallet

import (
	"bytes"
	"testing"
)

func TestAESEncryptionWithPadding(t *testing.T) {
	key := randomBytes(32)
	iv := randomBytes(16)

	plaintext := []byte("foobar")
	ciphertext, err := encryptAesCbcPkcs7(key, iv, plaintext)
	if err != nil {
		t.Fatal(err)
	}

	decrypted, err := decryptAesCbcPkcs7(key, iv, ciphertext)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(decrypted, plaintext) {
		t.Fatalf("expected decrypted text to match plaintext")
	}
}
