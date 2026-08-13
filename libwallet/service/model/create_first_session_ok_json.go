package model

type CreateFirstSessionOkJson struct { //nolint:staticcheck // TODO: type CreateFirstSessionOkJson should be CreateFirstSessionOkJSON
	CosigningPublicKey  PublicKeyJson `json:"cosigningPublicKey"`
	SwapServerPublicKey PublicKeyJson `json:"swapServerPublicKey"`
	// TODO: user UserJson `json:"client"`
	PlayIntegrityNonce *string `json:"playIntegrityNonce,omitempty"`
}
