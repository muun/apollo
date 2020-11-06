package keycrypt

import (
	"bytes"
	"os"
	"testing"

	"github.com/btcsuite/btcd/chaincfg"

	"github.com/btcsuite/btcutil/hdkeychain"
)

var (
	key        *hdkeychain.ExtendedKey // set by TestMain
	path       = "m/123'/1"
	passphrase = "asdasdasd"
)

func TestEncrypt(t *testing.T) {
	_, err := Encrypt(key, path, passphrase)
	if err != nil {
		t.Errorf("Encrypt() error = %v", err)
		return
	}
}

func TestEncryptDecrypt(t *testing.T) {
	encrypted, err := Encrypt(key, path, passphrase)
	if err != nil {
		t.Fatalf("Encrypt() error = %v", err)
	}

	decryptedKey, decryptedPath, err := Decrypt(encrypted, passphrase)
	if err != nil {
		t.Fatalf("Encrypt() error = %v", err)
	}

	if decryptedKey.String() != key.String() {
		t.Errorf("Encrypt() expected key %v got %v", key.String(), decryptedKey.String())
	}

	if decryptedPath != path {
		t.Errorf("Encrypt() expected path %v got %v", path, decryptedPath)
	}
}

func TestBadPassphrase(t *testing.T) {
	encrypted, err := Encrypt(key, path, passphrase)
	if err != nil {
		t.Fatalf("Encrypt() error = %v", err)
	}

	_, _, err = Decrypt(encrypted, passphrase+"foo")
	if err == nil {
		t.Fatalf("expected decryption error")
	}
}

func TestEncodeUTF16(t *testing.T) {
	tests := []struct {
		name  string
		input string
		want  []byte
	}{
		{name: "no data", input: "", want: nil},
		{name: "one char", input: "a", want: []byte{0, 97}},
		{name: "multi byte char", input: "€", want: []byte{0x20, 0xAC}},
		{name: "complex string", input: "€aह", want: []byte{0x20, 0xAC, 0, 97, 0x09, 0x39}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := encodeUTF16(tt.input); !bytes.Equal(got, tt.want) {
				t.Errorf("EncodeString() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestMain(m *testing.M) {
	var err error
	key, err = hdkeychain.NewMaster(randomBytes(32), &chaincfg.MainNetParams)
	if err != nil {
		panic(err)
	}

	os.Exit(m.Run())
}
