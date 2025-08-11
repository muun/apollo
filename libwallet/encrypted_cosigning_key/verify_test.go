package encrypted_cosigning_key

import (
	"bytes"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"testing"
	"time"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/recoverycode"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
)

const houstonUrl = "http://localhost:8080"
const proverUrl = "http://localhost:8130"

func MustDecode(h string, t *testing.T) []byte {
	decoded, err := hex.DecodeString(h)
	if err != nil {
		t.Fatal(err)
	}
	return decoded
}

func MustParsePublicKey(key string, t *testing.T) btcec.PublicKey {
	pubkey, err := btcec.ParsePubKey(MustDecode(key, t))
	if err != nil {
		t.Fatal(err)
	}
	return *pubkey
}

func MustParsePrivateKey(key string, t *testing.T) btcec.PrivateKey {
	privkey, _ := btcec.PrivKeyFromBytes(MustDecode(key, t))
	return *privkey

}

func WaitForCondition(timeout time.Duration, condition func() (bool, error)) error {
	end := time.Now().Add(timeout)

	var err error

	for time.Now().Before(end) {
		holds, innerErr := condition()

		if holds {
			return nil
		}

		if innerErr != nil {
			err = innerErr
		}

		time.Sleep(100 * time.Millisecond)
	}

	if err != nil {
		return fmt.Errorf("timed out waiting for condition, failed with error %w", err)
	} else {
		return fmt.Errorf("timed out waiting for condition")
	}
}

func DefaultProvider() *service.TestProvider {
	return &service.TestProvider{
		ClientVersion:     "1205",
		ClientVersionName: "2.9.2",
		Language:          "en",
		ClientType:        "FALCON",
		BaseURL:           houstonUrl,
	}
}

func CreateFirstSession(houstonService service.HoustonService, t *testing.T) model.CreateFirstSessionOkJson {
	provider := DefaultProvider()
	strClientVersion, err := strconv.Atoi(provider.ClientVersion)
	if err != nil {
		t.Fatal(err)
	}
	sessionJson := model.CreateFirstSessionJson{
		Client: model.ClientJson{
			Type:        provider.ClientType,
			BuildType:   "debug",
			Version:     strClientVersion,
			VersionName: provider.ClientVersionName,
			Language:    provider.Language,
		},
		GcmToken:        nil,
		PrimaryCurrency: "USD",
		BasePublicKey: model.PublicKeyJson{
			Key:  "tpubDAygaiK3eZ9hpC3aQkxtu5fGSTK4P7QKTwwGExN8hGZytjpEfsrUjtM8ics8Y7YLrvf1GLBZTFjcpmkEP1KKTRyo8D2ku5zz49bRudDrngd",
			Path: "m/schema:1'/recovery:1'",
		},
	}
	sessionOkJson, err := houstonService.CreateFirstSession(sessionJson)
	if err != nil {
		t.Fatal(err)
	}
	return sessionOkJson
}

// This is a valid encrypted private key used for testing
const EncryptedPrivateKey string = "v1:512:1:8:582626b7244e1e72:01b0df9b1ffbdfe8eabae00a2c208317:0a6593c59927797f6a850f1775792378b81078ba29c203b41fac51d6d62069fbb5306e6585c7abab06905d86cc01bc3c0302087edec71c9018191e68e72348e06f2b87c4b5ee0efb55c99fd9f3703ebfc94127bebd41fa72bced4747718a5c43219dd010aa2e9be848e387b3c29f4df3:6d2f736368656d613a31272f7265636f766572793a3127"

func TestCosigningKeyProofHappyPath_Integration(t *testing.T) {
	t.Skip("/verifiable-server-cosigning-key endpoint disabled")
	houstonService := service.NewHoustonService(DefaultProvider())
	sessionOkJson := CreateFirstSession(*houstonService, t)

	key, err := hdkeychain.NewKeyFromString(sessionOkJson.CosigningPublicKey.Key)
	if err != nil {
		t.Fatal(err)
	}

	cosigningKey, err := key.ECPubKey()
	if err != nil {
		t.Fatal(err)
	}

	recoveryCode := recoverycode.Generate()
	rcPrivKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}
	rcPubKey := rcPrivKey.PubKey()

	_, err = houstonService.ChallengeKeySetupStart(model.ChallengeSetupJson{
		Type:                "RECOVERY_CODE",
		PublicKey:           hex.EncodeToString(rcPubKey.SerializeCompressed()),
		EncryptedPrivateKey: EncryptedPrivateKey,
		Version:             2,
		Salt:                "dfb80ea8c30959e8",
	})
	if err != nil {
		t.Fatal(err)
	}

	err = houstonService.ChallengeKeySetupFinish(model.ChallengeSetupVerifyJson{
		ChallengeType: "RECOVERY_CODE",
		PublicKey:     hex.EncodeToString(rcPubKey.SerializeCompressed()),
	})
	if err != nil {
		t.Fatal(err)
	}

	// Wait for the proof to finish
	// it's a mock proof so actual proving is instant, but it still
	// has to propagate through the services
	err = WaitForCondition(1*time.Second, func() (bool, error) {
		result, err := houstonService.VerifiableServerCosginingKey()
		if err != nil {
			return false, err
		}
		return result.Proof != "", nil
	})
	if err != nil {
		t.Fatalf("Timed out: %s", err)
	}

	result, err := houstonService.VerifiableServerCosginingKey()
	if err != nil {
		t.Fatal(err)
	}

	if result.Proof != "mock_proof" {
		t.Fatalf("Proof is not `mock_proof`")
	}

	verifiableCosigningKey := VerifiableCosigningKey{
		EphemeralPublicKey:       MustParsePublicKey(result.EphemeralPublicKey, t),
		PaddedServerCosigningKey: MustParsePrivateKey(result.PaddedServerCosigningKey, t),
		Proof:                    result.Proof,
	}

	encryptedKey, _, err := ComputeVerifiedEncryptedServerCosigningKey(key, verifiableCosigningKey, rcPubKey)
	if err != nil {
		t.Fatal(err)
	}

	decryptedKey, err := DecryptExtendedKey(recoveryCode, encryptedKey, libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(decryptedKey.PublicKey().Raw(), cosigningKey.SerializeCompressed()) {
		t.Fatalf("Decrypted key does not match original key")
	}

	if !bytes.Equal(decryptedKey.ChainCode(), key.ChainCode()) {
		t.Fatalf("Decrypted key chain code does not match original key chain code")
	}

}

