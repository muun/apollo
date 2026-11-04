package libwallet_init

import (
	"context"
	"fmt"
	"log/slog"
	"net"
	"path"
	"runtime/debug"
	"time"

	"github.com/go-errors/errors"
	grpc_middleware "github.com/grpc-ecosystem/go-grpc-middleware"
	"google.golang.org/grpc"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/base/data/electrum"
	"github.com/muun/libwallet/base/data/securekv"
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/base/data/walletdb"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/legacy/newop"
	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/subsystems/background_actions"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
	"github.com/muun/libwallet/subsystems/diagnostics"
	"github.com/muun/libwallet/subsystems/emergency_kit"
	"github.com/muun/libwallet/subsystems/marketplace"
	"github.com/muun/libwallet/subsystems/rates"
	"github.com/muun/libwallet/subsystems/recovery"
	"github.com/muun/libwallet/subsystems/recovery_tool"
	"github.com/muun/libwallet/subsystems/security_card"

	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/houston/simulator"
	"github.com/muun/libwallet/platform/observability/logging"
	"github.com/muun/libwallet/platform/observability/otel"
	"github.com/muun/libwallet/server"
	"github.com/muun/libwallet/server/api"
)

var grpcServer *grpc.Server
var pool *walletdb.Pool
var cfg *app_provided_data.Config
var keyValueStorage *storage.KeyValueStorage
var network *libwallet.Network
var houstonService houston.HoustonService
var simulatedHoustonService houston.HoustonService
var keyProvider adapted.KeyProvider
var otelSetup *otel.Setup
var resetDataFn func() error
var locker memlock.NamedLocker

var recoverySubsystem recovery.Recovery
var recoveryToolSubsystem recovery_tool.RecoveryTool
var diagnosticsSubsystem diagnostics.Diagnostics
var securityCardSubsystem security_card.SecurityCard
var marketplaceSubsystem marketplace.Marketplace
var emergencyKitSubsystem emergency_kit.EmergencyKit
var backgroundActionsSubsystem background_actions.BackgroundActions
var ratesSubsystem rates.Rates

