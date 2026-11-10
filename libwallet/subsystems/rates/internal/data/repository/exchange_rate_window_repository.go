// Package repository provides persistence for the rates subsystem.
package repository

import (
	"context"
	"encoding/json"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/subsystems/rates/internal/domain/model"
)

// keyExchangeRateWindow is defined in base/data/storage/kv_migrations.go.
const keyExchangeRateWindow = "exchangeRateWindow"

// ExchangeRateWindowRepository persists the exchange rate window under the exchangeRateWindow
// KV key.
type ExchangeRateWindowRepository interface {
	// Load returns the stored window, or nil when none has been stored yet.
	Load(ctx context.Context) (*model.ExchangeRateWindow, error)

	// Save stores the given window, replacing the previous one.
	Save(ctx context.Context, window *model.ExchangeRateWindow) error
}

// exchangeRateWindowRepository implements ExchangeRateWindowRepository on KeyValueStorage;
// callers never touch the raw KV API.
type exchangeRateWindowRepository struct {
	kv *storage.KeyValueStorage
}

// NewExchangeRateWindowRepository creates an ExchangeRateWindowRepository backed by the given
// key-value storage.
func NewExchangeRateWindowRepository(kv *storage.KeyValueStorage) ExchangeRateWindowRepository {
	return &exchangeRateWindowRepository{kv: kv}
}

func (r *exchangeRateWindowRepository) Load(
	ctx context.Context,
) (*model.ExchangeRateWindow, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	value, err := r.kv.Get(keyExchangeRateWindow)
	if err != nil {
		return nil, err
	}

	if value == nil {
		return nil, nil
	}

	str, ok := value.(string)
	if !ok {
		return nil, errors.Errorf("unexpected type for %s: %T", keyExchangeRateWindow, value)
	}

	var document storedExchangeRateWindow
	if err := json.Unmarshal([]byte(str), &document); err != nil {
		return nil, errors.Errorf("unmarshalling %s: %w", keyExchangeRateWindow, err)
	}

	err = model.ValidateExchangeRateWindow(
		document.WindowID,
		document.FetchDateInMillis,
		document.Rates,
	)
	if err != nil {
		return nil, errors.Errorf("stored %s is invalid: %w", keyExchangeRateWindow, err)
	}

	return model.NewExchangeRateWindow(
		document.WindowID,
		document.FetchDateInMillis,
		document.Rates,
	), nil
}

func (r *exchangeRateWindowRepository) Save(
	ctx context.Context,
	window *model.ExchangeRateWindow,
) error {
	if err := ctx.Err(); err != nil {
		return err
	}

	document, err := json.Marshal(storedExchangeRateWindow{
		WindowID:          window.WindowID,
		FetchDateInMillis: window.FetchDateInMillis,
		Rates:             window.Rates,
	})
	if err != nil {
		return errors.Errorf("marshalling %s: %w", keyExchangeRateWindow, err)
	}

	return r.kv.Save(keyExchangeRateWindow, string(document))
}

// storedExchangeRateWindow is the document stored under the exchangeRateWindow KV key. App
// versions that predate the rates RPCs read it directly, so its shape must not change.
type storedExchangeRateWindow struct {
	WindowID          int64              `json:"windowId"`
	FetchDateInMillis int64              `json:"fetchDateInMillis"`
	Rates             map[string]float64 `json:"rates"`
}
