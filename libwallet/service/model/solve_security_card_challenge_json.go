package model

type SolveSecurityCardChallengeJson struct {
	PublicKeyInHex string `json:"publicKeyInHex"`
	MacInHex       string `json:"macInHex"`
}
