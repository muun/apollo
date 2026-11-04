package action

import (
	"context"

	"github.com/muun/libwallet/subsystems/rates/internal/data/repository"
	rates_model "github.com/muun/libwallet/subsystems/rates/presentation/model"
)

// LoadExchangeRateWindowAction reads the stored exchange rate window.
type LoadExchangeRateWindowAction interface {
	// Run returns the stored window, or nil when none has been stored yet.
	Run(ctx context.Context) (*rates_model.ExchangeRateWindow, error)
}

type loadExchangeRateWindowAction struct {
	windowRepository repository.ExchangeRateWindowRepository
}

// NewLoadExchangeRateWindowAction creates a LoadExchangeRateWindowAction backed by the given
// repository.
func NewLoadExchangeRateWindowAction(
	windowRepository repository.ExchangeRateWindowRepository,
) LoadExchangeRateWindowAction {
	return &loadExchangeRateWindowAction{windowRepository: windowRepository}
}

func (a *loadExchangeRateWindowAction) Run(
	ctx context.Context,
) (*rates_model.ExchangeRateWindow, error) {
	window, err := a.windowRepository.Load(ctx)
	if err != nil {
		return nil, err
	}
	if window == nil {
		return nil, nil
	}

	converted := rates_model.NewExchangeRateWindow(
		window.WindowID,
		window.FetchDateInMillis,
		window.Rates,
	)
	return &converted, nil
}
