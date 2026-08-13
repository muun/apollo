package securekv

import (
	"context"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
)

type SecureKeyValueStorage interface {
	Put(ctx context.Context, key string, value []byte) error
	Get(ctx context.Context, key string) (*Secret, error)
	Delete(ctx context.Context, key string) error
	Wipe(ctx context.Context) error
}

type secureKeyValueStorage struct {
	bridge app_provided_data.SecureKeyValueStorage
}

func NewSecureKeyValueStorage(
	bridge app_provided_data.SecureKeyValueStorage,
) SecureKeyValueStorage {
	return &secureKeyValueStorage{bridge: bridge}
}

func (s *secureKeyValueStorage) Put(_ context.Context, key string, value []byte) error {
	if key == "" {
		return newStorageFailedError(errors.Errorf("key must not be empty"))
	}
	if value == nil {
		return newStorageFailedError(errors.Errorf("value must not be nil"))
	}
	resp, err := s.bridge.Put(key, value)
	if err != nil {
		return newStorageFailedError(err)
	}
	if resp == nil {
		return newStorageFailedError(errors.Errorf("bridge returned nil response"))
	}
	if resp.StatusCode != app_provided_data.SecureKvStatusOk {
		return newStorageFailedError(errors.Errorf("unexpected status: %d", resp.StatusCode))
	}
	return nil
}

// Get returns a capability handle; the bridge is queried lazily by WithSecret.
// Errors from the bridge surface there, not here.
func (s *secureKeyValueStorage) Get(_ context.Context, key string) (*Secret, error) {
	if key == "" {
		return nil, newStorageFailedError(errors.Errorf("key must not be empty"))
	}
	return NewSecret(key, s.bridge), nil
}

func (s *secureKeyValueStorage) Delete(_ context.Context, key string) error {
	if key == "" {
		return newStorageFailedError(errors.Errorf("key must not be empty"))
	}
	resp, err := s.bridge.Delete(key)
	if err != nil {
		return newStorageFailedError(err)
	}
	if resp == nil {
		return newStorageFailedError(errors.Errorf("bridge returned nil response"))
	}
	if resp.StatusCode != app_provided_data.SecureKvStatusOk {
		return newStorageFailedError(errors.Errorf("unexpected status: %d", resp.StatusCode))
	}
	return nil
}

func (s *secureKeyValueStorage) Wipe(_ context.Context) error {
	resp, err := s.bridge.Wipe()
	if err != nil {
		return newStorageFailedError(err)
	}
	if resp == nil {
		return newStorageFailedError(errors.Errorf("bridge returned nil response"))
	}
	if resp.StatusCode != app_provided_data.SecureKvStatusOk {
		return newStorageFailedError(errors.Errorf("unexpected status: %d", resp.StatusCode))
	}
	return nil
}
