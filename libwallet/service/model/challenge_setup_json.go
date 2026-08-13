package model

type ChallengeSetupJson struct { //nolint:staticcheck // TODO: type ChallengeSetupJson should be ChallengeSetupJSON
	Type                string `json:"type"`
	PublicKey           string `json:"passwordSecretPublicKey"`
	Salt                string `json:"passwordSecretSalt"`
	EncryptedPrivateKey string `json:"encryptedPrivateKey"`
	Version             int    `json:"version"`
}
