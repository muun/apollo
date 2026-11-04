package test

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/subsystems/rates/internal/domain/model"
)

func TestExchangeRateWindow_InvalidArgumentsFail(t *testing.T) {
	validRates := map[string]float64{"USD": 114_521.55}

	cases := map[string]func() error{
		"zero windowId": func() error {
			return model.ValidateExchangeRateWindow(0, 1_754_424_000_000, validRates)
		},
		"negative windowId": func() error {
			return model.ValidateExchangeRateWindow(-1, 1_754_424_000_000, validRates)
		},
		"zero fetchDateInMillis": func() error {
			return model.ValidateExchangeRateWindow(123_456, 0, validRates)
		},
		"negative fetchDateInMillis": func() error {
			return model.ValidateExchangeRateWindow(123_456, -1, validRates)
		},
		"nil rates": func() error {
			return model.ValidateExchangeRateWindow(123_456, 1_754_424_000_000, nil)
		},
		"empty rates": func() error {
			return model.ValidateExchangeRateWindow(
				123_456,
				1_754_424_000_000,
				map[string]float64{},
			)
		},
	}

	for name, validate := range cases {
		t.Run(name, func(t *testing.T) {
			require.Error(t, validate())
		})
	}
}

func TestExchangeRateWindow_InvalidArgumentsPanic(t *testing.T) {
	require.Panics(t, func() {
		model.NewExchangeRateWindow(0, 1_754_424_000_000, map[string]float64{"USD": 114_521.55})
	})
}
