package securekv

import (
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
)

type (
	NotFoundError         struct{ error }
	DecryptionFailedError struct{ error }
	StorageFailedError    struct{ error }
)

func newNotFoundError(err error) error {
	return &NotFoundError{
		errors.Errorf("secure key-value storage: not found: %w", err),
	}
}

func newDecryptionFailedError(err error) error {
	return &DecryptionFailedError{
		errors.Errorf("secure key-value storage: decryption failed: %w", err),
	}
}

func newStorageFailedError(err error) error {
	return &StorageFailedError{
		errors.Errorf("secure key-value storage: storage failed: %w", err),
	}
}

// errorFromGetStatus maps a non-Ok Get status to its typed error. Caller must
// branch on Ok before invoking; Ok is treated as unexpected here.
func errorFromGetStatus(status int32) error {
	switch status {
	case app_provided_data.SecureKvStatusNotFound:
		return newNotFoundError(errors.New("key missing"))
	case app_provided_data.SecureKvStatusDecryptionFailed:
		return newDecryptionFailedError(errors.New("key invalidated"))
	case app_provided_data.SecureKvStatusStorageFailed:
		return newStorageFailedError(errors.New("operation failed"))
	default:
		return newStorageFailedError(errors.Errorf("unexpected status: %d", status))
	}
}
