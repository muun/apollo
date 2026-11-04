package verifiable_cosigner_key

import (
	"fmt"
	"io"
	"log/slog"
)

type EncryptedCosignerKeyWithVerificationFlag struct {
	EncryptedCosignerKey string
	Verified             bool
}

func NewEncryptedCosignerKeyWithVerificationFlag(
	encryptedCosignerKey string,
	verified bool,
) *EncryptedCosignerKeyWithVerificationFlag {
	return &EncryptedCosignerKeyWithVerificationFlag{
		EncryptedCosignerKey: encryptedCosignerKey,
		Verified:             verified,
	}
}

// Format prints LogValue for every verb.
func (k EncryptedCosignerKeyWithVerificationFlag) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, k.LogValue().String())
}

// LogValue returns the verification flag, without the encrypted key.
func (k EncryptedCosignerKeyWithVerificationFlag) LogValue() slog.Value {
	return slog.GroupValue(slog.Bool("verified", k.Verified))
}
