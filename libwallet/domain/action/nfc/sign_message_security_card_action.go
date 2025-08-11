package nfc

import (
	"encoding/hex"
	"fmt"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/storage"
	"log/slog"
)

type SignMessageSecurityCardAction struct {
	keyValueStorage *storage.KeyValueStorage
	muunCard        *nfc.MuunCard
	network         *libwallet.Network
}

func NewSignMessageSecurityCardAction(
	keyValueStorage *storage.KeyValueStorage,
	muunCard *nfc.MuunCard,
	network *libwallet.Network,
) *SignMessageSecurityCardAction {
	return &SignMessageSecurityCardAction{keyValueStorage: keyValueStorage, muunCard: muunCard, network: network}
}

func (ac *SignMessageSecurityCardAction) Run(messageHex string) (*nfc.SignedMessage, error) {
	xPubSerialized, err := ac.keyValueStorage.Get(storage.KeySecurityCardXpubSerialized)
	if err != nil {
		return nil, fmt.Errorf("error loading Xpub from local: %w", err)
	}
	if xPubSerialized == nil {
		// TODO: Remove this later. In POC V3 pairing/unpairing isn’t supported, so it does a pair in first interaction.
		pairNfcCard := NewPairSecurityCardAction(ac.keyValueStorage, ac.muunCard)
		xPub, err := pairNfcCard.Run()
		if err != nil {
			return nil, fmt.Errorf("error pairing card on first interaction: %w", err)
		}
		xPubSerialized = base58.Encode(xPub.RawBytes)
	}
	slog.Debug("WalletServer: xPubSerialized loaded", slog.Any("xPubSerialized", xPubSerialized))

	pubKey, err := libwallet.NewHDPublicKeyFromString(xPubSerialized.(string), "m", ac.network)
	if err != nil {
		return nil, fmt.Errorf("error extracting public key from base58 xPub: %w", err)
	}

	signedMessage, err := ac.muunCard.SignMessage(messageHex)
	if err != nil {
		return nil, fmt.Errorf("failed to sign message: %w", err)
	}

	signedMessageHex := hex.EncodeToString(signedMessage.RawBytes)
	// FIXME: Remove this log when go to production
	slog.Debug("WalletServer: Signed Message", slog.Any("signature", signedMessageHex))

	isValidated, err := ac.muunCard.VerifySignature(pubKey, []byte(messageHex), signedMessage.RawBytes)
	if err != nil {
		return nil, fmt.Errorf("error validating signature: %w", err)
	}
	if !isValidated {
		return nil, fmt.Errorf("invalid signature: %s", signedMessageHex)
	}

	return &nfc.SignedMessage{RawBytes: signedMessage.RawBytes}, nil
}
