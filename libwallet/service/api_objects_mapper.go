package service

import (
	"encoding/binary"
	"encoding/hex"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/service/model"
)

func MapRegisterSecurityCardJson( //nolint:staticcheck // TODO: func MapRegisterSecurityCardJson should be MapRegisterSecurityCardJSON
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

func mapSecurityCardMetadataJson( //nolint:staticcheck // TODO: func mapSecurityCardMetadataJson should be mapSecurityCardMetadataJSON
	metadata *nfc.CardMetadata,
) (*model.SecurityCardMetadataJson, error) {
	if metadata == nil {
		return nil, errors.Errorf("missing card metadata in pairing response")
	}

	globalPubCardInHex := hex.EncodeToString(metadata.GlobalPubCard[:])
	cardVendorInHex := hex.EncodeToString(metadata.CardVendor[:])
	cardModelInHex := hex.EncodeToString(metadata.CardModel[:])
	firmwareVersion := binary.BigEndian.Uint16(metadata.FirmwareVersion[:])
	languageCodeInHex := hex.EncodeToString(metadata.LanguageCode[:])

	metadataJson := &model.SecurityCardMetadataJson{ //nolint:staticcheck // TODO: var metadataJson should be metadataJSON
		GlobalPublicKeyInHex: globalPubCardInHex,
		CardVendorInHex:      cardVendorInHex,
		CardModelInHex:       cardModelInHex,
		FirmwareVersion:      firmwareVersion,
		UsageCount:           metadata.UsageCount,
		LanguageCodeInHex:    languageCodeInHex,
	}

	return metadataJson, nil
}

func mapSecurityCardV3MetadataJSON(
	metadata *nfc.CardMetadataV3,
) (*model.SecurityCardV3MetadataJSON, error) {
	if metadata == nil {
		return nil, errors.Errorf("missing card metadata in pairing response")
	}

	return &model.SecurityCardV3MetadataJSON{
		AttestationPubKeyInHex: hex.EncodeToString(metadata.AttestationPub[:]),
		CardVendorInHex:        hex.EncodeToString(metadata.CardVendor[:]),
		CardModelInHex:         hex.EncodeToString(metadata.CardModel[:]),
		FirmwareVersion:        binary.BigEndian.Uint16(metadata.FirmwareVersion[:]),
		CapabilitiesInHex:      hex.EncodeToString(metadata.Capabilities[:]),
		OperationCount:         metadata.OperationCount,
		ProviderPubKeyInHex:    hex.EncodeToString(metadata.ProviderPub[:]),
		ProviderSigInHex:       hex.EncodeToString(metadata.ProviderSig),
	}, nil
}
