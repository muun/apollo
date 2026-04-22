package service

import (
	"encoding/hex"
	"fmt"

	"github.com/muun/libwallet/domain/model/security_card"
	"github.com/muun/libwallet/domain/model/security_cards_marketplace"
	"github.com/muun/libwallet/service/model"
)

func MapSecurityCardPaired(in model.RegisterSecurityCardOkJson) *security_card.SecurityCardPaired {
	return &security_card.SecurityCardPaired{
		Metadata:          mapSecurityCardMetadata(in.Metadata),
		IsKnownProvider:   in.IsKnownProvider,
		IsCardAlreadyUsed: in.IsCardAlreadyUsed,
	}
}

func mapSecurityCardMetadata(in model.SecurityCardMetadataJson) *security_card.SecurityCardMetadata {
	return &security_card.SecurityCardMetadata{
		GlobalPublicKeyInHex: in.GlobalPublicKeyInHex,
		CardVendorInHex:      in.CardVendorInHex,
		CardModelInHex:       in.CardModelInHex,
		FirmwareVersion:      in.FirmwareVersion,
		UsageCount:           in.UsageCount,
		LanguageCodeInHex:    in.LanguageCodeInHex,
	}
}

func MapSecurityCardSignChallengeResponse(
	in model.ChallengeSecurityCardSignResponseJson,
) (*security_card.SecurityCardSignChallenge, error) {

	serverPublicKeyBytes, err := hex.DecodeString(in.ServerPublicKeyInHex)
	if err != nil {
		return nil, fmt.Errorf("error decoding server public key: %w", err)
	}

	macBytes, err := hex.DecodeString(in.MacInHex)
	if err != nil {
		return nil, fmt.Errorf("error decoding mac: %w", err)
	}

	return &security_card.SecurityCardSignChallenge{
		ServerPublicKey: serverPublicKeyBytes,
		Mac:             macBytes,
		PairingSlot:     in.PairingSlot,
		CardUsageCount:  in.CardUsageCount,
	}, nil
}

func MapSecurityCardsMarketplace(
	in model.SecurityCardsMarketplaceJson,
) (*security_cards_marketplace.Marketplace, error) {

	providers := make([]security_cards_marketplace.SecurityCardsProvider, 0, len(in.Providers))
	for _, provider := range in.Providers {

		securityCards := make([]security_cards_marketplace.SecurityCard, 0, len(provider.SecurityCards))
		for _, securityCard := range provider.SecurityCards {

			securityCards = append(securityCards, security_cards_marketplace.SecurityCard{
				Image: securityCard.Image,
				Stock: securityCard.Stock,
			})
		}

		providers = append(providers, security_cards_marketplace.SecurityCardsProvider{
			Name:          provider.Name,
			CurrencyCode:  provider.CurrencyCode,
			ColorHex:      provider.ColorHex,
			Material:      provider.Material,
			Price:         provider.Price,
			ShippingCost:  provider.ShippingCost,
			SecurityCards: securityCards,
		})
	}

	return &security_cards_marketplace.Marketplace{
		Providers: providers,
	}, nil
}
