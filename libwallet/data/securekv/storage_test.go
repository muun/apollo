package securekv_test

import (
	"testing"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/data/securekv"
)

type fakeBridge struct {
	getResp         *app_provided_data.SecureKvGetResponse
	getErr          error
	getNilResp      bool
	lastGetReturned *app_provided_data.SecureKvGetResponse

	putStatus     int32
	putErr        error
	putNilResp    bool
	deleteStatus  int32
	deleteErr     error
	deleteNilResp bool
	wipeStatus    int32
	wipeErr       error
	wipeNilResp   bool
}

func newFakeBridge() *fakeBridge {
	return &fakeBridge{}
}

func (b *fakeBridge) Put(_ string, _ []byte) (*app_provided_data.SecureKvResponse, error) {
	if b.putNilResp {
		return nil, b.putErr
	}
	return &app_provided_data.SecureKvResponse{StatusCode: b.putStatus}, b.putErr
}

func (b *fakeBridge) Get(_ string) (*app_provided_data.SecureKvGetResponse, error) {
	if b.getErr != nil {
		return nil, b.getErr
	}
	if b.getNilResp {
		return nil, nil
	}
	if b.getResp == nil {
		panic("fakeBridge.Get: no preset getResp, getErr, or getNilResp; tests must set one")
	}
	// Fresh copy each call so WithSecret's wipe does not affect later calls.
	resp := &app_provided_data.SecureKvGetResponse{
		Value:      append([]byte(nil), b.getResp.Value...),
		StatusCode: b.getResp.StatusCode,
	}
	b.lastGetReturned = resp
	return resp, nil
}

func (b *fakeBridge) Delete(_ string) (*app_provided_data.SecureKvResponse, error) {
	if b.deleteNilResp {
		return nil, b.deleteErr
	}
	return &app_provided_data.SecureKvResponse{StatusCode: b.deleteStatus}, b.deleteErr
}

func (b *fakeBridge) Wipe() (*app_provided_data.SecureKvResponse, error) {
	if b.wipeNilResp {
		return nil, b.wipeErr
	}
	return &app_provided_data.SecureKvResponse{StatusCode: b.wipeStatus}, b.wipeErr
}

func TestSecureKeyValueStorage(t *testing.T) {
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
				bridge := newFakeBridge()
				bridge.getResp = &app_provided_data.SecureKvGetResponse{StatusCode: tc.status}
				storage := securekv.NewSecureKeyValueStorage(bridge)

				secret, err := storage.Get(ctx, "key")
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
		bridge := newFakeBridge()
		bridge.getErr = errors.New("gomobile call failed")
		storage := securekv.NewSecureKeyValueStorage(bridge)

		secret, err := storage.Get(ctx, "key")
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
		bridge := newFakeBridge()
		// Neither getResp nor getErr set: any bridge.Get call would panic.
		storage := securekv.NewSecureKeyValueStorage(bridge)

		_, err := storage.Get(ctx, "key")
		if err != nil {
			t.Fatalf("Get() error = %v, should not call bridge", err)
		}
	})

	t.Run("Get rejects empty key", func(t *testing.T) {
		bridge := newFakeBridge()
		storage := securekv.NewSecureKeyValueStorage(bridge)

		_, err := storage.Get(ctx, "")
		if err == nil {
			t.Fatal("expected error for empty key")
		}
	})

	t.Run("Put rejects empty key", func(t *testing.T) {
		bridge := newFakeBridge()
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Put(ctx, "", []byte("value"))
		if err == nil {
			t.Fatal("expected error for empty key")
		}
	})

	t.Run("Delete rejects empty key", func(t *testing.T) {
		bridge := newFakeBridge()
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Delete(ctx, "")
		if err == nil {
			t.Fatal("expected error for empty key")
		}
	})

	t.Run("Put rejects nil value", func(t *testing.T) {
		bridge := newFakeBridge()
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Put(ctx, "key", nil)
		if err == nil {
			t.Fatal("expected error for nil value")
		}
	})

	t.Run("Put allows empty byte slice", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.putStatus = app_provided_data.SecureKvStatusOk
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Put(ctx, "key", []byte{})
		if err != nil {
			t.Fatalf("Put() error = %v, empty slice should be allowed", err)
		}
	})

	t.Run("Put surfaces typed error when bridge returns non-Ok status", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.putStatus = app_provided_data.SecureKvStatusStorageFailed
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Put(ctx, "key", []byte("value"))
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Delete returns nil when bridge reports Ok", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.deleteStatus = app_provided_data.SecureKvStatusOk
		storage := securekv.NewSecureKeyValueStorage(bridge)

		if err := storage.Delete(ctx, "key"); err != nil {
			t.Fatalf("Delete() error = %v, want nil on Ok status", err)
		}
	})

	t.Run("Delete surfaces typed error when bridge returns non-Ok status", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.deleteStatus = app_provided_data.SecureKvStatusStorageFailed
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Delete(ctx, "key")
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Wipe returns nil when bridge reports Ok", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.wipeStatus = app_provided_data.SecureKvStatusOk
		storage := securekv.NewSecureKeyValueStorage(bridge)

		if err := storage.Wipe(ctx); err != nil {
			t.Fatalf("Wipe() error = %v, want nil on Ok status", err)
		}
	})

	t.Run("Wipe surfaces typed error when bridge returns non-Ok status", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.wipeStatus = app_provided_data.SecureKvStatusStorageFailed
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Wipe(ctx)
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Unknown status surfaces as StorageFailedError", func(t *testing.T) {
		bridge := newFakeBridge()
		// putStatus left at zero value (SecureKvStatusUnknown) on purpose.
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Put(ctx, "key", []byte("value"))
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Put surfaces StorageFailedError when bridge returns nil response", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.putNilResp = true
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Put(ctx, "key", []byte("value"))
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})

	t.Run("Delete surfaces StorageFailedError when bridge returns nil response",
		func(t *testing.T) {
			bridge := newFakeBridge()
			bridge.deleteNilResp = true
			storage := securekv.NewSecureKeyValueStorage(bridge)

			err := storage.Delete(ctx, "key")
			var target *securekv.StorageFailedError
			if !errors.As(err, &target) {
				t.Fatalf("expected StorageFailedError, got %v", err)
			}
		})

	t.Run("Wipe surfaces StorageFailedError when bridge returns nil response", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.wipeNilResp = true
		storage := securekv.NewSecureKeyValueStorage(bridge)

		err := storage.Wipe(ctx)
		var target *securekv.StorageFailedError
		if !errors.As(err, &target) {
			t.Fatalf("expected StorageFailedError, got %v", err)
		}
	})
}

