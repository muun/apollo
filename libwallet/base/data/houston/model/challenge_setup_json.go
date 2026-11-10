package model

import (
	"fmt"
	"io"
	"log/slog"
)

type ChallengeSetupJSON struct {
	Type                string `json:"type"`
	PublicKey           string `json:"passwordSecretPublicKey"`
	Salt                string `json:"passwordSecretSalt"`
	EncryptedPrivateKey string `json:"encryptedPrivateKey"`
	Version             int    `json:"version"`
}

// Format prints LogValue for every verb.
func (c ChallengeSetupJSON) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, c.LogValue().String())
}

// LogValue returns the challenge type and version, without the encrypted key and its salt.
func (c ChallengeSetupJSON) LogValue() slog.Value {
	return slog.GroupValue(
		slog.String("type", c.Type),
		slog.Int("version", c.Version),
	)
}
