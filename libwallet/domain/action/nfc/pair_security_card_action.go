package nfc

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/storage"
	"log/slog"
)

type PairSecurityCardAction struct {
	keyValueStorage *storage.KeyValueStorage
	muunCard        *nfc.MuunCard
}

func NewPairSecurityCardAction(storage *storage.KeyValueStorage, muunCard *nfc.MuunCard) *PairSecurityCardAction {
	return &PairSecurityCardAction{keyValueStorage: storage, muunCard: muunCard}
}

func (ac *PairSecurityCardAction) Run() (*nfc.ExtendedPublicKey, error) {
	xPub, _ := ac.keyValueStorage.Get(storage.KeySecurityCardXpubSerialized)
	// If the user uninstall the app, xPub can be nil. In that case, we wipe nfc card in POC V3
	if xPub == nil {
		// TODO: Remove this later. In POC V3 pairing/unpairing isn’t supported, so we wipe all data.
		err := ac.muunCard.ResetCard()
		if err != nil {
			var cardError *nfc.CardError
			if errors.As(err, &cardError) {
				// Do not return error in first pairing
				if cardError.Code != nfc.ErrSlotNotInitialized {
					return nil, fmt.Errorf("error wipping card before pairing: %w", cardError)
				}
			} else {
				return nil, fmt.Errorf("error wipping card before pairing: %w", err)
			}
		}
	}

	seed := randomBytes(16)
	var seed16Bytes [16]byte
	copy(seed16Bytes[:], seed)
	// FIXME: Remove this log when go to production
	slog.Debug("pairing card with random seed:", slog.String("seed", hex.EncodeToString(seed)))
	serializedXpub, err := ac.muunCard.GenerateKeyPair(seed16Bytes)
	if err != nil {
		return nil, fmt.Errorf("error pairing card: %w", err)
	}

	// Persist Xpub in local storage; it will be used later for signature validation
	base58Xpub := base58.Encode(serializedXpub.RawBytes)
	err = ac.keyValueStorage.Save(storage.KeySecurityCardXpubSerialized, base58Xpub)
	if err != nil {
		return nil, fmt.Errorf("error storing Xpub: %w", err)
	}

	return serializedXpub, nil
}

func randomBytes(count int) []byte {
	buf := make([]byte, count)
	_, err := rand.Read(buf)
	if err != nil {
		panic(err)
	}
	return buf
}
