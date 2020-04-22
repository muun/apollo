package libwallet

import (
	"reflect"
	"testing"

	"github.com/btcsuite/btcutil/base58"
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
	network := Regtest()
	salt := randomBytes(8)

	privKey, _ := NewHDPrivateKey(randomBytes(32), network)
	challengePrivKey := NewChallengePrivateKey([]byte("a very good password"), salt)

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

func TestChallengeKeyCryptoV2(t *testing.T) {

	const (
		encodedKey   = "tprv8ZgxMBicQKsPcxg1GFGZgL5zALjPwijrYNUqTi2s9JsVqDLzbpX55U9JH2PKAQKExtpdTyboZmV2ytaqr9pAHuxE1hX8k9bQgZAjq25E6P7"
		encryptedKey = "4LbSKwcepbbx4dPetoxvTWszb6mLyJHFhumzmdPRVprbn8XZBvFa6Ffarm6R3WGKutFzdxxJgQDdSHuYdjhDp1EZfSNbj12gXMND1AgmNijSxEua3LwVURU3nzWsvV5b1AsWEjJca24CaFY6T3C"
		password     = "a very good password"
		saltLength   = 8
		birthday     = 376
	)

	extractSalt := func(rawKey string) []byte {
		bytes := base58.Decode(rawKey)
		return bytes[len(bytes)-saltLength:]
	}

	challengeKey := NewChallengePrivateKey([]byte(password), extractSalt(encryptedKey))
	decryptedKey, err := challengeKey.DecryptKey(encryptedKey, Regtest())
	if err != nil {
		t.Fatal(err)
	}

	if decryptedKey.Birthday != birthday {
		t.Fatalf("decrypted birthday %v differs from expected %v", decryptedKey.Birthday, birthday)
	}

	if decryptedKey.Key.String() != encodedKey {
		t.Fatalf("key doesnt match\ngot %v\nexpected %v\n", decryptedKey.Key.String(), encodedKey)
	}

	_, err = challengeKey.PubKey().EncryptKey(decryptedKey.Key, extractSalt(encryptedKey), birthday)
	if err != nil {
		t.Fatal(err)
	}
}
