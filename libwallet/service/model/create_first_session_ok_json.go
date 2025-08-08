package model

type CreateFirstSessionOkJson struct {
	CosigningPublicKey  PublicKeyJson `json:"cosigningPublicKey"`
	SwapServerPublicKey PublicKeyJson `json:"swapServerPublicKey"`
	// TODO: user UserJson `json:"client"`
	PlayIntegrityNonce *string `json:"playIntegrityNonce,omitempty"`
}
