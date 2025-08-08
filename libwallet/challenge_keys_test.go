package libwallet

import (
	"encoding/hex"
	"reflect"
	"testing"

	"github.com/btcsuite/btcd/btcutil/base58"
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

	const (
		birthday            = 376
		v2MuunSerializedKey = "4TZDw4ndUdVxGL1up8aCxeJHP3nz4RZdz7VHzSskvs7jLc8GbhM2Ey3YhHnT2EopAPkAvqDs3eUDM5uMRfnEqWPSkNVbZ73zNf6KZDWideKKkBQsCkQPXeBbygf6RioEsYpbJYsuGyMnY6QuJHh"
	)

	network := Regtest()
	salt := randomBytes(8)

	privKey, _ := NewHDPrivateKey(randomBytes(32), network)
	challengePrivKey := NewChallengePrivateKey([]byte("a very good password"), salt)

	encryptedKey, err := challengePrivKey.PubKey().EncryptKey(privKey, salt, birthday, v2MuunSerializedKey)
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
		encodedKey          = "tprv8ZgxMBicQKsPcxg1GFGZgL5zALjPwijrYNUqTi2s9JsVqDLzbpX55U9JH2PKAQKExtpdTyboZmV2ytaqr9pAHuxE1hX8k9bQgZAjq25E6P7"
		encryptedKey        = "4LbSKwcepbbx4dPetoxvTWszb6mLyJHFhumzmdPRVprbn8XZBvFa6Ffarm6R3WGKutFzdxxJgQDdSHuYdjhDp1EZfSNbj12gXMND1AgmNijSxEua3LwVURU3nzWsvV5b1AsWEjJca24CaFY6T3C"
		v2MuunSerializedKey = "4TZDw4ndUdVxGL1up8aCxeJHP3nz4RZdz7VHzSskvs7jLc8GbhM2Ey3YhHnT2EopAPkAvqDs3eUDM5uMRfnEqWPSkNVbZ73zNf6KZDWideKKkBQsCkQPXeBbygf6RioEsYpbJYsuGyMnY6QuJHh"
		password            = "a very good password"
		saltLength          = 8
		birthday            = 376
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

	_, err = challengeKey.PubKey().EncryptKey(decryptedKey.Key, extractSalt(encryptedKey), birthday, v2MuunSerializedKey)
	if err != nil {
		t.Fatal(err)
	}
}

func TestChallengeKeyCryptoV3(t *testing.T) {
	const (
		// TODO: How do I extract the encoded key in order to test withouth extracting it from the test failure?
		encodedKey          = "tprv8ZgxMBicQKsPevsz5yq38nX4LGUVyumVV4jLQuNRa9XSBwYydujAMcyVttkSnEQPwmJpe3DhcpFuc6AeL7vz1L7Lo313ygWuvwRmwE1hrYr"
		v3MuunSerializedKey = "FwASVLD82GhZTPCuf2C4tk3einixU2EVAoSEE7vK2RnLBQT4d5Uy6vH42EzLq6MLWzRQQAA9ppwTkdj2NSXmXQYTKpmzf5pjPanxguNJMgyo6bnGzCtgQsExVVGbhpCewX3u1pDFZdB6MFiY"
		password            = "a very good password"
		checksum            = "ba2aa3af07aaaa5f"
		saltLength          = 8
		birthday            = 0
	)

	extractSalt := func(rawKey string) []byte {
		bytes := base58.Decode(rawKey)
		return bytes[len(bytes)-saltLength:]
	}

	challengeKey := NewChallengePrivateKey([]byte(password), extractSalt(v3MuunSerializedKey))
	decryptedKey, err := challengeKey.DecryptRawKey(v3MuunSerializedKey, Regtest())
	if err != nil {
		t.Fatal(err)
	}

	if decryptedKey.Birthday != birthday {
		t.Fatalf("decrypted birthday %v differs from expected %v", decryptedKey.Birthday, birthday)
	}

	if decryptedKey.Key.String() != encodedKey {
		t.Fatalf("key doesnt match\ngot %v\nexpected %v\n", decryptedKey.Key.String(), encodedKey)
	}

	actualChecksum := challengeKey.PubKey().GetChecksum()

	if actualChecksum != checksum {
		t.Fatalf("checksum doesnt match\ngot %v\nexpected %v\n", actualChecksum, checksum)
	}

	_, err = challengeKey.PubKey().EncryptKey(decryptedKey.Key, extractSalt(v3MuunSerializedKey), birthday, v3MuunSerializedKey)

	if err != nil {
		t.Fatal(err)
	}
}

// This test is synced with ChallengePublicKeyTest#getChecksum. The idea is to use the same
// input data, so we can assert that a checksum generated in Java has the same value as a
// checksum generated in go.
func TestChecksum(t *testing.T) {
	const (
		expectedChecksum = "6d2c70f7530e96a6"
		hexPubKey        = "03959fef3d0a832fe494ac8ae91cec0a39f26e9913ae3b111d55092848b37304be"
	)

	serializedPublicKey, err := hex.DecodeString(hexPubKey)

	if err != nil {
		t.Fatalf("failed to decode public key: %v", err)
	}

	challengePublicKey, err := NewChallengePublicKeyFromSerialized(serializedPublicKey)

	if err != nil {
		t.Fatalf("failed to create challenge public key: %v", err)
	}

	actualChecksum := challengePublicKey.GetChecksum()

	if actualChecksum != expectedChecksum {
		t.Fatalf("checksum doesnt match\ngot %v\nexpected %v\n", actualChecksum, expectedChecksum)
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

func TestDecodeKeyV3(t *testing.T) {
	const (
		v3MuunSerializedKey = "FwBs2Fh3TCTMhTg9DNrr3MuiGhVmiNGeqpg8Zubo8mbZkYpNejJZkmsTU7iJNXEtxmWDVXaF8auAaQhFj8oMH5BhfLAdieLVAuy59RGHsCvEwzubbY7dzqYvpcSfWypzcERHxKVTMmjqwtTK"
	)

	expected := &EncryptedPrivateKeyInfo{
		Version:      3,
		Birthday:     0,
		CipherText:   "0ce3ff52d4bb35e99f0868585342cc7f95c7b282c9b57ab44177b3caeb5a5177972ced426cfc09d38703d3f2ec623fcd202456b4d5238cd7707c284182161e44",
		EphPublicKey: "03ab02bfb3f61a213d2c4ea980689fea20a866d718e6d009f1149f074ba00bc066",
		Salt:         "6675492c525f1ed2",
	}

	// Verify the salted version:
	actual, err := DecodeEncryptedPrivateKey(v3MuunSerializedKey)
	if err != nil {
		t.Fatal(err)
	}

	assertDecodedKeysEqual(t, actual, expected)
}

func TestDecodeUnknownKeyVersion(t *testing.T) {
	const (
		sixtyInBase58       = "2j"
		v3MuunSerializedKey = sixtyInBase58 + "63FwBs2Fh3TCTMhTg9DNrr3MuiGhVmiNGeqpg8Zubo8mbZkYpNejJZkmsTU7iJNXEtxmWDVXaF8auAaQhFj8oMH5BhfLAdieLVAuy59RGHsCvEwzubbY7dzqYvpcSfWypzcERHxKVTMmjqwtTK"
	)

	_, err := DecodeEncryptedPrivateKey(v3MuunSerializedKey)

	if err.Error() != "unrecognized key version 60" {
		t.Fatalf("Serialized key version was invalid but test didn't fail")
	}
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
