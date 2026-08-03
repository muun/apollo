package security_cards_marketplace

type Marketplace struct {
	Providers []SecurityCardsProvider
	Specs     []SecurityCardSpec
}

func NewMarketplace(
	providers []SecurityCardsProvider,
	specs []SecurityCardSpec,
) *Marketplace {
	return &Marketplace{Providers: providers, Specs: specs}
}

type SecurityCardsProvider struct {
	Id                      string //nolint:staticcheck // TODO: struct field Id should be ID
	Name                    string
	Description             string
	SiteUrl                 string //nolint:staticcheck // TODO: struct field SiteUrl should be SiteURL
	LightTheme              ProviderTheme
	DarkTheme               ProviderTheme
	SecurityCards           []SecurityCard
	EstimatedShippingPrices []ShippingPrice
}

func NewSecurityCardsProvider(
	id string,
	name string,
	description string,
	siteUrl string, //nolint:staticcheck // TODO: func parameter siteUrl should be siteURL
	lightTheme ProviderTheme,
	darkTheme ProviderTheme,
	securityCards []SecurityCard,
	estimatedShippingPrices []ShippingPrice,
) SecurityCardsProvider {
	return SecurityCardsProvider{
		Id:                      id,
		Name:                    name,
		Description:             description,
		SiteUrl:                 siteUrl,
		LightTheme:              lightTheme,
		DarkTheme:               darkTheme,
		SecurityCards:           securityCards,
		EstimatedShippingPrices: estimatedShippingPrices,
	}
}

type ProviderTheme struct {
	PrimaryColor string
	SurfaceColor string
}

func NewProviderTheme(primaryColor, surfaceColor string) ProviderTheme {
	return ProviderTheme{
		PrimaryColor: primaryColor,
		SurfaceColor: surfaceColor,
	}
}

type SecurityCard struct {
	Id       string //nolint:staticcheck // TODO: struct field Id should be ID
	AssetUrl string //nolint:staticcheck // TODO: struct field AssetUrl should be AssetURL
	Tag      string
	SpecId   string //nolint:staticcheck // TODO: struct field SpecId should be SpecID
	CardCost Price
}

func NewSecurityCard(
	id string,
	assetUrl string, //nolint:staticcheck // TODO: func parameter assetUrl should be assetURL
	tag string,
	specId string, //nolint:staticcheck // TODO: func parameter specId should be specID
	cardCost Price,
) SecurityCard {
	return SecurityCard{
		Id:       id,
		AssetUrl: assetUrl,
		Tag:      tag,
		SpecId:   specId,
		CardCost: cardCost,
	}
}

type Price struct {
	CurrencyCode string
	Amount       string
}

func NewPrice(currencyCode, amount string) Price {
	return Price{CurrencyCode: currencyCode, Amount: amount}
}

type ShippingPrice struct {
	Price     Price
	Countries []Country
}

func NewShippingPrice(price Price, countries []Country) ShippingPrice {
	return ShippingPrice{Price: price, Countries: countries}
}

type Country struct {
	Code string
	Name string
	Flag string
}

func NewCountry(code, name, flag string) Country {
	return Country{Code: code, Name: name, Flag: flag}
}

type SecurityCardSpec struct {
	SpecId string //nolint:staticcheck // TODO: struct field SpecId should be SpecID
	Items  map[string][]SpecItem
}

func NewSecurityCardSpec(
	specId string, //nolint:staticcheck // TODO: func parameter specId should be specID
	items map[string][]SpecItem,
) SecurityCardSpec {
	return SecurityCardSpec{SpecId: specId, Items: items}
}

type SpecItem struct {
	IconUrl        string //nolint:staticcheck // TODO: struct field IconUrl should be IconURL
	Label          string
	Value          string
	AdditionalData string
}

func NewSpecItem(
	iconUrl string, //nolint:staticcheck // TODO: func parameter iconUrl should be iconURL
	label string,
	value string,
	additionalData string,
) SpecItem {
	return SpecItem{
		IconUrl:        iconUrl,
		Label:          label,
		Value:          value,
		AdditionalData: additionalData,
	}
}
