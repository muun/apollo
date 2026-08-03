package model

type SignRequestChallengeResponseJSON struct {
	ServerPubKeyInHex string                            `json:"serverPubKeyInHex"`
	ReasonInHex       string                            `json:"reasonInHex"`
	PerCardPayloads   []SignChallengePerCardPayloadJSON `json:"perCardPayloads"`
}

// SignChallengePerCardPayloadJSON is the per-card slice of a sign
// challenge response. The client picks the right one by matching the
// tapped card's attestation key against AttestationPubKeyInHex.
type SignChallengePerCardPayloadJSON struct {
	AttestationPubKeyInHex string `json:"attestationPubKeyInHex"`
	Index                  uint16 `json:"index"`
	MacInHex               string `json:"macInHex"`
	ReplayCounter          uint16 `json:"replayCounter"`
}