// Init configures libwallet
func Init(c *app_provided_data.Config) {
	cfg = c

	debug.SetTraceback("crash")
	libwallet.Init(c)

	if c.AppLogSink != nil {
		logger := slog.New(logging.NewBridgeHandler(c.AppLogSink))
		slog.SetDefault(logger)
	}

	if cfg.HttpClientSessionProvider != nil {
		houstonService = houston.NewHoustonService(cfg.HttpClientSessionProvider)
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
	secureKeyValueStore := securekv.NewSecureKeyValueStore(c.SecureKeyValueStore)
	keyProvider = adapted.NewKeyProvider(c.KeyProvider, secureKeyValueStore, *network)

	simulatedHoustonService = simulator.NewSimulatedHoustonService(keyValueStorage, keyProvider)

	platform := app_provided_data.Platform(c.Platform)
	if !platform.Android() && !platform.IOS() {
		panic("unknown platform: " + c.Platform)
	}

	periodicActionsBridge := adapted.NewPeriodicActionsBridge(c.PeriodicActionsBridge)

	otelConfig := otel.NewConfig().
		WithHoneycombAPIKey(cfg.HoneycombAPIKey).
		WithHoneycombDataset(cfg.HoneycombDataset).
		WithLogSpans(true).
		WithExtraAttrs(map[string]string{
			"os_name":    cfg.Platform,
			"os_version": cfg.OsVersion,
		})

	if cfg.HoneycombAPIKey != "" && cfg.HttpClientSessionProvider != nil {
		session, err := cfg.HttpClientSessionProvider.Session()
		if err != nil {
			slog.Error("failed to get the client session for tracing", "error", err)
		} else {
			otelConfig.WithExtraAttrs(map[string]string{
				"client_type":         session.ClientType,
				"client_version":      session.ClientVersion,
				"client_version_name": session.ClientVersionName,
				"client_sdk_version":  session.ClientSdkVersion,
			})
		}
	}

	otelSetup, err = otel.NewSetup(context.Background(), otelConfig)
	if err != nil {
		slog.Error("Failed to setup OpenTelemetry", "error", err)
		otelSetup = nil
	}

	electrumProvider := electrum.NewServerProvider(electrum.PublicServers)

	recoverySubsystem = recovery.NewRecovery(
		houstonService, keyValueStorage, keyProvider,
	)
	recoveryToolSubsystem = recovery_tool.NewRecoveryTool(
		keyProvider, electrumProvider, network,
	)
	diagnosticsSubsystem = diagnostics.NewDiagnostics(houstonService, cfg.DataDir)
	securityCardSubsystem = security_card.NewSecurityCard(
		cfg.NfcBridge, keyValueStorage, simulatedHoustonService, keyProvider,
	)
	marketplaceSubsystem = marketplace.NewMarketplace(
		simulatedHoustonService, keyValueStorage, network,
	)
	emergencyKitSubsystem = emergency_kit.NewEmergencyKit()
	backgroundActionsSubsystem = background_actions.NewBackgroundActions(
		&locker,
		keyValueStorage,
		platform,
		cfg.AndroidBackgroundActionsBridge,
		cfg.IOSBackgroundActionsBridge,
		cfg.SessionProvider,
		map[ba_model.Function]ba_model.Handler{
			ba_model.PlatformPullNotifications: periodicActionsBridge.PullNotifications,
			ba_model.PlatformSyncRealTimeData:  periodicActionsBridge.SyncRealTimeData,
			ba_model.PlatformRegisterInvoices:  periodicActionsBridge.RegisterInvoices,
		},
		[]ba_model.StaticRegistration{
			ba_model.NewStaticRegistration(ba_model.PlatformPullNotifications, 3*time.Hour),
			ba_model.NewStaticRegistration(ba_model.PlatformSyncRealTimeData, 6*time.Hour),
			ba_model.NewStaticRegistration(ba_model.PlatformRegisterInvoices, 6*time.Hour),
		},
	)
	ratesSubsystem = rates.NewRates(keyValueStorage)
	newop.SetRates(&ratesSubsystem)

	migrationPlan := storage.BuildKVMigrationPlan()
	resetPool := pool
	resetDataFn = func() error {
		return walletdb.ResetDB(dbPath, resetPool, func(db *walletdb.DB) error {
			_, err := storage.RunKeyValueMigrations(db, migrationPlan)
			return err
		})
	}

}

func StartServer() error {
	if grpcServer != nil {
		return errors.New("server is already running")
	}

	opts := []grpc.ServerOption{
		grpc.ReadBufferSize(0),
		grpc.WriteBufferSize(0),
		grpc.NumStreamWorkers(8),
		grpc.UnaryInterceptor(
			grpc_middleware.ChainUnaryServer(
				// Order is important.
				server.TracingUnaryInterceptor(
					otelSetup,
					cfg.SessionProvider,
					// Methods where we want to force flush traces when finished.
					api.WalletService_RunDueBackgroundActions_FullMethodName,
				), // First interceptor
				server.RecoverUnknownErrorUnaryInterceptor(),
				server.RecoverPanicUnaryInterceptor(), // Last interceptor
			),
		),
		grpc.StreamInterceptor(
			grpc_middleware.ChainStreamServer(
				// Order is important.
				server.TracingStreamInterceptor(
					otelSetup,
					cfg.SessionProvider,
				), // First interceptor
				server.RecoverUnknownErrorStreamInterceptor(),
				server.RecoverPanicStreamInterceptor(), // Last interceptor
			),
		),
	}

	grpcServer = grpc.NewServer(opts...)
	api.RegisterWalletServiceServer(grpcServer, server.NewWalletServer(
		cfg.NfcBridge,
		keyProvider,
		network,
		houstonService,
		keyValueStorage,
		resetDataFn,
		&recoverySubsystem,
		&recoveryToolSubsystem,
		&diagnosticsSubsystem,
		&securityCardSubsystem,
		&marketplaceSubsystem,
		&emergencyKitSubsystem,
		&backgroundActionsSubsystem,
		&ratesSubsystem,
	))

	lc := net.ListenConfig{}
	listener, err := lc.Listen(context.Background(), "unix", cfg.SocketPath)
	if err != nil {
		slog.Error("socket creation failure", "error", err)
		return err
	}

	go func() {
		if err := grpcServer.Serve(listener); err != nil {
			slog.Error("error when starting server goroutine", "error", err)
		}

		if otelSetup != nil {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()

			if err := otelSetup.Shutdown(ctx); err != nil {
				slog.Error("error when shutting down OpenTelemetry", "error", err)
			}
		}
	}()

	return nil
}

func StopServer() {
	if grpcServer == nil {
		slog.Warn("tried to stop server when none is running")
		return
	}
	grpcServer.Stop()
	if pool != nil {
		pool.Close()
		pool = nil
		libwallet.Pool = nil
	}
	newop.SetRates(nil)
}
