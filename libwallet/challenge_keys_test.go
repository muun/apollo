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

func TestChallengeKeySignSha(t *testing.T) {
	input := randomBytes(32)
	salt := randomBytes(32)
	challengePrivKey := NewChallengePrivateKey(input, salt)

	payload := []byte("foobar")
	_, err := challengePrivKey.SignSha(payload)
	if err != nil {
		t.Fatal(err)
	}
	// TODO(federicobond): assert that signature verifies
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

	decryptedKey, err := challengePrivKey.DecryptRawKey(encryptedKey, network)
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
	decryptedKey, err := challengeKey.DecryptRawKey(encryptedKey, Regtest())
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

func TestDecodeKeyWithOrWithoutSalt(t *testing.T) {
	const (
		// The same encoded key, with one version missing the salt field:
		saltedKey   = "4LbSKwcepbbx4dPetoxvTWszb6mLyJHFhumzmdPRVprbn8XZBvFa6Ffarm6R3WGKutFzdxxJgQDdSHuYdjhDp1EZfSNbj12gXMND1AgmNijSxEua3LwVURU3nzWsvV5b1AsWEjJca24CaFY6T3C"
		unsaltedKey = "5XEEts6mc9WV34krDWsqmpLcPCw2JkK8qJu3gFdZpP8ngkERuQEsaDvYrGkhXUpM6jQRtimTYm4XnBPujpo3MsdYBedsNVxvT3WC6uCCFuzNUZCoydVY39yJXbxva7naDxH5iTra"
	)

	expected := &EncryptedPrivateKeyInfo{
		Version:      2,
		Birthday:     376,
		CipherText:   "f6af1ecd17052a81b75902c1712567cf1c650329875feb7e24af3e27235f384054ea549025e99dc2659f95bb6447cf861aa2ec0407ea74baf5a9d6a885ae184b",
		EphPublicKey: "020a8d322dda8ff685d80b16681d4e87c109664cdc246a9d3625adfe0de203e71e",
		Salt:         "e3305526d0cd675f",
	}

	// Verify the salted version:
	actual, err := DecodeEncryptedPrivateKey(saltedKey)
	if err != nil {
		t.Fatal(err)
	}

	assertDecodedKeysEqual(t, actual, expected)

	// Verify the unsalted version:
	actual, err = DecodeEncryptedPrivateKey(unsaltedKey)
	if err != nil {
		t.Fatal(err)
	}

	expected.Salt = "0000000000000000" // unsalted key should decode with zeroed field

	assertDecodedKeysEqual(t, actual, expected)
}

func assertDecodedKeysEqual(t *testing.T, actual, expected *EncryptedPrivateKeyInfo) {
	if actual.Version != expected.Version {
		t.Fatalf("version %v expected %v", actual.Version, expected.Version)
	}

	if actual.Birthday != expected.Birthday {
		t.Fatalf("birthday %v, expected %v", actual.Birthday, expected.Birthday)
	}

	if actual.CipherText != expected.CipherText {
		t.Fatalf("cipherText %x expected %x", actual.CipherText, expected.CipherText)
	}

	if actual.EphPublicKey != expected.EphPublicKey {
		t.Fatalf("ephPublicKey %x expected %x", actual.EphPublicKey, expected.EphPublicKey)
	}

	if actual.Salt != expected.Salt {
		t.Fatalf("salt %x expected %x", actual.Salt, expected.Salt)
	}
}
