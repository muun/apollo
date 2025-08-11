package model

type ChallengeSetupVerifyJson struct {
	ChallengeType string `json:"type"`
	PublicKey     string `json:"publicKey"`
}
