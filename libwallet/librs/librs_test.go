package librs

import (
	"encoding/hex"
	"strings"
	"testing"

	_ "embed"
)

//go:embed test_proof.bin
var proof []byte

func decode(s string) []byte {
	val, _ := hex.DecodeString(s)
	return val
}

func TestVerifierOk(t *testing.T) {

	HPKE_EPHEMERAL_PUBLIC_KEY := "04197175cf54953d5637aaa6d9f12424d8fc708e826cf5d3442298f3c6545cfc35ab37898edf5457e4823bf35a8a08a0088236fc9194e048cd1345ca0d4f3afa8f"
	RECOVERY_CODE_PUBLIC_KEY := "04465538823cd6f5438db12bc9b57a35c0f19e5787b517502719da3fe63597566284056b4a89937f529ad7e93a76f3e894d6572db3ef493d29f7d05f2b5ff1939a"
	CIPHERTEXT := "34bfe3253dde55ec3fbe27a9f8cf91293ba40822107f2736eb4fc0228716f320450e78057c76aea4177c6a008ab7b57e"
	PLAINTEXT_PUBLIC_KEY := "04a537b6bb21a11d8662317659e2b2a27aa37a4e95b5ececa1b3fdd4a7a772dac8047073a501394fd623a5747b25bbc0a16e934ba865423df840b35a9a5019f511"

	res := string(Plonky2ServerKeyVerify(
		proof,
		decode(RECOVERY_CODE_PUBLIC_KEY),
		decode(HPKE_EPHEMERAL_PUBLIC_KEY),
		decode(CIPHERTEXT),
		decode(PLAINTEXT_PUBLIC_KEY),
	))
	if res != "ok" {
		t.Fatalf("Expected res=\"ok\", actual res=\"%s\"", res)
	}
}

func TestVerifierPanic(t *testing.T) {
	HPKE_EPHEMERAL_PUBLIC_KEY := "04197175cf54953d5637aaa6d9f12424d8fc708e826cf5d3442298f3c6545cfc35ab37898edf5457e4823bf35a8a08a0088236fc9194e048cd1345ca0d4f3afa8f"
	RECOVERY_CODE_PUBLIC_KEY := "04465538823cd6f5438db12bc9b57a35c0f19e5787b517502719da3fe63597566284056b4a89937f529ad7e93a76f3e894d6572db3ef493d29f7d05f2b5ff1939a"
	CIPHERTEXT := "34bfe3253dde55ec3fbe27a9f8cf91293ba40822107f2736eb4fc0228716f320450e78057c76aea4177c6a008ab7b57e"
	PLAINTEXT_PUBLIC_KEY := "04a537b6bb21a11d8662317659e2b2a27aa37a4e95b5ececa1b3fdd4a7a772dac8047073a501394fd623a5747b25bbc0a16e934ba865423df840b35a9a5019f511"

	modifiedProof := append([]byte{}, proof...)
	modifiedProof[100] = 7

	res := string(Plonky2ServerKeyVerify(
		modifiedProof,
		decode(RECOVERY_CODE_PUBLIC_KEY),
		decode(HPKE_EPHEMERAL_PUBLIC_KEY),
		decode(CIPHERTEXT),
		decode(PLAINTEXT_PUBLIC_KEY),
	))
	if !strings.HasPrefix(res, "panic:") {
		t.Fatalf("Expected res=\"panic:...\", actual res=\"%s\"", res)
	}
}

func TestVerifierError(t *testing.T) {
	HPKE_EPHEMERAL_PUBLIC_KEY := "04197175cf54953d5637aaa6d9f12424d8fc708e826cf5d3442298f3c6545cfc35ab37898edf5457e4823bf35a8a08a0088236fc9194e048cd1345ca0d4f3afa8f"
	// changed the first byte in RECOVERY_CODE_PUBLIC_KEY to 0x03 so that the encoding is not valid
	RECOVERY_CODE_PUBLIC_KEY := "03465538823cd6f5438db12bc9b57a35c0f19e5787b517502719da3fe63597566284056b4a89937f529ad7e93a76f3e894d6572db3ef493d29f7d05f2b5ff1939a"
	CIPHERTEXT := "34bfe3253dde55ec3fbe27a9f8cf91293ba40822107f2736eb4fc0228716f320450e78057c76aea4177c6a008ab7b57e"
	PLAINTEXT_PUBLIC_KEY := "04a537b6bb21a11d8662317659e2b2a27aa37a4e95b5ececa1b3fdd4a7a772dac8047073a501394fd623a5747b25bbc0a16e934ba865423df840b35a9a5019f511"
	res := string(Plonky2ServerKeyVerify(
		proof,
		decode(RECOVERY_CODE_PUBLIC_KEY),
		decode(HPKE_EPHEMERAL_PUBLIC_KEY),
		decode(CIPHERTEXT),
		decode(PLAINTEXT_PUBLIC_KEY),
	))
	if !strings.HasPrefix(res, "error:") {
		t.Fatalf("Expected res=\"error:...\", actual res=\"%s\"", res)
	}
}