func TestCosigningKeyProofUnhappyPath_Integration(t *testing.T) {
	t.Skip("/verifiable-server-cosigning-key endpoint disabled")
	houstonService := service.NewHoustonService(DefaultProvider())
	sessionOkJson := CreateFirstSession(*houstonService, t)

	key, err := hdkeychain.NewKeyFromString(sessionOkJson.CosigningPublicKey.Key)
	if err != nil {
		t.Fatal(err)
	}

	cosigningKey, err := key.ECPubKey()
	if err != nil {
		t.Fatal(err)
	}

	recoveryCode := recoverycode.Generate()
	rcPrivKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	rcPubKey := rcPrivKey.PubKey()

	if err != nil {
		t.Fatal(err)
	}

	// This requests prevents the proof from completing, this exercising the unhappy path
	req, err := http.NewRequest("POST", proverUrl+"/testing/delay-job?pattern="+hex.EncodeToString(rcPubKey.SerializeCompressed()), nil)
	if err != nil {
		t.Fatal(err)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	if resp.StatusCode >= 300 || resp.StatusCode < 200 {
		r, _ := io.ReadAll(resp.Body)
		t.Fatalf("request failed with status code %d, resp: %s", resp.StatusCode, string(r))
	}

	_, err = houstonService.ChallengeKeySetupStart(model.ChallengeSetupJson{
		Type:                "RECOVERY_CODE",
		PublicKey:           hex.EncodeToString(rcPubKey.SerializeCompressed()),
		EncryptedPrivateKey: EncryptedPrivateKey,
		Version:             2,
		Salt:                "dfb80ea8c30959e8",
	})
	if err != nil {
		t.Fatal(err)
	}

	err = houstonService.ChallengeKeySetupFinish(model.ChallengeSetupVerifyJson{
		ChallengeType: "RECOVERY_CODE",
		PublicKey:     hex.EncodeToString(rcPubKey.SerializeCompressed()),
	})
	if err != nil {
		t.Fatal(err)
	}

	result, err := houstonService.VerifiableServerCosginingKey()
	if err != nil {
		t.Fatal(err)
	}

	if result.Proof != "" {
		t.Fatalf("Proof is not empty")
	}

	verifiableCosigningKey := VerifiableCosigningKey{
		EphemeralPublicKey:       MustParsePublicKey(result.EphemeralPublicKey, t),
		PaddedServerCosigningKey: MustParsePrivateKey(result.PaddedServerCosigningKey, t),
		Proof:                    result.Proof,
	}

	encryptedKey, _, err := ComputeVerifiedEncryptedServerCosigningKey(key, verifiableCosigningKey, rcPubKey)
	if err != nil {
		t.Fatal(err)
	}

	decryptedKey, err := DecryptExtendedKey(recoveryCode, encryptedKey, libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(decryptedKey.PublicKey().Raw(), cosigningKey.SerializeCompressed()) {
		t.Fatalf("Decrypted key does not match original key")
	}

	if !bytes.Equal(decryptedKey.ChainCode(), key.ChainCode()) {
		t.Fatalf("Decrypted key chain code does not match original key chain code")
	}

}

func TestSubtractPublicKeys(t *testing.T) {

	aSerialization, err := hex.DecodeString("028e949335c8d8bc841167860949c990ac10c87004f74c4513c39603dddf687dbb")
	if err != nil {
		t.Fatal(err)
	}

	bSerialization, err := hex.DecodeString("0316e7c706d5bfd42194360e7109d0717c18bdba36c24442af99555dc981d1a66b")
	if err != nil {
		t.Fatal(err)
	}

	aMinusBExpected := "03d8047be580c609b8f63ba3c9ffb4b4bf20da0ae655d651fc95afb4f087b9f3d5"

	A, err := btcec.ParsePubKey(aSerialization)
	if err != nil {
		t.Fatal(err)
	}

	B, err := btcec.ParsePubKey(bSerialization)
	if err != nil {
		t.Fatal(err)
	}

	if hex.EncodeToString(subtractPublicKeys(A, B).SerializeCompressed()) != aMinusBExpected {
		t.Fatalf("incorrect value for subtractPublicKeys(A,B)")
	}
}
