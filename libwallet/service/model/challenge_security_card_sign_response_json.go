package model

type ChallengeSecurityCardSignResponseJson struct { //nolint:staticcheck // TODO: type ChallengeSecurityCardSignResponseJson should be ChallengeSecurityCardSignResponseJSON
	ServerPublicKeyInHex string `json:"serverPublicKeyInHex"`
	MacInHex             string `json:"macInHex"`
	CardUsageCount       uint16 `json:"cardUsageCount"`
	PairingSlot          uint16 `json:"pairingSlot"`
}
