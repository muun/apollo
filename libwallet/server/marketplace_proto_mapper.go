package server

import (
	goerr "github.com/go-errors/errors"

	"github.com/muun/libwallet/legacy/newop"
	"github.com/muun/libwallet/platform/preconditions"
	"github.com/muun/libwallet/server/api"
	marketplace_model "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/card"
	"github.com/muun/libwallet/subsystems/marketplace/presentation/model/provider"
)

func toProtoSCMProvider(in provider.Provider) *api.SCMProvider {
	return api.SCMProvider_builder{
		Uuid:    in.UUID,
		Name:    in.Name,
		SiteUrl: in.SiteURL,
		LightTheme: toProtoSCMProviderTheme(
			in.LightTheme,
		),
		DarkTheme: toProtoSCMProviderTheme(
			in.DarkTheme,
		),
	}.Build()
}

func toProtoSCMProviderTheme(in provider.Theme) *api.SCMProviderTheme {
	return api.SCMProviderTheme_builder{
		PrimaryColorHex: in.PrimaryColorHex,
		SurfaceColorHex: in.SurfaceColorHex,
	}.Build()
}

func toProtoSCMCards(in []card.Card) []*api.SCMCard {
	out := make([]*api.SCMCard, 0, len(in))
	for _, c := range in {
		out = append(out, toProtoSCMCard(c))
	}
	return out
}

func toProtoSCMCard(in card.Card) *api.SCMCard {
	return api.SCMCard_builder{
		Uuid:     in.UUID,
		Sku:      in.SKU,
		ImageUrl: in.ImageURL,
		HasStock: in.HasStock,
		Material: toProtoSCMCardMaterial(in.Material),
	}.Build()
}

func toProtoSCMCardMaterial(in card.Material) api.SCMCardMaterial {
	switch in {
	case card.MaterialPlastic:
		return api.SCMCardMaterial_PLASTIC
	case card.MaterialMetal:
		return api.SCMCardMaterial_METAL
	default:
		panic(goerr.Errorf("unknown card material: %d", in))
	}
}

func toProtoSCMSecureElement(in card.SecureElement) api.SCMSecureElement {
	switch in {
	case card.SecureElementEAL5:
		return api.SCMSecureElement_EAL_5
	case card.SecureElementEAL5Plus:
		return api.SCMSecureElement_EAL_5_PLUS
	case card.SecureElementEAL6:
		return api.SCMSecureElement_EAL_6
	case card.SecureElementEAL6Plus:
		return api.SCMSecureElement_EAL_6_PLUS
	default:
		panic(goerr.Errorf("unknown card secure element: %d", in))
	}
}

func toProtoSCMShippingOptions(
	in []marketplace_model.ShippingMethod,
) []*api.SCMShippingOption {
	out := make([]*api.SCMShippingOption, 0, len(in))
	for _, method := range in {
		out = append(out, api.SCMShippingOption_builder{
			Method: toProtoSCMShippingMethod(method),
		}.Build())
	}
	return out
}

func toProtoSCMShippingMethod(
	in marketplace_model.ShippingMethod,
) api.SCMShippingMethod {
	switch in {
	case marketplace_model.ShippingMethodMail:
		return api.SCMShippingMethod_MAIL
	case marketplace_model.ShippingMethodCourier:
		return api.SCMShippingMethod_COURIER
	default:
		preconditions.MissingCase(in)
		return 0
	}
}

func toMarketplaceErrorCode(in marketplace_model.ProviderErrorKind) errorCode {
	switch in {
	case marketplace_model.ProviderErrorUnavailable:
		return errorCodes.ErrMarketplaceProviderUnavailable
	case marketplace_model.ProviderErrorOutOfStock:
		return errorCodes.ErrMarketplaceCardOutOfStock
	case marketplace_model.ProviderErrorDeprecatedCard:
		return errorCodes.ErrMarketplaceCardDeprecated
	case marketplace_model.ProviderErrorUnreachableCountry:
		return errorCodes.ErrMarketplaceCountryUnreachable
	default:
		return errorCodes.ErrUnknown
	}
}

func toProtoSCMBitcoinAmount(
	in *newop.BitcoinAmount,
) *api.SCMBitcoinAmount {
	return api.SCMBitcoinAmount_builder{
		InSat: in.InSat,
		InInputCurrency: toProtoSCMMonetaryAmount(
			in.InInputCurrency,
		),
		InPrimaryCurrency: toProtoSCMMonetaryAmount(
			in.InPrimaryCurrency,
		),
	}.Build()
}

func toProtoSCMMonetaryAmount(
	in *newop.MonetaryAmount,
) *api.SCMMonetaryAmount {
	return api.SCMMonetaryAmount_builder{
		CurrencyCode: in.Currency,
		Amount:       in.ValueAsString(),
	}.Build()
}
