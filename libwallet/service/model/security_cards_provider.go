package model

type SecurityCardsProviderJson struct {
	Name          string             `json:"name"`
	SecurityCards []SecurityCardJson `json:"securityCards"`
	CurrencyCode  string             `json:"currencyCode"`
	ColorHex      string             `json:"colorHex"`
	Material      string             `json:"material"`
	Price         float64            `json:"price"`
	ShippingCost  float64            `json:"shippingCost"`
}
