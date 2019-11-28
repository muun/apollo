package libwallet

import (
	"reflect"
	"testing"
)

func TestNewChallengePrivateKey(t *testing.T) {
	type args struct {
		input []byte
		salt  []byte
	}
	tests := []struct {
		name string
		args args
		want *ChallengePrivateKey
	}{
		// TODO: Add test cases.
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := NewChallengePrivateKey(tt.args.input, tt.args.salt); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("NewChallengePrivateKey() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestChallengeKeyCrypto(t *testing.T) {

	const birthday = 376
	network := Mainnet()
	salt := randomBytes(8)

	privKey, _ := NewHDPrivateKey(randomBytes(32), network)
	challengePrivKey := NewChallengePrivateKey([]byte("viva peron"), salt)

	encryptedKey, err := challengePrivKey.PubKey().EncryptKey(privKey, salt, birthday)
	if err != nil {
		t.Fatal(err)
	}

	decryptedKey, err := challengePrivKey.DecryptKey(encryptedKey, network)
	if err != nil {
		t.Fatal(err)
	}

	if privKey.String() != decryptedKey.Key.String() {
		t.Fatalf("keys dont match: orig %v vs decrypted %v", privKey.String(), decryptedKey.Key.String())
	}
	if birthday != decryptedKey.Birthday {
		t.Fatalf("birthdays dont match: expected %v got %v", birthday, decryptedKey.Birthday)
	}
}