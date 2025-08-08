package nfc

import (
	"fmt"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/storage"
)

type ResetSecurityCardAction struct {
	keyValueStorage *storage.KeyValueStorage
	muunCard        *nfc.MuunCard
}

func NewResetSecurityCardAction(storage *storage.KeyValueStorage, muunCard *nfc.MuunCard) *ResetSecurityCardAction {
	return &ResetSecurityCardAction{keyValueStorage: storage, muunCard: muunCard}
}

func (ac *ResetSecurityCardAction) Run() error {

	err := ac.muunCard.ResetCard()
	if err != nil {
		return fmt.Errorf("error unpairing card: %w", err)
	}

	err = ac.keyValueStorage.Delete(storage.KeySecurityCardXpubSerialized)
	if err != nil {
		return fmt.Errorf("error removing Xpub: %w", err)
	}

	return nil
}
