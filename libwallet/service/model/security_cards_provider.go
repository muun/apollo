package model

type SecurityCardsProviderJson struct { //nolint:staticcheck // TODO: type SecurityCardsProviderJson should be SecurityCardsProviderJSON
	Id                      string                  `json:"id"` //nolint:staticcheck // TODO: struct field Id should be ID
	Name                    string                  `json:"name"`
	Description             string                  `json:"description"`
	SiteUrl                 string                  `json:"siteUrl"` //nolint:staticcheck // TODO: struct field SiteUrl should be SiteURL
	LightTheme              ProviderThemeJson       `json:"lightTheme"`
	DarkTheme               ProviderThemeJson       `json:"darkTheme"`
	SecurityCards           []SecurityCardJson      `json:"securityCards"`
	EstimatedShippingPrices []ShippingPriceInfoJson `json:"estimatedShippingPrices"`
}

type ProviderThemeJson struct { //nolint:staticcheck // TODO: type ProviderThemeJson should be ProviderThemeJSON
	PrimaryColor string `json:"primaryColor"`
	SurfaceColor string `json:"surfaceColor"`
}

type ShippingPriceInfoJson struct { //nolint:staticcheck // TODO: type ShippingPriceInfoJson should be ShippingPriceInfoJSON
	Price     PriceInfoJson     `json:"price"`
	Countries []CountryInfoJson `json:"countries"`
}

type PriceInfoJson struct { //nolint:staticcheck // TODO: type PriceInfoJson should be PriceInfoJSON
	CurrencyCode string `json:"currencyCode"`
	Amount       string `json:"amount"`
}

type CountryInfoJson struct { //nolint:staticcheck // TODO: type CountryInfoJson should be CountryInfoJSON
	Code string `json:"code"`
	Name string `json:"name"`
	Flag string `json:"flag"`
}
