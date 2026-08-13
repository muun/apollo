package app_provided_data

// Status codes for SecureKeyValueStorage bridge calls. Transport failures travel
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

// SecureKvGetResponse holds the outcome Get returns; Value is populated only when StatusCode is Ok.
type SecureKvGetResponse struct {
	Value      []byte
	StatusCode int32
}

// SecureKeyValueStorage provides hardware-encrypted key-value storage backed by native
// secure storage (Android KeyStore / iOS Keychain).
type SecureKeyValueStorage interface {
	// Put encrypts and stores the value under the given key (upsert).
	Put(key string, value []byte) (*SecureKvResponse, error)

	// Get returns the plaintext stored under the given key.
	Get(key string) (*SecureKvGetResponse, error)

	// Delete removes the value under the given key. No-op if missing.
	Delete(key string) (*SecureKvResponse, error)

	// Wipe deletes everything in the underlying native storage.
	Wipe() (*SecureKvResponse, error)
}
