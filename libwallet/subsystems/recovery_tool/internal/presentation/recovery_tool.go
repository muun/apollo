package presentation

import (
	"log/slog"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/wire"

	"github.com/muun/libwallet/legacy/scanner"
	"github.com/muun/libwallet/subsystems/recovery_tool/internal/domain/action"
)

type RecoveryTool struct {
	scanForFunds action.ScanForFundsAction
	buildSweepTx action.BuildSweepTxAction
	signSweepTx  action.SignSweepTxAction
}

func NewRecoveryTool(
	scanForFunds action.ScanForFundsAction,
	buildSweepTx action.BuildSweepTxAction,
	signSweepTx action.SignSweepTxAction,
) RecoveryTool {
	return RecoveryTool{
		scanForFunds: scanForFunds,
		buildSweepTx: buildSweepTx,
		signSweepTx:  signSweepTx,
	}
}

func (r *RecoveryTool) ScanForFunds(
	logger *slog.Logger,
) (<-chan *scanner.Report, error) {
	return r.scanForFunds.Run(logger)
}

func (r *RecoveryTool) BuildSweepTx(
	utxos []*scanner.Utxo,
	sweepAddress btcutil.Address,
	feeRateInSatsPerVByte float64,
) (*wire.MsgTx, error) {
	return r.buildSweepTx.Run(utxos, sweepAddress, feeRateInSatsPerVByte)
}

func (r *RecoveryTool) SignSweepTx(
	utxos []*scanner.Utxo,
	tx *wire.MsgTx,
	recoveryCode string,
) (*wire.MsgTx, error) {
	return r.signSweepTx.Run(utxos, tx, recoveryCode)
}
