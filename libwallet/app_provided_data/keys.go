package app_provided_data

import (
	"fmt"
	"io"
	"log/slog"
)

type KeyData struct {
	Serialized string
	Path       string
}

// String returns the key path, without the serialized key.
func (k KeyData) String() string {
	return k.LogValue().String()
}

// Format prints String for every verb.
func (k KeyData) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, k.String())
}

// LogValue returns the key path, without the serialized key.
func (k KeyData) LogValue() slog.Value {
	return slog.GroupValue(slog.String("path", k.Path))
}

// KeyProvider is the low-level bridge to the keys native code holds. libwallet code must not call
// it directly; use adapted.KeyProvider, which parses its raw values.
type KeyProvider interface {
	FetchUserKeyPath() (string, error)
	FetchUserPublicKey() (*KeyData, error)
	FetchCosignerKey() (*KeyData, error)
	FetchEncryptedCosignerPrivateKey() (string, error)
	FetchMaxDerivedIndex() int
}
