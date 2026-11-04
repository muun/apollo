package model

type CreateFirstSessionOkJSON struct {
	CosigningPublicKey  PublicKeyJSON `json:"cosigningPublicKey"`
	SwapServerPublicKey PublicKeyJSON `json:"swapServerPublicKey"`
	// TODO: user UserJson `json:"client"`
	PlayIntegrityNonce *string `json:"playIntegrityNonce,omitempty"`
}
