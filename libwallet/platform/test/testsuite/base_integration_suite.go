package testsuite

import (
	"context"
	"net"
	"path"
	"strings"
	"testing"
	"time"

	grpc_middleware "github.com/grpc-ecosystem/go-grpc-middleware"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"go.opentelemetry.io/otel/baggage"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"

	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/base/data/walletdb"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/platform/concurrency/memlock"
	"github.com/muun/libwallet/platform/observability/otel"
	"github.com/muun/libwallet/platform/observability/tracing"
	"github.com/muun/libwallet/platform/test/testenv"
	"github.com/muun/libwallet/server"
	"github.com/muun/libwallet/server/api"
)

// Run takes a test suite and runs all the tests attached to it.
// This function is meant to run integration tests.
func Run(t *testing.T, s suite.TestingSuite) {
	if testing.Short() {
		t.Skip("skipping integration test suite")
	}
	suite.Run(t, s)
}

// BaseIntegrationSuite is the base suite to be used for integration test suites.
// It contains logic to be applied to all integration tests.
// Embed this struct in a concrete suite struct instead of suite.Suite.
type BaseIntegrationSuite struct {
	suite.Suite

	// Overrides testify default so that all failed assertions stop the test execution.
	*require.Assertions

	// Ctx is the per-test context to be used during tests.
	// Carries test configuration.
	Ctx context.Context

	// otel is the otel.Setup that should be registered for each test.
	otel *otel.Setup

	// KvStore is an initialized kv storage with the migrations defined in KvStoreMigrations.
	// It's reset for every test.
	KvStore *storage.KeyValueStorage

	// ResetData is the function used to reset the KvStore.
	ResetData func() error

	Locker memlock.NamedLocker

	Network *libwallet.Network

	// Client is an gRPC client for a server.WalletServer.
	// This field is populated only if StartWalletServer was called during SetupSuite.
	Client api.WalletServiceClient
}

// SetupSuite is run automatically before all tests in the suite.
// If the concrete suite overrides this method, then it needs to be manually called.
func (s *BaseIntegrationSuite) SetupSuite() {
	s.Assertions = require.New(s.T())

	s.Ctx = s.T().Context()

	s.otel = s.setupOpenTelemetry(s.Ctx)

	s.setupKvStore()

	s.Network = libwallet.Regtest()
}

// SetupTest is automatically run before each test in the suite.
// If the concrete suite overrides this method, then it needs to be manually called.
func (s *BaseIntegrationSuite) SetupTest() {
	s.Assertions = require.New(s.T())

	s.Ctx = s.T().Context()

	s.Ctx = s.startTracing(s.Ctx)

	s.NoError(s.ResetData())
}

// KvStoreMigrations returns the migrations to be applied for this integration test.
// The concrete suite may override this method.
func (s *BaseIntegrationSuite) KvStoreMigrations() []storage.Migration {
	return storage.BuildKVMigrationPlan()
}

// StartWalletServer starts a gRPC server implementing the server.WalletServer provided.
// It populates the BaseIntegrationSuite.Client able to call it.
// Any concrete suite may decide to call this method on SetupSuite to test against the real server.
func (s *BaseIntegrationSuite) StartWalletServer(walletServer WalletServerBuilder) {
	walletServer.KeyValueStorage = s.KvStore
	walletServer.ResetData = s.ResetData
	walletServer.Network = s.Network

	grpcServer := grpc.NewServer(
		grpc.UnaryInterceptor(grpc_middleware.ChainUnaryServer(
			server.TracingUnaryInterceptor(nil, nil),
			server.RecoverUnknownErrorUnaryInterceptor(),
			server.RecoverPanicUnaryInterceptor(),
		)),
	)
	api.RegisterWalletServiceServer(grpcServer, walletServer.Build())

	listener := bufconn.Listen(1024 * 1024)
	go func() {
		_ = grpcServer.Serve(listener)
	}()
	s.T().Cleanup(grpcServer.Stop)

	conn, err := grpc.NewClient(
		"passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) {
			return listener.DialContext(ctx)
		}),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	s.NoError(err)
	s.T().Cleanup(func() {
		_ = conn.Close()
	})

	s.Client = api.NewWalletServiceClient(conn)
}

// setupOpenTelemetry creates and saves a new otel.Setup to be used for each test.
func (s *BaseIntegrationSuite) setupOpenTelemetry(ctx context.Context) *otel.Setup {
	env, err := testenv.LoadEnvironmentIntoMap("local_vars.env")
	s.NoError(err)

	otelSetup, err := otel.NewSetup(ctx, otel.NewConfigFromMap(env))
	s.NoError(err)

	// The ctx in Shutdown is just used for timeouts. Give shutdown 10 seconds.
	s.T().Cleanup(func() {
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = otelSetup.Shutdown(shutdownCtx)
	})

	return otelSetup
}

// startTracing opens the per-test root span, adds common tags, and closes root span on cleanup.
func (s *BaseIntegrationSuite) startTracing(ctx context.Context) context.Context {
	ctx = tracing.WithOpenTelemetrySetup(ctx, s.otel)

	name := s.T().Name()

	var testSuite, testCase string
	if nameParts := strings.SplitN(name, "/", 2); len(nameParts) == 2 {
		testSuite = nameParts[0]
		testCase = nameParts[1]
	} else {
		testCase = name
	}

	if testSuite != "" {
		ctx = s.addPropagatedTag(ctx, "test_suite", testSuite)
	}
	if testCase != "" {
		ctx = s.addPropagatedTag(ctx, "test_case", testCase)
	}

	ctx, trace := tracing.StartWithoutSampling(ctx, name, true)
	s.T().Cleanup(func() { trace.Finish() })

	s.T().Cleanup(func() {
		status := "success"
		switch {
		case s.T().Failed():
			status = "failed"
		case s.T().Skipped():
			status = "skipped"
		}
		trace.AddTag("test_status", status)
	})

	return ctx
}

// addPropagatedTag adds a tag to the OTel baggage to be attached to all spans created.
func (s *BaseIntegrationSuite) addPropagatedTag(
	ctx context.Context,
	key, value string,
) context.Context {
	member, err := baggage.NewMemberRaw(key, value)
	s.NoError(err)

	bag, err := baggage.FromContext(ctx).SetMember(member)
	s.NoError(err)

	return baggage.ContextWithBaggage(ctx, bag)
}

func (s *BaseIntegrationSuite) setupKvStore() {
	migrationPlan := s.KvStoreMigrations()

	dbPath := path.Join(s.T().TempDir(), "wallet.db")
	runMigrations := func(db *walletdb.DB) error {
		_, err := storage.RunKeyValueMigrations(db, migrationPlan)
		return err
	}

	var schema map[string]storage.Classification
	pool, err := walletdb.NewPool(dbPath, func(db *walletdb.DB) error {
		var migrationErr error
		schema, migrationErr = storage.RunKeyValueMigrations(db, migrationPlan)
		return migrationErr
	})
	s.NoError(err)
	s.T().Cleanup(pool.Close)

	s.KvStore = storage.NewKeyValueStorage(pool.NewKeyValueRepository(), schema)
	s.ResetData = func() error {
		return walletdb.ResetDB(dbPath, pool, runMigrations)
	}
}
