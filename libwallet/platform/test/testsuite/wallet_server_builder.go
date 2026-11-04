package testsuite

import (
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/storage"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/server"
	"github.com/muun/libwallet/subsystems/background_actions"
	"github.com/muun/libwallet/subsystems/diagnostics"
	"github.com/muun/libwallet/subsystems/emergency_kit"
	"github.com/muun/libwallet/subsystems/marketplace"
	"github.com/muun/libwallet/subsystems/rates"
	"github.com/muun/libwallet/subsystems/recovery"
	"github.com/muun/libwallet/subsystems/recovery_tool"
	"github.com/muun/libwallet/subsystems/security_card"
)

type WalletServerBuilder struct {
	NfcBridge         app_provided_data.NfcBridge
	KeyProvider       adapted.KeyProvider
	Network           *libwallet.Network
	HoustonService    houston.HoustonService
	KeyValueStorage   *storage.KeyValueStorage
	ResetData         func() error
	Recovery          recovery.Recovery
	RecoveryTool      recovery_tool.RecoveryTool
	Diagnostics       diagnostics.Diagnostics
	SecurityCard      security_card.SecurityCard
	Marketplace       marketplace.Marketplace
	EmergencyKit      emergency_kit.EmergencyKit
	BackgroundActions background_actions.BackgroundActions
	Rates             rates.Rates
}

func (b WalletServerBuilder) Build() *server.WalletServer {
	return server.NewWalletServer(
		b.NfcBridge,
		b.KeyProvider,
		b.Network,
		b.HoustonService,
		b.KeyValueStorage,
		b.ResetData,
		&b.Recovery,
		&b.RecoveryTool,
		&b.Diagnostics,
		&b.SecurityCard,
		&b.Marketplace,
		&b.EmergencyKit,
		&b.BackgroundActions,
		&b.Rates,
	)
}
