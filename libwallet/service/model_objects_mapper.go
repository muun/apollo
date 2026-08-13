package service

import (
	"encoding/hex"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/domain/model/security_card"
	"github.com/muun/libwallet/domain/model/security_cards_marketplace"
	"github.com/muun/libwallet/service/model"
)

func MapSecurityCardPaired(in model.RegisterSecurityCardOkJson) *security_card.SecurityCardPaired {
	return security_card.NewSecurityCardPaired(
		mapSecurityCardMetadata(in.Metadata),
		in.IsKnownProvider,
		in.IsCardAlreadyUsed,
	)
}

func mapSecurityCardMetadata(
	in model.SecurityCardMetadataJson,
) *security_card.SecurityCardMetadata {
	return security_card.NewSecurityCardMetadata(
		in.GlobalPublicKeyInHex,
		in.CardVendorInHex,
		in.CardModelInHex,
		in.FirmwareVersion,
		in.UsageCount,
		in.LanguageCodeInHex,
	)
}

func MapSecurityCardSignChallengeResponse(
	in model.ChallengeSecurityCardSignResponseJson,
) (*security_card.SecurityCardSignChallenge, error) {

	serverPublicKeyBytes, err := hex.DecodeString(in.ServerPublicKeyInHex)
	if err != nil {
		return nil, errors.Errorf("error decoding server public key: %w", err)
	}

	macBytes, err := hex.DecodeString(in.MacInHex)
	if err != nil {
		return nil, errors.Errorf("error decoding mac: %w", err)
	}

	return security_card.NewSecurityCardSignChallenge(
		serverPublicKeyBytes,
		macBytes,
		in.CardUsageCount,
		in.PairingSlot,
	), nil
}

func MapSecurityCardsMarketplace(
	in model.SecurityCardsMarketplaceJson,
) (*security_cards_marketplace.Marketplace, error) {

	providers := make([]security_cards_marketplace.SecurityCardsProvider, 0, len(in.Providers))
	for _, provider := range in.Providers {

		securityCards := make(
			[]security_cards_marketplace.SecurityCard,
			0,
			len(provider.SecurityCards),
		)
		for _, sc := range provider.SecurityCards {
			securityCards = append(securityCards, security_cards_marketplace.NewSecurityCard(
				sc.Id,
				sc.AssetUrl,
				sc.Tag,
				sc.SpecId,
				mapPriceInfo(sc.CardCost),
			))
		}

		shippingPrices := make(
			[]security_cards_marketplace.ShippingPrice,
			0,
			len(provider.EstimatedShippingPrices),
		)
		for _, shippingPrice := range provider.EstimatedShippingPrices {

			countries := make([]security_cards_marketplace.Country, 0, len(shippingPrice.Countries))
			for _, country := range shippingPrice.Countries {
				countries = append(countries, security_cards_marketplace.NewCountry(
					country.Code,
					country.Name,
					country.Flag,
				))
			}

			shippingPrices = append(shippingPrices, security_cards_marketplace.NewShippingPrice(
				mapPriceInfo(shippingPrice.Price),
				countries,
			))
		}

		providers = append(providers, security_cards_marketplace.NewSecurityCardsProvider(
			provider.Id,
			provider.Name,
			provider.Description,
			provider.SiteUrl,
			mapProviderTheme(provider.LightTheme),
			mapProviderTheme(provider.DarkTheme),
			securityCards,
			shippingPrices,
		))
	}

	specs := make([]security_cards_marketplace.SecurityCardSpec, 0, len(in.Specs))
	for _, spec := range in.Specs {

		items := make(map[string][]security_cards_marketplace.SpecItem, len(spec.Items))
		for category, specItems := range spec.Items {
			mapped := make([]security_cards_marketplace.SpecItem, 0, len(specItems))
			for _, item := range specItems {
				mapped = append(mapped, security_cards_marketplace.NewSpecItem(
					item.IconUrl,
					item.Label,
					item.Value,
					item.AdditionalData,
				))
			}
			items[category] = mapped
		}

		specs = append(specs, security_cards_marketplace.NewSecurityCardSpec(
			spec.SpecId,
			items,
		))
	}

	return security_cards_marketplace.NewMarketplace(providers, specs), nil
}

func mapPriceInfo(in model.PriceInfoJson) security_cards_marketplace.Price {
	return security_cards_marketplace.NewPrice(in.CurrencyCode, in.Amount)
}

func mapProviderTheme(in model.ProviderThemeJson) security_cards_marketplace.ProviderTheme {
	return security_cards_marketplace.NewProviderTheme(in.PrimaryColor, in.SurfaceColor)
}
