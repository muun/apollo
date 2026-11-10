package model

import (
	"fmt"
	"io"
	"log/slog"
)

type CreateFirstSessionJSON struct {
	Client          ClientJSON    `json:"client"`
	GcmToken        *string       `json:"gcmToken,omitempty"`
	PrimaryCurrency string        `json:"primaryCurrency"`
	BasePublicKey   PublicKeyJSON `json:"basePublicKey"`
}

// Format prints LogValue for every verb.
func (s CreateFirstSessionJSON) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, s.LogValue().String())
}

// LogValue returns the request without the GCM token.
func (s CreateFirstSessionJSON) LogValue() slog.Value {
	return slog.GroupValue(
		slog.Any("client", s.Client),
		slog.String("primary_currency", s.PrimaryCurrency),
		slog.Any("base_public_key", s.BasePublicKey),
	)
}
