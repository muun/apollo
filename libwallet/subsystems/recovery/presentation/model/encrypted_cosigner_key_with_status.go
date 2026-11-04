package model

import (
	"fmt"
	"io"
	"log/slog"
)

type EncryptedCosignerKeyStatus int

const (
	HasVerifiedEncryptedCosignerKey EncryptedCosignerKeyStatus = iota
	OnlyHasUnverifiedEncryptedCosignerKey
	HasNoEncryptedCosignerKey
)

type EncryptedCosignerKeyWithStatus struct {
	EncryptedCosignerKey *string
	Status               EncryptedCosignerKeyStatus
}

// Format prints LogValue for every verb.
func (k EncryptedCosignerKeyWithStatus) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, k.LogValue().String())
}

// LogValue returns the status, without the encrypted key.
func (k EncryptedCosignerKeyWithStatus) LogValue() slog.Value {
	return slog.GroupValue(slog.Int("status", int(k.Status)))
}
