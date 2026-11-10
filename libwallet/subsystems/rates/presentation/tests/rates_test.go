package tests

import (
	"testing"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/emptypb"

	"github.com/muun/libwallet/platform/test/testsuite"
	"github.com/muun/libwallet/server/api"
	"github.com/muun/libwallet/subsystems/rates"
	"github.com/muun/libwallet/subsystems/rates/presentation"
	rates_model "github.com/muun/libwallet/subsystems/rates/presentation/model"
)

// exchangeRateWindowKey mirrors the KV key the subsystem persists the window to.
const exchangeRateWindowKey = "exchangeRateWindow"

func TestRates_Integration(t *testing.T) {
	testsuite.Run(t, new(RatesSuite))
}

// RatesSuite exercises the rates subsystem through its contract over a fresh KV store.
type RatesSuite struct {
	testsuite.BaseIntegrationSuite

	rates presentation.Rates
}

func (s *RatesSuite) SetupSuite() {
	s.BaseIntegrationSuite.SetupSuite()

	subsystem := rates.NewRates(s.KvStore)
	s.rates = &subsystem

	s.StartWalletServer(testsuite.WalletServerBuilder{
		Rates: subsystem,
	})
}

func (s *RatesSuite) TestLoadExchangeRateWindowReturnsNilWhenNoneStored() {
	window, err := s.rates.LoadExchangeRateWindow(s.Ctx)
	s.NoError(err)
	s.Nil(window)
}

func (s *RatesSuite) TestLoadExchangeRateWindowReturnsTheLastSavedWindow() {
	first := rates_model.NewExchangeRateWindow(
		1,
		1_720_000_000_000,
		map[string]float64{"USD": 32_000},
	)
	s.NoError(s.rates.SaveExchangeRateWindow(s.Ctx, first))
	second := rates_model.NewExchangeRateWindow(
		2,
		1_720_000_060_000,
		map[string]float64{"BTC": 1, "USD": 32_500},
	)
	s.NoError(s.rates.SaveExchangeRateWindow(s.Ctx, second))

	window, err := s.rates.LoadExchangeRateWindow(s.Ctx)
	s.NoError(err)
	s.NotNil(window)
	s.Equal(second, *window)
}

func (s *RatesSuite) TestSaveExchangeRateWindowRejectsInvalidWindow() {
	window := rates_model.NewExchangeRateWindow(
		0,
		1_720_000_000_000,
		map[string]float64{"USD": 32_000},
	)
	var invalidWindowErr *rates_model.InvalidExchangeRateWindowError
	s.ErrorAs(s.rates.SaveExchangeRateWindow(s.Ctx, window), &invalidWindowErr)

	stored, err := s.rates.LoadExchangeRateWindow(s.Ctx)
	s.NoError(err)
	s.Nil(stored)
}

func (s *RatesSuite) TestLoadExchangeRateWindowFailsOnInvalidStoredDocument() {
	documents := map[string]string{
		"unexpected shape": `[]`,
		"zero windowId":    `{"windowId":0,"fetchDateInMillis":1,"rates":{"BTC":1}}`,
	}

	for name, document := range documents {
		s.Run(name, func() {
			s.NoError(s.KvStore.Save(exchangeRateWindowKey, document))

			_, err := s.rates.LoadExchangeRateWindow(s.Ctx)
			s.Error(err)
		})
	}
}

// Falcon and Apollo already wrote this document through the generic KV API before libwallet
// owned the format, so it must keep loading.
func (s *RatesSuite) TestLoadExchangeRateWindowReadsDocumentWrittenByTheApps() {
	stored := `{"windowId":1234,"fetchDateInMillis":1720000000000,"rates":{"BTC":1,"USD":32000}}`
	s.NoError(s.KvStore.Save(exchangeRateWindowKey, stored))

	window, err := s.rates.LoadExchangeRateWindow(s.Ctx)
	s.NoError(err)
	s.NotNil(window)
	expected := rates_model.NewExchangeRateWindow(
		1_234,
		1_720_000_000_000,
		map[string]float64{"BTC": 1, "USD": 32_000},
	)
	s.Equal(expected, *window)
}

// Adding optional fields to the document later must not break existing readers.
func (s *RatesSuite) TestLoadExchangeRateWindowIgnoresUnknownFields() {
	stored := `{"windowId":42,"fetchDateInMillis":1720000000000,"rates":{"USD":32000},"future":1}`
	s.NoError(s.KvStore.Save(exchangeRateWindowKey, stored))

	window, err := s.rates.LoadExchangeRateWindow(s.Ctx)
	s.NoError(err)
	s.NotNil(window)
	expected := rates_model.NewExchangeRateWindow(
		42,
		1_720_000_000_000,
		map[string]float64{"USD": 32_000},
	)
	s.Equal(expected, *window)
}

// App versions that predate the dedicated RPCs read this document through the generic KV API
// after a downgrade, so saving must keep writing the same shape.
func (s *RatesSuite) TestSaveExchangeRateWindowWritesTheDocumentTheAppsRead() {
	window := rates_model.NewExchangeRateWindow(
		1_234,
		1_720_000_000_000,
		map[string]float64{"BTC": 1, "USD": 32_000},
	)
	s.NoError(s.rates.SaveExchangeRateWindow(s.Ctx, window))

	stored, err := s.KvStore.Get(exchangeRateWindowKey)
	s.NoError(err)
	s.IsType("", stored)
	s.JSONEq(
		`{"windowId":1234,"fetchDateInMillis":1720000000000,"rates":{"BTC":1,"USD":32000}}`,
		stored.(string),
	)
}

func (s *RatesSuite) TestGetExchangeRateWindowLeavesWindowUnsetWhenNoneStored() {
	response, err := s.Client.GetExchangeRateWindow(s.Ctx, &emptypb.Empty{})
	s.NoError(err)
	s.False(response.HasWindow())
}

func (s *RatesSuite) TestGetExchangeRateWindowReturnsTheSavedWindow() {
	window := api.ExchangeRateWindow_builder{
		WindowId:          1234,
		FetchDateInMillis: 1_720_000_000_000,
		Rates:             map[string]float64{"BTC": 1, "USD": 32_000},
	}.Build()
	_, err := s.Client.SaveExchangeRateWindow(
		s.Ctx,
		api.SaveExchangeRateWindowRequest_builder{Window: window}.Build(),
	)
	s.NoError(err)

	response, err := s.Client.GetExchangeRateWindow(s.Ctx, &emptypb.Empty{})
	s.NoError(err)
	s.True(response.HasWindow())
	s.Equal(int64(1234), response.GetWindow().GetWindowId())
	s.Equal(int64(1_720_000_000_000), response.GetWindow().GetFetchDateInMillis())
	s.Equal(map[string]float64{"BTC": 1, "USD": 32_000}, response.GetWindow().GetRates())
}

func (s *RatesSuite) TestSaveExchangeRateWindowFailsWithoutAWindow() {
	_, err := s.Client.SaveExchangeRateWindow(
		s.Ctx,
		api.SaveExchangeRateWindowRequest_builder{}.Build(),
	)

	grpcStatus := status.Convert(err)
	s.Equal(codes.InvalidArgument, grpcStatus.Code())
	s.Len(grpcStatus.Details(), 1)
	detail, ok := grpcStatus.Details()[0].(*api.ErrorDetail)
	s.True(ok)
	s.Equal(api.ErrorType_CLIENT, detail.GetType())
	s.Equal(int64(14_200), detail.GetCode())
}
