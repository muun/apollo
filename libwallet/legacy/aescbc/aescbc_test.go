package aescbc

import (
	"bytes"
	"crypto/rand"
	"testing"
)

func TestEncryptionWithPkcs7Padding(t *testing.T) {
	key := randomBytes(32)
	iv := randomBytes(16)

	plaintext := []byte("foobar")
	ciphertext, err := EncryptPkcs7(key, iv, plaintext)
	if err != nil {
		t.Fatal(err)
	}

	decrypted, err := DecryptPkcs7(key, iv, ciphertext)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(decrypted, plaintext) {
		t.Fatalf("expected decrypted text to match plaintext")
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
