package service

import (
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/service/model"
)

func MapRegisterSecurityCardJson(
	pairingResponse *nfc.PairingResponse,
	clientPublicKey []byte,
) (*model.RegisterSecurityCardJson, error) {

	metadata, err := mapSecurityCardMetadataJson(pairingResponse.Metadata)
	if err != nil {
		return nil, err
	}

	return &model.RegisterSecurityCardJson{
		CardPublicKeyInHex:   hex.EncodeToString(pairingResponse.CardPublicKey),
		ClientPublicKeyInHex: hex.EncodeToString(clientPublicKey),
		PairingSlot:          binary.BigEndian.Uint16(pairingResponse.PairingSlot),
		Metadata:             *metadata,
		MacInHex:             hex.EncodeToString(pairingResponse.MAC),
		GlobalSignCardInHex:  hex.EncodeToString(pairingResponse.GlobalSignature),
	}, nil
}

func mapSecurityCardMetadataJson(metadata *nfc.CardMetadata) (*model.SecurityCardMetadataJson, error) {
	if metadata == nil {
		return nil, fmt.Errorf("missing card metadata in pairing response")
	}

	globalPubCardInHex := hex.EncodeToString(metadata.GlobalPubCard[:])
	cardVendorInHex := hex.EncodeToString(metadata.CardVendor[:])
	cardModelInHex := hex.EncodeToString(metadata.CardModel[:])
	firmwareVersion := binary.BigEndian.Uint16(metadata.FirmwareVersion[:])
	languageCodeInHex := hex.EncodeToString(metadata.LanguageCode[:])

	metadataJson := &model.SecurityCardMetadataJson{
		GlobalPublicKeyInHex: globalPubCardInHex,
		CardVendorInHex:      cardVendorInHex,
		CardModelInHex:       cardModelInHex,
		FirmwareVersion:      firmwareVersion,
		UsageCount:           metadata.UsageCount,
		LanguageCodeInHex:    languageCodeInHex,
	}

	return metadataJson, nil
}
