package model

type VerifiableServerCosigningKeyJson struct {
	EphemeralPublicKey       string `json:"ephemeralPublicKey"`
	PaddedServerCosigningKey string `json:"paddedServerCosigningKey"`
	SharedSecretPublicKey    string `json:"sharedSecretPublicKey"`
	Proof                    string `json:"proof"`
}
