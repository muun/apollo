package model

type SolveSecurityCardChallengeJson struct { //nolint:staticcheck // TODO: type SolveSecurityCardChallengeJson should be SolveSecurityCardChallengeJSON
	PublicKeyInHex string `json:"publicKeyInHex"`
	MacInHex       string `json:"macInHex"`
}
