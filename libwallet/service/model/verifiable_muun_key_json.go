package model

type VerifiableMuunKeyJson struct {
	FirstHalfKeyEncryptedToClient        string  `json:"firstHalfKeyEncryptedToClient"`
	SecondHalfKeyEncryptedToRecoveryCode string  `json:"secondHalfKeyEncryptedToRecoveryCode"`
	Proof                                *string `json:"proof"`
}
