package libwallet_init

import (
	"errors"
	"github.com/grpc-ecosystem/go-grpc-middleware"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/domain/action/challenge_keys"
	"github.com/muun/libwallet/domain/action/diagnostic_mode_reports"
	nfcActions "github.com/muun/libwallet/domain/action/nfc"
	"github.com/muun/libwallet/domain/action/recovery"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/electrum"
	"github.com/muun/libwallet/storage"
	"log/slog"
	"net"
	"path"
	"runtime/debug"

	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/log"
	"github.com/muun/libwallet/presentation"
	"github.com/muun/libwallet/presentation/api"
	"github.com/muun/libwallet/service"
	"google.golang.org/grpc"
)

var server *grpc.Server
var cfg *app_provided_data.Config
var keyValueStorage *storage.KeyValueStorage
var network *libwallet.Network
var houstonService service.HoustonService
var mockHoustonService service.HoustonService
var keyProvider keys.KeyProvider
var startChallengeSetupAction *challenge_keys.StartChallengeSetupAction
var finishChallengeSetupAction *challenge_keys.FinishChallengeSetupAction
var computeAndStoreEncryptedMuunKeyAction *recovery.ComputeAndStoreEncryptedMuunKeyAction
var populateEncryptedMuunKeyAction *recovery.PopulateEncryptedMuunKeyAction
var scanForFundsAction *recovery.ScanForFundsAction
var submitDiagnosticAction *diagnostic_mode_reports.SubmitDiagnosticAction
var buildSweepTxAction *recovery.BuildSweepTxAction
var signSweepTxAction *recovery.SignSweepTxAction
var pairSecurityCardAction *nfcActions.PairSecurityCardAction
var resetSecurityCardAction *nfcActions.ResetSecurityCardAction
var signMessageSecurityCardAction *nfcActions.SignMessageSecurityCardAction
var pairSecurityCardActionV2 *nfcActions.PairSecurityCardActionV2
var signMessageSecurityCardActionV2 *nfcActions.SignMessageSecurityCardActionV2

// Init configures libwallet
func Init(c *app_provided_data.Config) {
	cfg = c

	debug.SetTraceback("crash")
	libwallet.Init(c)

	if c.AppLogSink != nil {
		level := c.AppLogSink.GetDefaultLogLevel()
		logger := slog.New(log.NewBridgeLogHandler(c.AppLogSink, slog.Level(level)))
		slog.SetDefault(logger)
	}

	if cfg.HttpClientSessionProvider != nil {
		houstonService = service.NewHoustonService(cfg.HttpClientSessionProvider)
	}

	var storageSchema = storage.BuildStorageSchema()
	keyValueStorage = storage.NewKeyValueStorage(path.Join(cfg.DataDir, "wallet.db"), storageSchema)

	mockHoustonService = service.NewMockHoustonService(keyValueStorage)

	switch c.Network {
	case libwallet.Mainnet().Name():
		network = libwallet.Mainnet()
	case libwallet.Testnet().Name():
		network = libwallet.Testnet()
	case libwallet.Regtest().Name():
		network = libwallet.Regtest()
	default:
		panic("unknown network: " + c.Network)
	}
	keyProvider = keys.NewKeyProvider(c.KeyProvider, *network)

	// TODO do this only for debug builds and while making use of FakeNfcSession or equivalent
	// Enables security cards testing in emulators and ui tests.
	//mockMuunCardV2, _ := nfc.NewMockMuunCardV2()
	//cfg.NfcBridge = nfc.NewMockJavaCard(mockMuunCardV2)

	muuncard := nfc.NewCard(cfg.NfcBridge)
	muuncardV2 := nfc.NewCardV2(cfg.NfcBridge)
	// Actions
	computeAndStoreEncryptedMuunKeyAction = recovery.NewComputeAndStoreEncryptedMuunKeyAction(
		keyValueStorage,
		keyProvider,
	)
	populateEncryptedMuunKeyAction = recovery.NewPopulateEncryptedMuunKeyAction(
		houstonService,
		keyValueStorage,
		keyProvider,
	)
	startChallengeSetupAction = challenge_keys.NewStartChallengeSetupAction(houstonService)
	finishChallengeSetupAction = challenge_keys.NewFinishChallengeSetupAction(
		houstonService,
		keyValueStorage,
		computeAndStoreEncryptedMuunKeyAction,
	)
	electrumProvider := electrum.NewServerProvider(electrum.PublicServers)
	scanForFundsAction = recovery.NewScanForFundsAction(keyProvider, electrumProvider, network)
	submitDiagnosticAction = diagnostic_mode_reports.NewSubmitDiagnosticAction(houstonService)
	buildSweepTxAction = recovery.NewBuildSweepTxAction(keyProvider, network)
	signSweepTxAction = recovery.NewSignSweepTxAction(keyProvider, network)
	pairSecurityCardAction = nfcActions.NewPairSecurityCardAction(keyValueStorage, muuncard)
	resetSecurityCardAction = nfcActions.NewResetSecurityCardAction(keyValueStorage, muuncard)
	signMessageSecurityCardAction = nfcActions.NewSignMessageSecurityCardAction(
		keyValueStorage,
		muuncard,
		network,
	)
	pairSecurityCardActionV2 = nfcActions.NewPairSecurityCardActionV2(keyValueStorage, muuncardV2, mockHoustonService)
	signMessageSecurityCardActionV2 = nfcActions.NewSignMessageSecurityCardActionV2(
		muuncardV2,
		mockHoustonService,
		keyValueStorage,
		pairSecurityCardActionV2,
	)
}

func StartServer() error {
	if server != nil {
		return errors.New("server is already running")
	}

	opts := []grpc.ServerOption{
		grpc.ReadBufferSize(0),
		grpc.WriteBufferSize(0),
		grpc.NumStreamWorkers(8),
		grpc.UnaryInterceptor(
			grpc_middleware.ChainUnaryServer(
				// Order is important.
				presentation.LoggingInterceptor(), // First interceptor
				presentation.RecoverUnknownErrorInterceptor(),
				presentation.RecoverPanicInterceptor(), // Last interceptor
			),
		),
	}

	server = grpc.NewServer(opts...)
	api.RegisterWalletServiceServer(server, presentation.NewWalletServer(
		cfg.NfcBridge,
		keyProvider,
		network,
		houstonService,
		keyValueStorage,
		startChallengeSetupAction,
		finishChallengeSetupAction,
		populateEncryptedMuunKeyAction,
		scanForFundsAction,
		submitDiagnosticAction,
		buildSweepTxAction,
		signSweepTxAction,
		pairSecurityCardAction,
		resetSecurityCardAction,
		signMessageSecurityCardAction,
		pairSecurityCardActionV2,
		signMessageSecurityCardActionV2,
	))

	listener, err := net.Listen("unix", cfg.SocketPath)
	if err != nil {
		slog.Error("socket creation failure", "error", err)
		return err
	}

	go func() {
		if err := server.Serve(listener); err != nil {
			slog.Error("error when starting server goroutine", "error", err)
		}
	}()

	return nil
}

func StopServer() {
	if server == nil {
		slog.Warn("tried to stop server when none is running")
		return
	}
	server.Stop()
}