func TestWithSecret(t *testing.T) {
	ctx := t.Context()

	t.Run("invokes fn with plaintext fetched from bridge", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.getResp = okResp([]byte("plaintext"))
		storage := securekv.NewSecureKeyValueStorage(bridge)

		secret, _ := storage.Get(ctx, "key")
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
		bridge := newFakeBridge()
		bridge.getResp = okResp([]byte("plaintext"))
		storage := securekv.NewSecureKeyValueStorage(bridge)

		secret, _ := storage.Get(ctx, "key")
		var captured []byte

		_ = secret.WithSecret(func(b []byte) error {
			captured = b
			return nil
		})

		assertZeroed(t, captured)
	})

	t.Run("clears the buffer on error", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.getResp = okResp([]byte("plaintext"))
		storage := securekv.NewSecureKeyValueStorage(bridge)

		secret, _ := storage.Get(ctx, "key")
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
		bridge := newFakeBridge()
		bridge.getResp = okResp([]byte("plaintext"))
		storage := securekv.NewSecureKeyValueStorage(bridge)

		secret, _ := storage.Get(ctx, "key")
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
		bridge := newFakeBridge()
		// Contract violation: non-Ok status carrying plaintext. WithSecret
		// must still wipe the buffer defensively.
		bridge.getResp = &app_provided_data.SecureKvGetResponse{
			Value:      []byte("leaked-plaintext"),
			StatusCode: app_provided_data.SecureKvStatusNotFound,
		}
		storage := securekv.NewSecureKeyValueStorage(bridge)

		secret, _ := storage.Get(ctx, "key")
		err := secret.WithSecret(func(_ []byte) error {
			t.Fatal("fn should not be called when status is not Ok")
			return nil
		})

		var target *securekv.NotFoundError
		if !errors.As(err, &target) {
			t.Fatalf("expected NotFoundError, got %v", err)
		}
		assertZeroed(t, bridge.lastGetReturned.Value)
	})

	t.Run("WithSecret surfaces StorageFailedError when bridge returns nil response",
		func(t *testing.T) {
			bridge := newFakeBridge()
			bridge.getNilResp = true
			storage := securekv.NewSecureKeyValueStorage(bridge)

			secret, _ := storage.Get(ctx, "key")
			err := secret.WithSecret(func(_ []byte) error {
				t.Fatal("fn should not be called on nil response")
				return nil
			})
			var target *securekv.StorageFailedError
			if !errors.As(err, &target) {
				t.Fatalf("expected StorageFailedError, got %v", err)
			}
		})

	t.Run("multiple WithSecret on the same Secret fetch fresh each time", func(t *testing.T) {
		bridge := newFakeBridge()
		bridge.getResp = okResp([]byte("plaintext"))
		storage := securekv.NewSecureKeyValueStorage(bridge)

		secret, _ := storage.Get(ctx, "key")

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
