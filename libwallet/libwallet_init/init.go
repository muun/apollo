package libwallet_init

import (
	"fmt"
	"log/slog"
	"net"
	"path"
	"runtime/debug"

	"github.com/go-errors/errors"
	grpc_middleware "github.com/grpc-ecosystem/go-grpc-middleware"
	"google.golang.org/grpc"

	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/data/securekv"
	"github.com/muun/libwallet/data/security_cards"
	"github.com/muun/libwallet/domain/action/challenge_keys"
	debugAction "github.com/muun/libwallet/domain/action/debug"
	"github.com/muun/libwallet/domain/action/diagnostic_mode_reports"
	"github.com/muun/libwallet/domain/action/emergency_kit"
	nfcActions "github.com/muun/libwallet/domain/action/nfc"
	"github.com/muun/libwallet/domain/action/recovery"
	"github.com/muun/libwallet/domain/action/reset"
	"github.com/muun/libwallet/domain/action/security_cards_marketplace"
	"github.com/muun/libwallet/domain/nfc"
	"github.com/muun/libwallet/electrum"
	"github.com/muun/libwallet/storage"
	"github.com/muun/libwallet/walletdb"

	"github.com/muun/libwallet/log"
	"github.com/muun/libwallet/presentation"
	"github.com/muun/libwallet/presentation/api"
	"github.com/muun/libwallet/service"
)

var server *grpc.Server
var pool *walletdb.Pool
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
var pairSecurityCardActionV2 *nfcActions.PairSecurityCardActionV2
var signMessageSecurityCardActionV2 *nfcActions.SignMessageSecurityCardActionV2
var pairRequestChallengeAction *nfcActions.PairRequestChallengeAction
var pairLoadPersistedChallengeAction *nfcActions.PairLoadPersistedChallengeAction
var pairSignChallengeAction *nfcActions.PairSignChallengeAction
var pairSubmitSolvedChallengeAction *nfcActions.PairSubmitSolvedChallengeAction
var pairSignAndSubmitChallengeAction *nfcActions.PairSignAndSubmitChallengeAction
var securityCardsProtocolRepository *security_cards.ProtocolRepository
var securityCardsMarketplaceAction *security_cards_marketplace.GetSecurityCardsMarketplaceAction
var generateEmergencyKitPDFAction *emergency_kit.GenerateEmergencyKitPDFAction
var secureKeyValueStorage securekv.SecureKeyValueStorage
var zipDataDirAction *debugAction.ZipDataDirAction
var resetDataAction reset.ResetDataAction

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

	dbPath := path.Join(cfg.DataDir, "wallet.db")
	var storageSchema map[string]storage.Classification
	var err error
	pool, err = walletdb.NewPool(dbPath, func(db *walletdb.DB) error {
		var migErr error
		storageSchema, migErr = storage.RunKeyValueMigrations(db, storage.BuildKVMigrationPlan())
		return migErr
	})
	if err != nil {
		slog.Error("failed to initialize database", "error", err)
		panic(fmt.Sprintf("failed to initialize database: %v", err))
	}
	libwallet.Pool = pool
	keyValueStorage = storage.NewKeyValueStorage(
		pool.NewKeyValueRepository(),
		storageSchema,
	)

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
	pairSecurityCardActionV2 = nfcActions.NewPairSecurityCardActionV2(
		keyValueStorage,
		muuncardV2,
		mockHoustonService,
	)
	signMessageSecurityCardActionV2 = nfcActions.NewSignMessageSecurityCardActionV2(
		muuncardV2,
		mockHoustonService,
		keyValueStorage,
		pairSecurityCardActionV2,
	)
	securityCardsProtocolRepository = security_cards.NewProtocolRepository(keyValueStorage)
	pairRequestChallengeAction = nfcActions.NewPairRequestChallengeAction(
		securityCardsProtocolRepository,
		mockHoustonService,
	)
	pairLoadPersistedChallengeAction = nfcActions.NewPairLoadPersistedChallengeAction(
		securityCardsProtocolRepository,
	)
	pairSignChallengeAction = nfcActions.NewPairSignChallengeAction(muuncardV2)
	pairSubmitSolvedChallengeAction = nfcActions.NewPairSubmitSolvedChallengeAction(
		securityCardsProtocolRepository,
		mockHoustonService,
	)
	pairSignAndSubmitChallengeAction = nfcActions.NewPairSignAndSubmitChallengeAction(
		pairLoadPersistedChallengeAction,
		pairRequestChallengeAction,
		pairSignChallengeAction,
		pairSubmitSolvedChallengeAction,
	)
	securityCardsMarketplaceAction = security_cards_marketplace.
		NewGetSecurityCardsMarketplaceAction(mockHoustonService)
	generateEmergencyKitPDFAction = emergency_kit.NewGenerateEmergencyKitPDFAction()

	if cfg.SecureKeyValueStorage != nil {
		secureKeyValueStorage = securekv.NewSecureKeyValueStorage(cfg.SecureKeyValueStorage)
	}
	zipDataDirAction = debugAction.NewZipDataDirAction(cfg.DataDir)
	resetDataAction = reset.NewResetDataAction(dbPath, pool, storage.BuildKVMigrationPlan())
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
				presentation.LoggingUnaryInterceptor(), // First interceptor
				presentation.RecoverUnknownErrorUnaryInterceptor(),
				presentation.RecoverPanicUnaryInterceptor(), // Last interceptor
			),
		),
		grpc.StreamInterceptor(
			grpc_middleware.ChainStreamServer(
				// Order is important.
				presentation.LoggingStreamInterceptor(), // First interceptor
				presentation.RecoverUnknownErrorStreamInterceptor(),
				presentation.RecoverPanicStreamInterceptor(), // Last interceptor
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
		resetDataAction,
		startChallengeSetupAction,
		finishChallengeSetupAction,
		populateEncryptedMuunKeyAction,
		scanForFundsAction,
		submitDiagnosticAction,
		buildSweepTxAction,
		signSweepTxAction,
		pairSecurityCardActionV2,
		signMessageSecurityCardActionV2,
		pairRequestChallengeAction,
		pairSignAndSubmitChallengeAction,
		securityCardsMarketplaceAction,
		generateEmergencyKitPDFAction,
		zipDataDirAction,
		secureKeyValueStorage,
	))

	listener, err := net.Listen("unix", cfg.SocketPath) //nolint:noctx // TODO: use (*net.ListenConfig).Listen
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
	if pool != nil {
		pool.Close()
		pool = nil
		libwallet.Pool = nil
	}
}
