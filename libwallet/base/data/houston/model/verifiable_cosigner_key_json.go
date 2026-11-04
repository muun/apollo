package model

import (
	"fmt"
	"io"
	"log/slog"
)

type VerifiableCosignerKeyJSON struct {
	FirstHalfKeyEncryptedToClient        string  `json:"firstHalfKeyEncryptedToClient"`
	SecondHalfKeyEncryptedToRecoveryCode string  `json:"secondHalfKeyEncryptedToRecoveryCode"`
	Proof                                *string `json:"proof"`
}

// Format prints LogValue for every verb.
func (k VerifiableCosignerKeyJSON) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, k.LogValue().String())
}

// LogValue returns whether the key comes with a proof, without the encrypted key halves.
func (k VerifiableCosignerKeyJSON) LogValue() slog.Value {
	return slog.GroupValue(slog.Bool("has_proof", k.Proof != nil))
}
