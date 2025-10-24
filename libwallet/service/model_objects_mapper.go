package service

import (
	"github.com/muun/libwallet/domain/model/security_card"
	"github.com/muun/libwallet/service/model"
)

func MapSecurityCardPaired(in model.RegisterSecurityCardOkJson) *security_card.SecurityCardPaired {
	return &security_card.SecurityCardPaired{
		Metadata:          MapSecurityCardMetadata(in.Metadata),
		IsKnownProvider:   in.IsKnownProvider,
		IsCardAlreadyUsed: in.IsCardAlreadyUsed,
	}
}

func MapSecurityCardMetadata(in model.SecurityCardMetadataJson) *security_card.SecurityCardMetadata {
	return &security_card.SecurityCardMetadata{
		GlobalPublicKeyInHex: in.GlobalPublicKeyInHex,
		CardVendorInHex:      in.CardVendorInHex,
		CardModelInHex:       in.CardModelInHex,
		FirmwareVersion:      in.FirmwareVersion,
		UsageCount:           in.UsageCount,
		LanguageCodeInHex:    in.LanguageCodeInHex,
	}
}
