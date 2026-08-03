package model

type ChallengeSetupVerifyJson struct { //nolint:staticcheck // TODO: type ChallengeSetupVerifyJson should be ChallengeSetupVerifyJSON
	ChallengeType string `json:"type"`
	PublicKey     string `json:"publicKey"`
}
