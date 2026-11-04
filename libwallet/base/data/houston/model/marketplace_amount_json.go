package model

// MarketplaceAmountJSON is a fiat amount: a decimal string expressed in
// major units of the given currency, e.g. {"currencyCode": "EUR",
// "amount": "200.00"} is two hundred euros. Each amount is independently
// denominated — amounts within one provider may use different currencies,
// and clients must convert before combining them.
type MarketplaceAmountJSON struct {
	CurrencyCode string `json:"currencyCode"`
	Amount       string `json:"amount"`
}
