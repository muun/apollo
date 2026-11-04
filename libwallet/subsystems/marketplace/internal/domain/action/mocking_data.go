package action

// hardcodedRates maps a currency code to the price of one BTC in that
// currency, backing the fiat to sats conversion of marketplace prices.
// TODO: replace with the ExchangeRateWindow served by
// ExchangeRateWindowRepository.
var hardcodedRates = map[string]float64{
	"ARS": 145_000_000,
	"EUR": 92_000,
	"USD": 100_000,
}

// hardcodedPrimaryCurrency is the user's primary currency, in which every
// marketplace price is also expressed.
// TODO: it currently lives on the native side; replace once it moves into
// libwallet (as the exchange rates are).
const hardcodedPrimaryCurrency = "USD"
