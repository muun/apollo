package app_provided_data

import (
	"fmt"
	"io"
	"log/slog"
)

// Status codes for SecureKeyValueStore bridge calls. Transport failures travel
// through the trailing error return; everything else surfaces as a status code.
const (
	SecureKvStatusUnknown          int32 = 0 // zero value; treated as StorageFailed
	SecureKvStatusOk               int32 = 1
	SecureKvStatusNotFound         int32 = 2
	SecureKvStatusDecryptionFailed int32 = 3
	SecureKvStatusStorageFailed    int32 = 4
)

// SecureKvResponse carries the outcome of Put, Delete and Wipe. Held as a
// struct (instead of a bare int32) so new diagnostic fields can be added
// without changing the bridge signature.
type SecureKvResponse struct {
	StatusCode int32
}

// SecureKvGetResponse holds the outcome Get returns; Value is populated only
// when StatusCode is Ok, and is never empty.
type SecureKvGetResponse struct {
	Value      []byte
	StatusCode int32
}

// String returns the status code and the value's length, without the value.
func (r SecureKvGetResponse) String() string {
	return r.LogValue().String()
}

// Format prints String for every verb.
func (r SecureKvGetResponse) Format(f fmt.State, _ rune) {
	_, _ = io.WriteString(f, r.String())
}

// LogValue returns the status code and the value's length, without the value.
func (r SecureKvGetResponse) LogValue() slog.Value {
	return slog.GroupValue(
		slog.Int64("status_code", int64(r.StatusCode)),
		slog.Int("value_length", len(r.Value)),
	)
}

// SecureKeyValueStore provides hardware-encrypted key-value storage backed by native
// secure storage (Android KeyStore / iOS Keychain).
type SecureKeyValueStore interface {
	// Put encrypts and stores the value under the given key (upsert). The
	// value is never empty: empty slices don't survive the gomobile boundary.
	Put(key string, value []byte) (*SecureKvResponse, error)

	// Get returns the plaintext stored under the given key.
	Get(key string) (*SecureKvGetResponse, error)

	// Delete removes the value under the given key. No-op if missing.
	Delete(key string) (*SecureKvResponse, error)

	// Wipe deletes everything in the underlying native storage.
	Wipe() (*SecureKvResponse, error)
}
