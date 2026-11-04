package presentation

import (
	"log/slog"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/wire"

	"github.com/muun/libwallet/legacy/scanner"
)

type RecoveryTool interface {
	ScanForFunds(logger *slog.Logger) (<-chan *scanner.Report, error)
	BuildSweepTx(
		utxos []*scanner.Utxo,
		sweepAddress btcutil.Address,
		feeRateInSatsPerVByte float64,
	) (*wire.MsgTx, error)
	SignSweepTx(
		utxos []*scanner.Utxo,
		tx *wire.MsgTx,
		recoveryCode string,
	) (*wire.MsgTx, error)
}
