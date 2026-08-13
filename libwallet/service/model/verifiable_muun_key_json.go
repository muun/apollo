package model

type VerifiableMuunKeyJson struct { //nolint:staticcheck // TODO: type VerifiableMuunKeyJson should be VerifiableMuunKeyJSON
	FirstHalfKeyEncryptedToClient        string  `json:"firstHalfKeyEncryptedToClient"`
	SecondHalfKeyEncryptedToRecoveryCode string  `json:"secondHalfKeyEncryptedToRecoveryCode"`
	Proof                                *string `json:"proof"`
}
