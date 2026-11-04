package securekv_test

import (
	"testing"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
	app_provided_data_mock "github.com/muun/libwallet/app_provided_data/mock"
	"github.com/muun/libwallet/base/data/securekv"
)

func TestSecureKeyValueStore(t *testing.T) {
	ctx := t.Context()

	t.Run("status classification via WithSecret", func(t *testing.T) {
		testCases := []struct {
			desc   string
			status int32
			check  func(error) bool
		}{
			{
				"StatusNotFound wraps as NotFoundError",
				app_provided_data.SecureKvStatusNotFound,
				func(err error) bool {
					var target *securekv.NotFoundError
					return errors.As(err, &target)
				},
			},
			{
				"StatusDecryptionFailed wraps as DecryptionFailedError",
				app_provided_data.SecureKvStatusDecryptionFailed,
				func(err error) bool {
					var target *securekv.DecryptionFailedError
					return errors.As(err, &target)
				},
			},
			{
				"StatusStorageFailed wraps as StorageFailedError",
				app_provided_data.SecureKvStatusStorageFailed,
				func(err error) bool {
					var target *securekv.StorageFailedError
					return errors.As(err, &target)
				},
			},
		}
		for _, tc := range testCases {
			t.Run(tc.desc, func(t *testing.T) {
				bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
				bridge.EXPECT().Get("key").
					Return(&app_provided_data.SecureKvGetResponse{StatusCode: tc.status}, nil)
				store := securekv.NewSecureKeyValueStore(bridge)

				secret, err := store.Get(ctx, "key")
				if err != nil {
					t.Fatalf("Get() error = %v, lazy Get must not fail", err)
				}
				err = secret.WithSecret(func(_ []byte) error {
					t.Fatal("fn should not be called when status is not Ok")
					return nil
				})
				if err == nil {
					t.Fatal("expected error from WithSecret")
				}
				if !tc.check(err) {
					t.Fatalf("error type check failed: %v", err)
				}
			})
		}
	})

	t.Run("transport error wraps as StorageFailedError", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Get("key").Return(nil, errors.New("gomobile call failed"))
		store := securekv.NewSecureKeyValueStore(bridge)

		secret, err := store.Get(ctx, "key")
		if err != nil {
			t.Fatalf("Get() error = %v, lazy Get must not fail", err)
		}
		err = secret.WithSecret(func(_ []byte) error {
			t.Fatal("fn should not be called on transport error")
			return nil
		})
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Get is lazy: no native call until WithSecret", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		// No expectations set: any bridge.Get call fails the test.
		store := securekv.NewSecureKeyValueStore(bridge)

		_, err := store.Get(ctx, "key")
		if err != nil {
			t.Fatalf("Get() error = %v, should not call bridge", err)
		}
	})

	t.Run("Get rejects empty key", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		store := securekv.NewSecureKeyValueStore(bridge)

		_, err := store.Get(ctx, "")
		if err == nil {
			t.Fatal("expected error for empty key")
		}
	})

	t.Run("Put rejects empty key", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Put(ctx, "", []byte("value"))
		if err == nil {
			t.Fatal("expected error for empty key")
		}
	})

	t.Run("Delete rejects empty key", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Delete(ctx, "")
		if err == nil {
			t.Fatal("expected error for empty key")
		}
	})

	t.Run("Put rejects nil value", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Put(ctx, "key", nil)
		if err == nil {
			t.Fatal("expected error for nil value")
		}
	})

	t.Run("Put rejects empty value", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Put(ctx, "key", []byte{})
		if err == nil {
			t.Fatal("expected error for empty value")
		}
	})

	t.Run("Put surfaces typed error when bridge returns non-Ok status", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Put("key", []byte("value")).
			Return(statusResp(app_provided_data.SecureKvStatusStorageFailed), nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Put(ctx, "key", []byte("value"))
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Delete returns nil when bridge reports Ok", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Delete("key").
			Return(statusResp(app_provided_data.SecureKvStatusOk), nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		if err := store.Delete(ctx, "key"); err != nil {
			t.Fatalf("Delete() error = %v, want nil on Ok status", err)
		}
	})

	t.Run("Delete surfaces typed error when bridge returns non-Ok status", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Delete("key").
			Return(statusResp(app_provided_data.SecureKvStatusStorageFailed), nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Delete(ctx, "key")
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Wipe returns nil when bridge reports Ok", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Wipe().
			Return(statusResp(app_provided_data.SecureKvStatusOk), nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		if err := store.Wipe(ctx); err != nil {
			t.Fatalf("Wipe() error = %v, want nil on Ok status", err)
		}
	})

	t.Run("Wipe surfaces typed error when bridge returns non-Ok status", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Wipe().
			Return(statusResp(app_provided_data.SecureKvStatusStorageFailed), nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Wipe(ctx)
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Unknown status surfaces as StorageFailedError", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		// StatusCode left at zero value (SecureKvStatusUnknown) on purpose.
		bridge.EXPECT().Put("key", []byte("value")).
			Return(&app_provided_data.SecureKvResponse{}, nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Put(ctx, "key", []byte("value"))
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Put surfaces StorageFailedError when bridge returns nil response", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Put("key", []byte("value")).Return(nil, nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Put(ctx, "key", []byte("value"))
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Delete surfaces StorageFailedError when bridge returns nil response",
		func(t *testing.T) {
			bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
			bridge.EXPECT().Delete("key").Return(nil, nil)
			store := securekv.NewSecureKeyValueStore(bridge)

			err := store.Delete(ctx, "key")
			var target *securekv.StorageFailedError
			if !errors.As(err, &target) {
				t.Fatalf("expected StorageFailedError, got %v", err)
			}
		})

	t.Run("Wipe surfaces StorageFailedError when bridge returns nil response", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Wipe().Return(nil, nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		err := store.Wipe(ctx)
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})
}

func TestWithSecret(t *testing.T) {
	ctx := t.Context()

	t.Run("invokes fn with plaintext fetched from bridge", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Get("key").Return(okResp([]byte("plaintext")), nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		secret, _ := store.Get(ctx, "key")
		var got []byte

		err := secret.WithSecret(func(b []byte) error {
			got = append([]byte(nil), b...)
			return nil
		})

		if err != nil {
			t.Fatalf("WithSecret() error = %v", err)
		}
		if string(got) != "plaintext" {
			t.Fatalf("fn received %q, want %q", got, "plaintext")
		}
	})

	t.Run("clears the buffer on success", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Get("key").Return(okResp([]byte("plaintext")), nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		secret, _ := store.Get(ctx, "key")
		var captured []byte

		_ = secret.WithSecret(func(b []byte) error {
			captured = b
			return nil
		})

		assertZeroed(t, captured)
	})

	t.Run("clears the buffer on error", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Get("key").Return(okResp([]byte("plaintext")), nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		secret, _ := store.Get(ctx, "key")
		boom := errors.New("boom")
		var captured []byte

		err := secret.WithSecret(func(b []byte) error {
			captured = b
			return boom
		})

		if !errors.Is(err, boom) {
			t.Fatalf("expected boom, got %v", err)
		}
		assertZeroed(t, captured)
	})

	t.Run("clears the buffer on panic", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Get("key").Return(okResp([]byte("plaintext")), nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		secret, _ := store.Get(ctx, "key")
		var captured []byte

		func() {
			defer func() { _ = recover() }()
			_ = secret.WithSecret(func(b []byte) error {
				captured = b
				panic("boom")
			})
		}()

		assertZeroed(t, captured)
	})

	t.Run("clears the buffer when bridge returns non-Ok status with bytes", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		// Contract violation: non-Ok status carrying plaintext. WithSecret
		// must still wipe the buffer defensively.
		getResp := &app_provided_data.SecureKvGetResponse{
			Value:      []byte("leaked-plaintext"),
			StatusCode: app_provided_data.SecureKvStatusNotFound,
		}
		bridge.EXPECT().Get("key").Return(getResp, nil)
		store := securekv.NewSecureKeyValueStore(bridge)

		secret, _ := store.Get(ctx, "key")
		err := secret.WithSecret(func(_ []byte) error {
			t.Fatal("fn should not be called when status is not Ok")
			return nil
		})

		var target *securekv.NotFoundError
		if !errors.As(err, &target) {
			t.Fatalf("expected NotFoundError, got %v", err)
		}
		assertZeroed(t, getResp.Value)
	})

	t.Run("WithSecret surfaces StorageFailedError when bridge returns nil response",
		func(t *testing.T) {
			bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
			bridge.EXPECT().Get("key").Return(nil, nil)
			store := securekv.NewSecureKeyValueStore(bridge)

			secret, _ := store.Get(ctx, "key")
			err := secret.WithSecret(func(_ []byte) error {
				t.Fatal("fn should not be called on nil response")
				return nil
			})
			var target *securekv.StorageFailedError
			if !errors.As(err, &target) {
				t.Fatalf("expected StorageFailedError, got %v", err)
			}
		})

	t.Run("Ok status with empty value surfaces StorageFailedError", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		bridge.EXPECT().Get("key").Return(okResp([]byte{}), nil)
		storage := securekv.NewSecureKeyValueStore(bridge)

		secret, _ := storage.Get(ctx, "key")
		err := secret.WithSecret(func(_ []byte) error {
			t.Fatal("fn should not be called on empty value")
			return nil
		})
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("multiple WithSecret on the same Secret fetch fresh each time", func(t *testing.T) {
		bridge := app_provided_data_mock.NewMockSecureKeyValueStore(t)
		// Fresh response each call, so WithSecret's wipe does not affect later calls.
		bridge.EXPECT().Get("key").
			RunAndReturn(func(_ string) (*app_provided_data.SecureKvGetResponse, error) {
				return okResp([]byte("plaintext")), nil
			}).
			Times(3)
		store := securekv.NewSecureKeyValueStore(bridge)

		secret, _ := store.Get(ctx, "key")

		for i := range 3 {
			var got []byte
			err := secret.WithSecret(func(b []byte) error {
				got = append([]byte(nil), b...)
				return nil
			})
			if err != nil {
				t.Fatalf("iteration %d: WithSecret error = %v", i, err)
			}
			if string(got) != "plaintext" {
				t.Fatalf("iteration %d: got %q, want plaintext", i, got)
			}
		}
	})
}

func okResp(value []byte) *app_provided_data.SecureKvGetResponse {
	return &app_provided_data.SecureKvGetResponse{
		Value:      value,
		StatusCode: app_provided_data.SecureKvStatusOk,
	}
}

func statusResp(status int32) *app_provided_data.SecureKvResponse {
	return &app_provided_data.SecureKvResponse{StatusCode: status}
}

func assertZeroed(t *testing.T, buf []byte) {
	t.Helper()
	if len(buf) == 0 {
		t.Fatal("captured buffer is empty; nothing to verify")
	}
	for i, b := range buf {
		if b != 0 {
			t.Fatalf("byte %d not zeroed: got %x", i, b)
		}
	}
}
