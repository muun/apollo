package nfc

import (
	"crypto/ecdh"
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"github.com/muun/libwallet/domain/model/security_card"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
)

type PairSecurityCardActionV2 struct {
	keyValueStorage *storage.KeyValueStorage
	muunCard        *nfc.MuunCardV2
	houstonService  service.HoustonService
}

func NewPairSecurityCardActionV2(
	storage *storage.KeyValueStorage,
	muunCard *nfc.MuunCardV2,
	houstonService service.HoustonService,
) *PairSecurityCardActionV2 {
	return &PairSecurityCardActionV2{keyValueStorage: storage, muunCard: muunCard, houstonService: houstonService}
}

func (ac *PairSecurityCardActionV2) Run() (*security_card.SecurityCardPaired, error) {
	challengePair, err := ac.houstonService.ChallengeSecurityCardPair()
	if err != nil {
		return nil, fmt.Errorf("error requesting challenge to server: %w", err)
	}

	serverPublicKey, err := hex.DecodeString(challengePair.ServerPublicKeyInHex)
	if err != nil {
		return nil, fmt.Errorf("error decoding server key: %w", err)
	}

	clientPrivateKey, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("error generating client private key: %w", err)
	}

	clientPublicKey := clientPrivateKey.PublicKey().Bytes()

	pairingResponse, err := ac.muunCard.Pair(serverPublicKey, clientPublicKey)
	if err != nil {
		return nil, fmt.Errorf("error during pairing with card: %w", err)
	}

	cardPublicKeyInHex := hex.EncodeToString(pairingResponse.CardPublicKey)
	clientPublicKeyInHex := hex.EncodeToString(clientPublicKey)
	macInHex := hex.EncodeToString(pairingResponse.MAC)
	globalSignCardInHex := hex.EncodeToString(pairingResponse.GlobalSignature)
	pairingSlot := int(binary.BigEndian.Uint16(pairingResponse.PairingSlot))

	if pairingResponse.Metadata == nil {
		return nil, fmt.Errorf("missing card metadata in pairing response")
	}
	metadata := mapToSecurityCardMetadataJson(pairingResponse.Metadata)

	registerSecurityCardJson := model.RegisterSecurityCardJson{
		CardPublicKeyInHex:   cardPublicKeyInHex,
		ClientPublicKeyInHex: clientPublicKeyInHex,
		PairingSlot:          pairingSlot,
		Metadata:             metadata,
		MacInHex:             macInHex,
		GlobalSignCardInHex:  globalSignCardInHex,
	}

	registerSecurityResponse, err := ac.houstonService.RegisterSecurityCard(registerSecurityCardJson)
	if err != nil {
		return nil, fmt.Errorf("server error registering security card: %w", err)
	}

	err = ac.keyValueStorage.Save(storage.KeySecurityCardPaired, true)
	if err != nil {
		return nil, fmt.Errorf("error storing paired status: %w", err)
	}

	return service.MapSecurityCardPaired(registerSecurityResponse), nil
}

func mapToSecurityCardMetadataJson(metadata *nfc.CardMetadata) model.SecurityCardMetadataJson {
	globalPubCardInHex := hex.EncodeToString(metadata.GlobalPubCard[:])
	cardVendorInHex := hex.EncodeToString(metadata.CardVendor[:])
	cardModelInHex := hex.EncodeToString(metadata.CardModel[:])
	firmwareVersion := int(binary.BigEndian.Uint16(metadata.FirmwareVersion[:]))
	languageCodeInHex := hex.EncodeToString(metadata.LanguageCode[:])

	metadataJson := model.SecurityCardMetadataJson{
		GlobalPublicKeyInHex: globalPubCardInHex,
		CardVendorInHex:      cardVendorInHex,
		CardModelInHex:       cardModelInHex,
		FirmwareVersion:      firmwareVersion,
		UsageCount:           int(metadata.UsageCount),
		LanguageCodeInHex:    languageCodeInHex,
	}

	return metadataJson
}
