package libwallet

import (
	"bytes"
	"encoding/base64"
	"encoding/hex"
	"testing"
)

func TestPublicKeyEncryption(t *testing.T) {

	network := Mainnet()
	senderKey, _ := NewHDPrivateKey(randomBytes(32), network)
	receiverKey, _ := NewHDPrivateKey(randomBytes(32), network)

	payload := randomBytes(178)

	ciphertext, err := senderKey.EncrypterTo(receiverKey.PublicKey()).Encrypt(payload)
	if err != nil {
		t.Fatal(err)
	}

	ecKey, _ := senderKey.PublicKey().key.ECPubKey()
	publicKey := &PublicKey{ecKey}
	plaintext, err := receiverKey.DecrypterFrom(publicKey).Decrypt(ciphertext)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(plaintext, payload) {
		t.Fatalf("decrypted payload differed from original\ndecrypted %v\noriginal %v",
			hex.EncodeToString(plaintext),
			hex.EncodeToString(payload))
	}

	// If we don't know who the sender was
	plaintext, err = receiverKey.DecrypterFrom(nil).Decrypt(ciphertext)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(plaintext, payload) {
		t.Fatalf("decrypted payload differed from original\ndecrypted %v\noriginal %v",
			hex.EncodeToString(plaintext),
			hex.EncodeToString(payload))
	}

	badKey, _ := NewHDPrivateKey(randomBytes(32), network)
	badEcKey, _ := badKey.PublicKey().key.ECPubKey()
	badPubKey := &PublicKey{badEcKey}
	_, err = receiverKey.DecrypterFrom(badPubKey).Decrypt(ciphertext)
	if err == nil {
		t.Fatal("Expected decryption from bad sender key to fail")
	}

	derivedSenderKey, _ := senderKey.DerivedAt(10, false)
	ciphertext, err = derivedSenderKey.Encrypter().Encrypt(payload)
	if err != nil {
		t.Fatal(err)
	}

	plaintext, err = senderKey.Decrypter().Decrypt(ciphertext)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(plaintext, payload) {
		t.Fatalf("decrypted payload differed from original\ndecrypted %v\noriginal %v",
			hex.EncodeToString(plaintext),
			hex.EncodeToString(payload))
	}
}

func TestEncDecOps(t *testing.T) {

	const (
		privHex = "xprv9s21ZrQH143K36uECEJcmTnxSXfHjT9jdb7FpMoUJpENDxeRgpscDF3g2w4ySH6G9uVsGKK7e6WgGp7Vc9VVnwC2oWdrr7a3taWiKW8jKnD"
		path    = "m"
		pathLen = 1
	)
	payload := []byte("Asado Viernes")

	privKey, _ := NewHDPrivateKeyFromString(privHex, path, Mainnet())
	encrypted, _ := NewEncryptOperation(privKey, payload).Encrypt()

	decrypted, err := NewDecryptOperation(privKey, encrypted).Decrypt()
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(payload, decrypted) {
		t.Fatal("decrypt is bad")
	}
}

