package libwallet

import (
	"reflect"
	"testing"
)

const (
	testPassphrase = "asdasdasd"
)

func TestKeyCrypt(t *testing.T) {

	key, _ := NewHDPrivateKey(randomBytes(16), Regtest())
	key, _ = key.DeriveTo("m/123'/1")

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

	t.Run("bad passpharse", func(t *testing.T) {

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

func Test_stringToBytes(t *testing.T) {
	type args struct {
		str string
	}
	tests := []struct {
		name string
		args args
		want []byte
	}{
		{name: "no data", args: args{str: ""}, want: []byte{}},
		{name: "one char", args: args{str: "a"}, want: []byte{0, 97}},
		{name: "multi byte char", args: args{str: "€"}, want: []byte{0x20, 0xAC}},
		{name: "complex string", args: args{str: "€aह"}, want: []byte{0x20, 0xAC, 0, 97, 0x09, 0x39}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := stringToBytes(tt.args.str); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("stringToBytes() = %v, want %v", got, tt.want)
			}
		})
	}
}
