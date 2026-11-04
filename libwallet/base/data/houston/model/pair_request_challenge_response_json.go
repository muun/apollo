package model

// PairRequestChallengeResponseJSON is the V3 pair request challenge
// response: the server's ephemeral public key the card MAC-verifies
// against during pairing.
type PairRequestChallengeResponseJSON struct {
	ServerPubKeyInHex string `json:"serverPubKeyInHex"`
}
