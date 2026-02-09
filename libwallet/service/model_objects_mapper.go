package service

import (
	"encoding/hex"
	"fmt"
	"github.com/muun/libwallet/domain/model/security_card"
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
