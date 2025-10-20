package model

type ChallengeSecurityCardSignJson struct {
	ServerPublicKeyInHex string `json:"serverPublicKeyInHex"`
	CardUsageCount       int    `json:"cardUsageCount"`
	MacInHex             string `json:"macInHex"`
}
