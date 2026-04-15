package security_cards_marketplace

type Marketplace struct {
	Providers []SecurityCardsProvider
}

type SecurityCardsProvider struct {
	Name          string
	SecurityCards []SecurityCard
	CurrencyCode  string
	ColorHex      string
	Material      string
	Price         float64
	ShippingCost  float64
}

type SecurityCard struct {
	Image string
	Stock int32
}
