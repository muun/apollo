package recovery_tool

import (
	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/electrum"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/subsystems/recovery_tool/internal/domain/action"
	"github.com/muun/libwallet/subsystems/recovery_tool/internal/presentation"
)

type RecoveryTool = presentation.RecoveryTool

func NewRecoveryTool(
	keyProvider adapted.KeyProvider,
	electrumProvider *electrum.ServerProvider,
	network *libwallet.Network,
) RecoveryTool {
	return presentation.NewRecoveryTool(
		action.NewScanForFundsAction(keyProvider, electrumProvider, network),
		action.NewBuildSweepTxAction(keyProvider, network),
		action.NewSignSweepTxAction(keyProvider, network),
	)
}
