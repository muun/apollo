package model

import (
	"fmt"
	"io"
	"log/slog"
)

type SetupChallengeResponseJSON struct {
	CosignerKey            *string `json:"muunKey,omitempty"`
	CosignerKeyFingerprint *string `json:"muunKeyFingerprint,omitempty"`
}

// Format prints LogValue for every verb.
func (r SetupChallengeResponseJSON) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, r.LogValue().String())
}

// LogValue returns the cosigner key's fingerprint, without the encrypted cosigner key.
func (r SetupChallengeResponseJSON) LogValue() slog.Value {
	fingerprint := ""
	if r.CosignerKeyFingerprint != nil {
		fingerprint = *r.CosignerKeyFingerprint
	}
	return slog.GroupValue(slog.String("cosigner_key_fingerprint", fingerprint))
}
