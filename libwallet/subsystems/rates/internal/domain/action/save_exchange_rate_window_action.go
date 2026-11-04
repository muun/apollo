package action

import (
	"context"

	"github.com/muun/libwallet/subsystems/rates/internal/data/repository"
	"github.com/muun/libwallet/subsystems/rates/internal/domain/model"
	rates_model "github.com/muun/libwallet/subsystems/rates/presentation/model"
)

// SaveExchangeRateWindowAction stores an exchange rate window, replacing the previous one.
type SaveExchangeRateWindowAction interface {
	// Run stores the given window. It fails with InvalidExchangeRateWindowError when the window
	// has no rates or a non-positive id or fetch date.
	Run(ctx context.Context, window rates_model.ExchangeRateWindow) error
}

type saveExchangeRateWindowAction struct {
	windowRepository repository.ExchangeRateWindowRepository
}

// NewSaveExchangeRateWindowAction creates a SaveExchangeRateWindowAction backed by the given
// repository.
func NewSaveExchangeRateWindowAction(
	windowRepository repository.ExchangeRateWindowRepository,
) SaveExchangeRateWindowAction {
	return &saveExchangeRateWindowAction{windowRepository: windowRepository}
}

func (a *saveExchangeRateWindowAction) Run(
	ctx context.Context,
	window rates_model.ExchangeRateWindow,
) error {
	err := model.ValidateExchangeRateWindow(
		window.WindowID,
		window.FetchDateInMillis,
		window.Rates,
	)
	if err != nil {
		return rates_model.NewInvalidExchangeRateWindowError(err)
	}

	return a.windowRepository.Save(ctx, model.NewExchangeRateWindow(
		window.WindowID,
		window.FetchDateInMillis,
		window.Rates,
	))
}
