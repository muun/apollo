package recovery

import (
	"bytes"
	"encoding/hex"
	"fmt"
	"github.com/btcsuite/btcd/wire"
	"github.com/muun/libwallet/electrum"
	"log/slog"
)

type BroadcastRecoveryTxAction struct {
	electrumProvider *electrum.ServerProvider
}

func NewBroadcastRecoveryTxAction(electrumProvider *electrum.ServerProvider) *BroadcastRecoveryTxAction {
	return &BroadcastRecoveryTxAction{
		electrumProvider: electrumProvider,
	}
}

func (s *BroadcastRecoveryTxAction) Run(tx *wire.MsgTx, log *slog.Logger) (string, error) {
	client := electrum.NewClient(true, log)

	for !client.IsConnected() {
		err := client.Connect(s.electrumProvider.NextServer())
		if err != nil {
			return "", err
		}
	}

	// Encode the transaction for broadcast:
	txBytes := new(bytes.Buffer)

	err := tx.BtcEncode(txBytes, wire.ProtocolVersion, wire.WitnessEncoding)
	if err != nil {
		return "", fmt.Errorf("error while encoding tx: %w", err)
	}

	txHex := hex.EncodeToString(txBytes.Bytes())

	return client.Broadcast(txHex)
}
