package newop

import (
	"fmt"
	"log"

	"github.com/btcsuite/btcutil"
	"github.com/shopspring/decimal"
)

// MonetaryAmount holds an amount of money in a certain currency
type MonetaryAmount struct {
	Value    decimal.Decimal
	Currency string
}

func NewMonetaryAmountFromSatoshis(value int64) *MonetaryAmount {
	v := decimal.NewFromInt(value).Div(decimal.NewFromInt(100_000_000))
	return &MonetaryAmount{
		Value:    v,
		Currency: "BTC",
	}
}

func NewMonetaryAmountFromFiat(value string, currency string) *MonetaryAmount {
	v, err := decimal.NewFromString(value)
	if err != nil {
		log.Printf("could not initialize monetary amount: %v", err)
		return nil
	}
	return &MonetaryAmount{
		Value:    v,
		Currency: currency,
	}
}

func (m *MonetaryAmount) ValueAsString() string {
	return m.Value.String()
}

func (m *MonetaryAmount) String() string {
	return fmt.Sprintf("%v %v", m.Value, m.Currency) // TODO(newop): this is just a stub implementation
}

func (m *MonetaryAmount) toBtc(window *ExchangeRateWindow) btcutil.Amount {
	rate := window.Rate(m.Currency)
	v := m.Value.Div(decimal.NewFromFloat(rate)).Mul(decimal.NewFromInt(100_000_000))
	v = v.RoundBank(0)
	return btcutil.Amount(v.IntPart())
}

func (m *MonetaryAmount) add(n *MonetaryAmount) *MonetaryAmount {
	if m.Currency != n.Currency {
		panic("currencies do not match") // TODO(newop): replace panic and bubble up errors?
	}
	return &MonetaryAmount{
		Value:    m.Value.Add(n.Value),
		Currency: m.Currency,
	}
}

func (m *MonetaryAmount) toBitcoinAmount(window *ExchangeRateWindow, primaryCurrency string) *BitcoinAmount {
	return &BitcoinAmount{
		InSat:             int64(m.toBtc(window)),
		InInputCurrency:   m,
		InPrimaryCurrency: window.convert(m, primaryCurrency),
	}
}

type BitcoinAmount struct {
	InSat             int64
	InPrimaryCurrency *MonetaryAmount
	InInputCurrency   *MonetaryAmount
}

func (a *BitcoinAmount) toBtc() btcutil.Amount {
	return btcutil.Amount(a.InSat)
}

func (a *BitcoinAmount) add(b *BitcoinAmount) *BitcoinAmount {
	return &BitcoinAmount{
		InSat:             a.InSat + b.InSat,
		InInputCurrency:   a.InInputCurrency.add(b.InInputCurrency),
		InPrimaryCurrency: a.InPrimaryCurrency.add(b.InPrimaryCurrency),
	}
}
