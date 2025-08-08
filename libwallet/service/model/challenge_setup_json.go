package model

type ChallengeSetupJson struct {
	Type                string `json:"type"`
	PublicKey           string `json:"passwordSecretPublicKey"`
	Salt                string `json:"passwordSecretSalt"`
	EncryptedPrivateKey string `json:"encryptedPrivateKey"`
	Version             int    `json:"version"`
}
