package model

// ProviderErrorKind classifies why a provider didn't answer a request, so the
// apps can tell the user what happened.
//
// It's an int enum, not a string-typed one, to match the other marketplace
// enums such as card.Material and card.SecureElement.
type ProviderErrorKind int

const (
	// ProviderErrorUnavailable means the provider couldn't be reached or
	// failed to answer.
	ProviderErrorUnavailable ProviderErrorKind = iota
	// ProviderErrorOutOfStock means the provider has no stock of the card.
	ProviderErrorOutOfStock
	// ProviderErrorDeprecatedCard means the provider no longer sells the card.
	ProviderErrorDeprecatedCard
	// ProviderErrorUnreachableCountry means the provider doesn't ship to the
	// country.
	ProviderErrorUnreachableCountry
)

func (k ProviderErrorKind) String() string {
	switch k {
	case ProviderErrorUnavailable:
		return "provider unavailable"
	case ProviderErrorOutOfStock:
		return "card out of stock"
	case ProviderErrorDeprecatedCard:
		return "card deprecated"
	case ProviderErrorUnreachableCountry:
		return "country unreachable"
	default:
		return "unknown provider error"
	}
}
