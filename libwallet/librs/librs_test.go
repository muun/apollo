package librs

import (
	_ "embed"
	"encoding/hex"
	"strings"
	"testing"
)

//go:embed test_proof.bin
var proof []byte

func decode(s string) []byte {
	val, _ := hex.DecodeString(s)
	return val
}

func TestVerifierOk(t *testing.T) {

	hpkeEphemeralPublicKey := "0471b55503fb340ec6c202d6cdce7d49c365b78ae2fa3bab06ae87553610006553441e4f7ad3c3c834b0e0538ac241e2adc61c85a10ec7341eb1129edb0caccd0a" //nolint:lll
	recoveryCodePublicKey := "04dc5489ca59d23d4deebc778850651da1f3da1c505db198df8e5cf9fe322964c7c5ab62cac0b255be7d75606e04bc8015e70c39d6e0d6faaf435eb92c29043ded"  //nolint:lll
	ciphertext := "23d170accd4b2849fbfa0e8e49f753eefb274c0449ab8ab46e9f35a4e2265f054d7cbab020157c34c5ba61e0e7695608"                                               //nolint:lll
	plaintextPublicKey := "0468a18701d75331dddbef334c070931cf3561288e78346666fdcc01fb28aac0f17823d00b35cd06eb0508067a345027ab03a716ea825220059a168c6a6d5090db"     //nolint:lll

	res := string(Plonky2ServerKeyVerify(
		proof,
		decode(recoveryCodePublicKey),
		decode(hpkeEphemeralPublicKey),
		decode(ciphertext),
		decode(plaintextPublicKey),
	))
	if res != "ok" {
		t.Fatalf("Expected res=\"ok\", actual res=\"%s\"", res)
	}
}

func TestVerifierPanic(t *testing.T) {
	hpkeEphemeralPublicKey := "0471b55503fb340ec6c202d6cdce7d49c365b78ae2fa3bab06ae87553610006553441e4f7ad3c3c834b0e0538ac241e2adc61c85a10ec7341eb1129edb0caccd0a" //nolint:lll
	recoveryCodePublicKey := "04dc5489ca59d23d4deebc778850651da1f3da1c505db198df8e5cf9fe322964c7c5ab62cac0b255be7d75606e04bc8015e70c39d6e0d6faaf435eb92c29043ded"  //nolint:lll
	ciphertext := "23d170accd4b2849fbfa0e8e49f753eefb274c0449ab8ab46e9f35a4e2265f054d7cbab020157c34c5ba61e0e7695608"                                               //nolint:lll
	plaintextPublicKey := "0468a18701d75331dddbef334c070931cf3561288e78346666fdcc01fb28aac0f17823d00b35cd06eb0508067a345027ab03a716ea825220059a168c6a6d5090db"     //nolint:lll

	modifiedProof := append([]byte{}, proof...)
	modifiedProof[100] = 7

	res := string(Plonky2ServerKeyVerify(
		modifiedProof,
		decode(recoveryCodePublicKey),
		decode(hpkeEphemeralPublicKey),
		decode(ciphertext),
		decode(plaintextPublicKey),
	))
	if !strings.HasPrefix(res, "panic:") {
		t.Fatalf("Expected res=\"panic:...\", actual res=\"%s\"", res)
	}
}

func TestVerifierError(t *testing.T) {
	hpkeEphemeralPublicKey := "0471b55503fb340ec6c202d6cdce7d49c365b78ae2fa3bab06ae87553610006553441e4f7ad3c3c834b0e0538ac241e2adc61c85a10ec7341eb1129edb0caccd0a" //nolint:lll
	// changed the first byte in recoveryCodePublicKey to 0x03 so that the encoding is not valid
	recoveryCodePublicKey := "03dc5489ca59d23d4deebc778850651da1f3da1c505db198df8e5cf9fe322964c7c5ab62cac0b255be7d75606e04bc8015e70c39d6e0d6faaf435eb92c29043ded" //nolint:lll
	ciphertext := "23d170accd4b2849fbfa0e8e49f753eefb274c0449ab8ab46e9f35a4e2265f054d7cbab020157c34c5ba61e0e7695608"                                              //nolint:lll
	plaintextPublicKey := "0468a18701d75331dddbef334c070931cf3561288e78346666fdcc01fb28aac0f17823d00b35cd06eb0508067a345027ab03a716ea825220059a168c6a6d5090db"    //nolint:lll
	res := string(Plonky2ServerKeyVerify(
		proof,
		decode(recoveryCodePublicKey),
		decode(hpkeEphemeralPublicKey),
		decode(ciphertext),
		decode(plaintextPublicKey),
	))
	if !strings.HasPrefix(res, "error:") {
		t.Fatalf("Expected res=\"error:...\", actual res=\"%s\"", res)
	}
}