func TestEncryptedMetadataImplicitHardenedDerivationBug(t *testing.T) {
	const (
		encryptedMetadata       = "57t61UGHbFPyQdauas7E8nYoZU5hVB1f5YveFYS5mTbXeJuYDXikVunjiL3wFi3upuQ3pgHLUrsQpjWfWUPEH7Fq3AHTUmsA24nSLV6cJwwZRoM2gZofQ86qmsr2TBvdzpifppj8JjXahaVYwnBFUzDs1L3zr1XabCJ9fFigetkmWt5vzq5uzWdSv6dK3W5H7T3aWqkYU9is4AsMUuQFCjMgRBTU1UsMPvctLMNCAhe7Frjs6vCYf1eo9XQM44UYyEoLFdNjyDfmsXaCWR3ZbB11wLcUqr8K1UDX2cZ2hz1o91S82fXmBusMnprvteri8TiPxGJ5AEwABUMj725resLwmc5AxBUBc7PamVbC4pqKVjHDGVWTDurHb3MjLqq4kPEM7bf4P5S7cny9Ans63mQnkTWvooxYYJvsQJ7PLFdb1kpYcb4V1QNFtvEfHHjE9x8DckkiANhkqBxqVR6wCzmyEU8gSFgjG3JVtZDgNZhUTBtd2CZQLrXum4YhaEV1VmTVECCk3AZAzBvrPpjwz5zgasMHLdRSZLuGWC8XppjS8xHHjYbLdkQsxZCwZZiuxiLV9zcohEb2uchMpQmXgsgjuHGzDwcjr8e8PttvGQuvay62SmBgwsyYMWiW9B3PLny1c2URsGPAN4Uwg5ycXw83CXZ6oNubhFCjRzxw4ddXqUCqBskShG7AXETsQAkXUifD7GpcXfEEyxMgar5NTx9xQ2qebcVTbGaeWa6vvXTrhoE8UusxwA5C2Kq1M4F6E4w1tmj8YPi6LiLQtyiVpJVy3xQ2D3weNTt3JTArKHiWhURuPwpuuhdhJmaCapDhewPp83TJ8RjATaKx4ahJhQAjn1ZyXVZoi87UdwgLqWD5wB14ADdZBfN81uhArrFeq1QJ6WRebSeUNwk8G38j3cUSSLwizxmt9JKTrEXkP63QroQo4yM4ibqSo8DZ6b81i6BikjYCwtsWrxnLaPUs6xEi2Qmn2B64HULCHmqHAfjUxZ9F2TutHpMbA83kWjeSL59ZGbEUSzkj8CMir34HiHk1184fPAXww2YkmvaSjxa5QUPcNa1CQySoN88Arm8E4t72MTSCipBjYzgF7yzF4V8GwroAwSkuQ92T8PTgaSEvVxpoLNyiBPyxMtvXpJfiEw2kUEG9EL63MmEGf52NGH5ZDwVNicYE3Wfi1dRHGTcAapw5PNxMtekteo2NbaqUeDN8z5DRXRbpbVvo7ArLZCyt7FVeypRyA7bLCqGvDt3jWuN3ovvpdjRmsJLzgE4xg3oNCtMYhvAyDXAfPNNwAY6QAT9xwxztUB6vGWfVs1YJKBLrn866HU4TTzz"
		encodedXpriv            = "xprv9s21ZrQH143K2B5wwtaARqgJa9XJMFnxK3AmZT8EsYZj3MTNBvuPBGR8eDsmDvCN75Znebqf2eEJz6mJHHQzkmNs7t2FAAQCeb4hFd8HDG1"
		expectedPlaintextBase64 = "W8lXYVt0oLHqeraGOIzqI+oqaMvbqTR0K2kKLAyv8/3iydwkP7dyTKBJU63YLC85jCFlcxPGfg9Rp0WlW/snvU0E9278WizintlaUF5Z55i9TbOQmsd6m4Pr0m1qMX9/fz0pruL7ryjnWCRDk/0Nr6nlF8SfatvG6Pl+pZ7GbocJJ5t0nYVjI11NtD2VWePfFofViSr/NMT56UUWb9D8BT9W9l6Zt3r6qiEnDCrMCjV4OSwFGWtzDSwQj9Zehr2YZMImi1VayZnkj1UsOFR4Nr69NwKGaDgytLIHWucw4EMCHR2xWF2whM7F9SLOp4iM42l4S5Mh5K8CdOeJg0rDg1/2G16JkLXzSS4yVFfUgR1nxr890CvOH5mEnatyY2ImwEOFTGaHmQeJAcLm8o4W5I3R9ePovkeYHb6yKP7sZdKBb7Z2nJU+VrUsiQzO2hJ3z2yzoqALbFCx/tRSdYh403M33n/SH4+9gpkzYx+eYBmCtykNlphzkh9SLzaV3OdpeXjckDRgQaocdAL4ZGdsjRF8qWd52c+H7iVgAps6ZooEY4axSAN0ATfem+UL8QJDzLJP1/PVz9pWwD1Hmw1IaqYn/z6ZkdF4SuiZioZmlXbhGf24qgfmh+yiRq+ITrn5u0hqwreFR0QC7JDU59SK5XmzeUyPAdK264WNwkendAxM58PdK9onVfFa3qKl3FMwU4y4LyUIt+lmPugXJJTbqHAvkT1ZebuBmxsoQ+oDYTYqqwcUdTNA+NH89s4HTQogD9tCzjF7Fmpr5gG0+G1J6Rldr0nKeP9OKCJkWmvuBgX0W/7Yn90vCgRQuoHAZHTolJLvgJLEQbF0Cp53JtmVhIg0UmSwHvjqfeYSEQe3bvOJ66GWyhRWaOvWtDhbjdZNiMPK4M8XArGRQyLBoDpTdi9aZcOoOc6LqfC+mUJ8EBrrlyYvARAuiZvP6/d/KUC3oMiS98ayhAt5EU6qMHhwy4/qHPu/nS274/OnGkbvblR+nHhW+dzpxHLdgVlLjyTinJCIa/2CUj8XzpT8oMjfhrAEDFndAK5jLNfCVbU="
	)
	expectedPlaintext, err := base64.StdEncoding.DecodeString(expectedPlaintextBase64)
	if err != nil {
		t.Fatal(err)
	}

	privKey, _ := NewHDPrivateKeyFromString(encodedXpriv, "m", Mainnet())
	plaintext, err := privKey.Decrypter().Decrypt(encryptedMetadata)
	if err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(plaintext, expectedPlaintext) {
		t.Fatalf("expected:\n%s\ngot:\n%s", expectedPlaintextBase64, plaintext)
	}

}
