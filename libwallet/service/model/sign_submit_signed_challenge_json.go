package model

// SignSubmitSignedChallengeJSON is the V3 sign-submit request body.
type SignSubmitSignedChallengeJSON struct {
	// TODO multi-pairing: identifies which paired card produced this
	// response. Unused while the mock is single-slot.
	AttestationPubKeyInHex string `json:"attestationPubKeyInHex"`
	CardPubKeyInHex        string `json:"cardPubKeyInHex"`
	MacInHex               string `json:"macInHex"`
}
