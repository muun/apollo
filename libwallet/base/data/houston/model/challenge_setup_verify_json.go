package model

type ChallengeSetupVerifyJSON struct {
	ChallengeType string `json:"type"`
	PublicKey     string `json:"publicKey"`
}
