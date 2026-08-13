package presentation

import (
	"context"
	"encoding/hex"
	"log"
	"net"
	"os"
	"path"
	"strconv"
	"strings"
	"testing"
	"time"

	goerr "github.com/go-errors/errors"
	grpc_middleware "github.com/grpc-ecosystem/go-grpc-middleware"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/resolver"
	"google.golang.org/grpc/status"

	"google.golang.org/grpc"
	"google.golang.org/grpc/test/bufconn"

	"github.com/muun/libwallet"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/domain/action/challenge_keys"
	"github.com/muun/libwallet/domain/action/recovery"
	"github.com/muun/libwallet/domain/action/reset"
	apierrors "github.com/muun/libwallet/errors"
	"github.com/muun/libwallet/presentation/api"
	"github.com/muun/libwallet/recoverycode"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
	"github.com/muun/libwallet/walletdb"
)

var bufconnListener *bufconn.Listener
var walletServer = &WalletServer{}

// 127.0.0.1 instead of localhost to avoid problems with network interfaces in local env
const houstonUrl string = "http://127.0.0.1:8080" //nolint:staticcheck // TODO: const houstonUrl should be houstonURL

func defaultProvider() *service.TestProvider {
	return &service.TestProvider{
		ClientVersion:     "1205",
		ClientVersionName: "2.9.2",
		Language:          "en",
		ClientType:        "FALCON",
		BaseURL:           houstonUrl,
	}
}

func init() {
	walletServer.network = libwallet.Regtest()
	walletServer.houstonService = service.NewHoustonService(defaultProvider())
	walletServer.startChallengeSetup = challenge_keys.NewStartChallengeSetupAction(
		walletServer.houstonService,
	)

	// Initialize grpc server of WalletService with bufconn
	bufconnListener = bufconn.Listen(1024 * 1024)

	// Add our interceptors for panic/error recovery in order to test them.
	opts := []grpc.ServerOption{
		grpc.UnaryInterceptor(
			grpc_middleware.ChainUnaryServer(
				LoggingUnaryInterceptor(),
				RecoverUnknownErrorUnaryInterceptor(),
				RecoverPanicUnaryInterceptor(),
			),
		),
		grpc.StreamInterceptor(
			grpc_middleware.ChainStreamServer(
				LoggingStreamInterceptor(),
				RecoverUnknownErrorStreamInterceptor(),
				RecoverPanicStreamInterceptor(),
			),
		),
	}

	grpcServer := grpc.NewServer(opts...)
	api.RegisterWalletServiceServer(grpcServer, walletServer)

	go func() {
		if err := grpcServer.Serve(bufconnListener); err != nil {
			panic(err)
		}
	}()
}

// TestMain is run before all tests defined in this package
func TestMain(m *testing.M) {
	shouldRunHealthcheck := false

	for _, arg := range os.Args {
		if strings.Contains(arg, "-test.run=_Integration") {
			shouldRunHealthcheck = true
			break
		}
	}

	if shouldRunHealthcheck {
		log.Println("Running healthcheck setup for integration tests...")

		if err := waitForHealthcheck(); err != nil {
			log.Fatalf("Healthcheck failed: %v", err)
		}
	} else {
		log.Println("Skipping healthcheck setup (not running integration tests).")
	}

	code := m.Run()
	os.Exit(code)
}

// waitForHealthcheck pings the healthcheck endpoint until it gets a 200 OK
func waitForHealthcheck() error {
	timeout := 30 * time.Second
	interval := 500 * time.Millisecond
	deadline := time.Now().Add(timeout)
	for {
		if time.Now().After(deadline) {
			return goerr.Errorf("healthcheck failed after %s", timeout)
		}

		err := walletServer.houstonService.HealthCheck()

		if err == nil {
			log.Println("Healthcheck successful.")
			return nil
		}

		log.Println("Healthcheck failed:", err)
		time.Sleep(interval)
	}
}

func TestSaveAndGetAndDelete(t *testing.T) {

	t.Run("success when saving, reading and deleting a key-value pair", func(t *testing.T) {
		setupKeyValueStorage(t, buildTestMigrationPlan())

		// Initialize grpc client of WalletService with bufconn
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		// Create Value message for emergencyKitVersion
		emergencyKitVersion := int32(1234)
		value := api.Value_builder{IntValue: &emergencyKitVersion}.Build()

		// Create SaveRequest
		saveReq := api.SaveRequest_builder{
			Key:   "emergencyKitVersion",
			Value: value,
		}.Build()

		// Call grpc client with SaveRequest
		_, err := client.Save(ctx, saveReq)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Create GetRequest
		getRequest := api.GetRequest_builder{Key: "emergencyKitVersion"}.Build()

		// Call grpc client with GetRequest
		getResponse, err := client.Get(ctx, getRequest)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		want := int32(1234)
		got := getResponse.GetValue().GetIntValue()
		if got != want {
			t.Errorf("want %v, but got %v", want, got)
		}

		// Create DeleteRequest
		deleteReq := api.DeleteRequest_builder{
			Key: "emergencyKitVersion",
		}.Build()

		// Call grpc client with DeleteRequest
		_, err = client.Delete(ctx, deleteReq)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Call grpc client with GetRequest
		getResponse, err = client.Get(ctx, getRequest)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Verify response is null after deleting the key-value pair
		if !getResponse.GetValue().HasNullValue() {
			t.Errorf("want null value, but got a non-null value")
		}
	})

	t.Run("return error when SaveRequest does not have a key defined", func(t *testing.T) {

		setupKeyValueStorage(t, buildTestMigrationPlan())

		// Initialize grpc client of WalletService with bufconn
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		// Create grpc message with NullValue for emergencyKitVersion
		nullValue := api.NullValue_NULL_VALUE
		value := api.Value_builder{NullValue: &nullValue}.Build()

		// Create SaveRequest without defining a key
		saveReq := api.SaveRequest_builder{
			Value: value,
		}.Build()

		// Call grpc client with SaveRequest
		_, err := client.Save(ctx, saveReq)
		if err == nil {
			t.Fatalf("expect error")
		}

		grpcStatus := status.Convert(err)
		if grpcStatus.Code() != codes.InvalidArgument {
			t.Errorf("want %v, but got %v", codes.InvalidArgument, grpcStatus.Code())
		}
		wantErr := apierrors.ErrorCodes.ErrKeyEmpty.Message
		if grpcStatus.Message() != wantErr {
			t.Errorf("want %v, but got %v", wantErr, grpcStatus.Message())
		}

		errorDetail := getErrorDetail(t, grpcStatus)

		wantType := api.ErrorType_CLIENT
		gotType := errorDetail.GetType()
		if gotType != wantType {
			t.Errorf("want %v, but got %v", wantType, gotType)
		}

		wantMsg := apierrors.ErrorCodes.ErrKeyEmpty.Message
		gotMsg := errorDetail.GetMessage()
		if gotMsg != wantMsg {
			t.Errorf("want %v, but got %v", wantMsg, gotMsg)
		}

	})

	t.Run("return error when SaveRequest has an invalid key", func(t *testing.T) {

		setupKeyValueStorage(t, buildTestMigrationPlan())

		// Initialize grpc client of WalletService with bufconn
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		// Create grpc message with NullValue for emergencyKitVersion
		nullValue := api.NullValue_NULL_VALUE
		value := api.Value_builder{NullValue: &nullValue}.Build()

		// Create SaveRequest with an invalid key
		saveReq := api.SaveRequest_builder{
			Key:   "invalid-key",
			Value: value,
		}.Build()

		// Call grpc client with SaveRequest
		_, err := client.Save(ctx, saveReq)
		if err == nil {
			t.Fatalf("expect error")
		}

		// Verify we fail due to the invalid key
		grpcStatus := status.Convert(err)
		if grpcStatus.Code() != codes.Internal {
			t.Errorf("want %v, but got %v", codes.Internal, grpcStatus.Code())
		}

		errorDetail := getErrorDetail(t, grpcStatus)

		wantType := api.ErrorType_LIBWALLET
		gotType := errorDetail.GetType()
		if gotType != wantType {
			t.Errorf("want %v, but got %v", wantType, gotType)
		}

		wantMsg := apierrors.ErrorCodes.ErrUnknown.Message
		gotMsg := errorDetail.GetMessage()
		if gotMsg != wantMsg {
			t.Errorf("want %v, but got %v", wantMsg, gotMsg)
		}

		wantDevMsg := "failed to save key with given data: " +
			"classification not found for key: invalid-key"
		gotDevMsg := errorDetail.GetDeveloperMessage()
		if gotDevMsg != wantDevMsg {
			t.Errorf("want %v, but got %v", wantDevMsg, gotDevMsg)
		}

	})

	t.Run("success when saving a key with NullValue", func(t *testing.T) {

		setupKeyValueStorage(t, buildTestMigrationPlan())

		// Initialize grpc client of WalletService with bufconn
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		// Create grpc message with NullValue for emergencyKitVersion
		apiNull := api.NullValue_NULL_VALUE
		nullValue := api.Value_builder{NullValue: &apiNull}.Build()

		// Create SaveRequest
		saveReq := api.SaveRequest_builder{
			Key:   "emergencyKitVersion",
			Value: nullValue,
		}.Build()

		// Call grpc client with SaveRequest
		_, err := client.Save(ctx, saveReq)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Create GetRequest
		getRequest := api.GetRequest_builder{Key: "emergencyKitVersion"}.Build()

		// Call grpc client with GetRequest
		getResponse, err := client.Get(ctx, getRequest)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Verify response is null
		if !getResponse.GetValue().HasNullValue() {
			t.Errorf("want null value, but got a non-null value")
		}

	})
}

func TestSaveBatchAndGetBatch(t *testing.T) {

	t.Run("success when saving and reading key-value pairs in batches", func(t *testing.T) {
		setupKeyValueStorage(t, buildTestMigrationPlan())

		// Initialize grpc client of WalletService with bufconn
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		// Create Struct message with a map of key-values
		items := map[string]any{
			"emergencyKitVersion": int32(123),
			"primaryCurrency":     "USD",
			"email":               "pepe@test.com",
			"gcmToken":            nil,
			"isEmailVerified":     true,
		}
		protoItems, err := toProtoValueMap(items)
		if err != nil {
			t.Fatalf("failed to create Struct for items: %v", err)
		}

		// Create SaveBatchRequest
		saveBatchReq := api.SaveBatchRequest_builder{
			Items: protoItems,
		}.Build()

		// Call grpc client with SaveBatchRequest
		_, err = client.SaveBatch(ctx, saveBatchReq)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Create GetBatchRequest
		getBatchReq := api.GetBatchRequest_builder{
			Keys: []string{
				"primaryCurrency",
				"email",
				"isEmailVerified",
				"emergencyKitVersion",
				"gcmToken",
			},
		}.Build()

		// Call grpc client with GetBatchRequest
		getBatchResponse, err := client.GetBatch(ctx, getBatchReq)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		var want any
		var got any

		// Validate returned data
		want = "USD"
		got = getBatchResponse.GetItems().GetFields()["primaryCurrency"].GetStringValue()
		if got != want {
			t.Fatalf("want %v, but got %v", want, got)
		}

		want = "pepe@test.com"
		got = getBatchResponse.GetItems().GetFields()["email"].GetStringValue()
		if got != want {
			t.Fatalf("want %v, but got %v", want, got)
		}

		want = int32(123)
		got = getBatchResponse.GetItems().GetFields()["emergencyKitVersion"].GetIntValue()
		if got != want {
			t.Fatalf("want %v, but got %v", want, got)
		}

		if !getBatchResponse.GetItems().GetFields()["gcmToken"].HasNullValue() {
			t.Fatalf("want null value, but got a non-null value")
		}

		want = true
		got = getBatchResponse.GetItems().GetFields()["isEmailVerified"].GetBoolValue()
		if got != want {
			t.Fatalf("want %v, but got %v", want, got)
		}
	})

}

func TestGetByPrefix(t *testing.T) {
	t.Run("success when getting key-value pairs by prefix", func(t *testing.T) {
		setupKeyValueStorage(t, buildTestMigrationPlan())

		// Initialize grpc client of WalletService with bufconn
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		// Create Struct message with a map of key-values
		items := map[string]any{
			"featureFlag:useDiagnosticMode":     true,
			"featureFlag:isDogfood":             false,
			"featureFlag:supportsNfc":           true,
			"featureFlag:utxoSelectionStrategy": "LIFO",
			// Add a key that doesn't match the prefix
			"email": "some@email.com",
		}
		protoItems, err := toProtoValueMap(items)
		if err != nil {
			t.Fatalf("failed to create Struct for items: %v", err)
		}

		// Create SaveBatchRequest
		saveBatchReq := api.SaveBatchRequest_builder{
			Items: protoItems,
		}.Build()

		// Call grpc client with SaveBatchRequest
		_, err = client.SaveBatch(ctx, saveBatchReq)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Create GetByPrefixRequest
		getByPrefixReq := api.GetByPrefixRequest_builder{
			Prefix: "featureFlag:",
		}.Build()

		// Call grpc client with GetByPrefixRequest
		getByPrefixResponse, err := client.GetByPrefix(ctx, getByPrefixReq)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Validate returned data
		responseFields := getByPrefixResponse.GetItems().GetFields()

		if len(responseFields) != 4 {
			t.Fatalf("want 4 items, but got %v", len(responseFields))
		}

		_, emailIsPresent := responseFields["email"]
		if emailIsPresent {
			t.Fatalf("unexpected key 'email' in response")
		}

		var want any
		var got any

		want = true
		got = responseFields["featureFlag:useDiagnosticMode"].GetBoolValue()
		if got != want {
			t.Fatalf("want %v, but got %v for featureFlag:useDiagnosticMode", want, got)
		}

		want = false
		got = responseFields["featureFlag:isDogfood"].GetBoolValue()
		if got != want {
			t.Fatalf("want %v, but got %v for featureFlag:isDogfood", want, got)
		}

		want = true
		got = responseFields["featureFlag:supportsNfc"].GetBoolValue()
		if got != want {
			t.Fatalf("want %v, but got %v for featureFlag:supportsNfc", want, got)
		}

		want = "LIFO"
		got = responseFields["featureFlag:utxoSelectionStrategy"].GetStringValue()
		if got != want {
			t.Fatalf("want %v, but got %v for featureFlag:utxoSelectionStrategy", want, got)
		}
	})

	t.Run("success when no keys match prefix", func(t *testing.T) {
		setupKeyValueStorage(t, buildTestMigrationPlan())

		// Initialize grpc client of WalletService with bufconn
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		// Create GetByPrefixRequest
		getByPrefixReq := api.GetByPrefixRequest_builder{
			Prefix: "nonExistentPrefix:",
		}.Build()

		// Call grpc client with GetByPrefixRequest
		getByPrefixResponse, err := client.GetByPrefix(ctx, getByPrefixReq)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Validate that the map is empty
		responseFields := getByPrefixResponse.GetItems().GetFields()
		if len(responseFields) != 0 {
			t.Fatalf("want 0 items, but got %v", len(responseFields))
		}
	})
}

func TestResetData(t *testing.T) {
	t.Run("success when wiping all database entries", func(t *testing.T) {
		setupKeyValueStorage(t, buildTestMigrationPlan())

		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		// Save several values of different types.
		emergencyKitVersion := int32(7)
		_, err := client.Save(ctx, api.SaveRequest_builder{
			Key:   "emergencyKitVersion",
			Value: api.Value_builder{IntValue: &emergencyKitVersion}.Build(),
		}.Build())
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		gcmToken := "test-token"
		_, err = client.Save(ctx, api.SaveRequest_builder{
			Key:   "gcmToken",
			Value: api.Value_builder{StringValue: &gcmToken}.Build(),
		}.Build())
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// Reset the database.
		_, err = client.ResetData(ctx, nil)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		// All previously saved keys must return null after reset.
		for _, key := range []string{"emergencyKitVersion", "gcmToken"} {
			resp, err := client.Get(ctx, api.GetRequest_builder{Key: key}.Build())
			if err != nil {
				failWithGrpcErrorDetails(t, err)
			}
			if !resp.GetValue().HasNullValue() {
				t.Errorf("Get(%q) after ResetData: want null, got non-null", key)
			}
		}

		// DB must still accept writes after reset.
		newVersion := int32(1)
		_, err = client.Save(ctx, api.SaveRequest_builder{
			Key:   "emergencyKitVersion",
			Value: api.Value_builder{IntValue: &newVersion}.Build(),
		}.Build())
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		resp, err := client.Get(ctx, api.GetRequest_builder{Key: "emergencyKitVersion"}.Build())
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}
		if got := resp.GetValue().GetIntValue(); got != newVersion {
			t.Errorf("Get() after ResetData + Save(): want %d, got %d", newVersion, got)
		}
	})
}

func TestErrorInterceptors(t *testing.T) {

	t.Run("return internal error when rpc execution raises a panic", func(t *testing.T) {
		setupKeyValueStorage(t, buildTestMigrationPlan())

		// Initialize grpc client of WalletService with bufconn
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		// Assign nil to force a PANIC error
		walletServer.keyValueStorage = nil

		// Create Value message for emergencyKitVersion
		emergencyKitVersion := int32(1234)
		value := api.Value_builder{IntValue: &emergencyKitVersion}.Build()

		// Create SaveRequest
		saveReq := api.SaveRequest_builder{
			Key:   "emergencyKitVersion",
			Value: value,
		}.Build()

		// Call grpc client with SaveRequest
		_, err := client.Save(ctx, saveReq)
		if err == nil {
			t.Fatalf("expect error")
		}

		// Verify we fail with codes.INTERNAL
		grpcStatus := status.Convert(err)
		if grpcStatus.Code() != codes.Internal {
			t.Errorf("want %v, but got %v", codes.Internal, grpcStatus.Code())
		}

		// Verify we fail with error code ErrUnknown
		wantErr := apierrors.ErrorCodes.ErrUnknown.Message
		got := grpcStatus.Message()
		if got != wantErr {
			t.Errorf("want %v, but got %v", wantErr, got)
		}

		// Verify we fail catching the error message of the panic
		wantErr = "panic: runtime error: invalid memory address or nil pointer dereference"
		got = getErrorDetail(t, grpcStatus).GetDeveloperMessage()
		if got != wantErr {
			t.Errorf("want %v, but got %v", wantErr, got)
		}

	})

	t.Run("unary: return internal error when intercepting a generic error", func(t *testing.T) {

		// Create a generic error with fmt package
		wantDevMsg := "generic error for testing"
		handler := func(ctx context.Context, req any) (any, error) { //nolint:revive // TODO: use or remove ctx
			return nil, goerr.New(wantDevMsg)
		}

		interceptor := RecoverUnknownErrorUnaryInterceptor()
		_, err := interceptor(context.Background(), nil, nil, handler)
		if err == nil {
			t.Fatalf("expect error")
		}

		grpcStatus := status.Convert(err)

		// Verify we fail with codes.INTERNAL
		if grpcStatus.Code() != codes.Internal {
			t.Errorf("want %v, but got %v", codes.Internal, grpcStatus.Code())
		}

		// Verify we fail with error code ErrUnknown
		wantCode := int64(apierrors.ErrorCodes.ErrUnknown.Code)
		gotCode := getErrorDetail(t, grpcStatus).GetCode()
		if gotCode != wantCode {
			t.Errorf("want %v, but got %v", wantCode, gotCode)
		}

		// Verify we fail catching the error message
		got := getErrorDetail(t, grpcStatus).GetDeveloperMessage()
		if got != wantDevMsg {
			t.Errorf("want %v, but got %v", wantDevMsg, got)
		}

	})

	t.Run("unary: return internal error when intercepting unknown grpc error", func(t *testing.T) {

		// Create gRPC error with codes.Unknown
		errorMsg := "an unknown error for testing"
		unknownErrorStatus := status.New(codes.Unknown, errorMsg)

		handler := func(ctx context.Context, req any) (any, error) { //nolint:revive // TODO: use or remove ctx
			return nil, unknownErrorStatus.Err()
		}

		interceptor := RecoverUnknownErrorUnaryInterceptor()
		_, err := interceptor(context.Background(), nil, nil, handler)
		if err == nil {
			t.Fatalf("expect error")
		}

		grpcStatus := status.Convert(err)

		// Verify we fail with codes.INTERNAL
		if grpcStatus.Code() != codes.Internal {
			t.Errorf("want %v, but got %v", codes.Internal, grpcStatus.Code())
		}

		// Verify we fail with error code ErrUnknown
		wantCode := int64(apierrors.ErrorCodes.ErrUnknown.Code)
		gotCode := getErrorDetail(t, grpcStatus).GetCode()
		if gotCode != wantCode {
			t.Errorf("want %v, but got %v", wantCode, gotCode)
		}

		// Verify we fail catching the original error message
		wantDevMsg := "rpc error: code = Unknown desc = " + errorMsg
		gotDevMsg := getErrorDetail(t, grpcStatus).GetDeveloperMessage()
		if gotDevMsg != wantDevMsg {
			t.Errorf("want %v, but got %v", wantDevMsg, gotDevMsg)
		}

	})

	t.Run("stream: return internal error when intercepting a generic error", func(t *testing.T) {

		wantDevMsg := "generic error for testing"
		handler := func(_ any, _ grpc.ServerStream) error {
			return goerr.New(wantDevMsg)
		}

		interceptor := RecoverUnknownErrorStreamInterceptor()
		err := interceptor(nil, nil, nil, handler)
		if err == nil {
			t.Fatalf("expect error")
		}

		grpcStatus := status.Convert(err)

		// Verify we fail with codes.INTERNAL
		if grpcStatus.Code() != codes.Internal {
			t.Errorf("want %v, but got %v", codes.Internal, grpcStatus.Code())
		}

		// Verify we fail with error code ErrUnknown
		wantCode := int64(apierrors.ErrorCodes.ErrUnknown.Code)
		gotCode := getErrorDetail(t, grpcStatus).GetCode()
		if gotCode != wantCode {
			t.Errorf("want %v, but got %v", wantCode, gotCode)
		}

		// Verify we fail catching the error message
		got := getErrorDetail(t, grpcStatus).GetDeveloperMessage()
		if got != wantDevMsg {
			t.Errorf("want %v, but got %v", wantDevMsg, got)
		}

	})

	t.Run("stream: return internal error when intercepting unknown grpc error", func(t *testing.T) {

		errorMsg := "an unknown error for testing"
		unknownErrorStatus := status.New(codes.Unknown, errorMsg)

		handler := func(_ any, _ grpc.ServerStream) error {
			return unknownErrorStatus.Err()
		}

		interceptor := RecoverUnknownErrorStreamInterceptor()
		err := interceptor(nil, nil, nil, handler)
		if err == nil {
			t.Fatalf("expect error")
		}

		grpcStatus := status.Convert(err)

		// Verify we fail with codes.INTERNAL
		if grpcStatus.Code() != codes.Internal {
			t.Errorf("want %v, but got %v", codes.Internal, grpcStatus.Code())
		}

		// Verify we fail with error code ErrUnknown
		wantCode := int64(apierrors.ErrorCodes.ErrUnknown.Code)
		gotCode := getErrorDetail(t, grpcStatus).GetCode()
		if gotCode != wantCode {
			t.Errorf("want %v, but got %v", wantCode, gotCode)
		}

		// Verify we fail catching the original error message
		wantDevMsg := "rpc error: code = Unknown desc = " + errorMsg
		gotDevMsg := getErrorDetail(t, grpcStatus).GetDeveloperMessage()
		if gotDevMsg != wantDevMsg {
			t.Errorf("want %v, but got %v", wantDevMsg, gotDevMsg)
		}

	})

}

func TestFinishRecoveryCodeSetupEndpoint_Integration(t *testing.T) {
	setupKeyValueStorage(t, storage.BuildKVMigrationPlan())

	recoveryCode := recoverycode.Generate()
	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}

	userPrivateKey, err := libwallet.NewHDPrivateKey(
		[]byte("1234567891011121314"),
		libwallet.Regtest(),
	)
	if err != nil {
		t.Fatal(err)
	}

	encryptedPrivateKey, err := libwallet.KeyEncrypt(userPrivateKey, recoveryCode)
	if err != nil {
		t.Fatal(err)
	}

	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	createFirstSessionOkJson := createFirstSession( //nolint:staticcheck // TODO: var createFirstSessionOkJson should be createFirstSessionOkJSON
		t,
		userPrivateKey.PublicKey(),
	)
	muunPublicKey, err := libwallet.NewHDPublicKeyFromString(
		createFirstSessionOkJson.CosigningPublicKey.Key,
		createFirstSessionOkJson.CosigningPublicKey.Path,
		libwallet.Regtest())
	if err != nil {
		t.Fatal(err)
	}

	walletServer.keyProvider = NewMockKeyProvider(userPrivateKey, muunPublicKey, 0)
	computeAndStoreEncryptedMuunKeyAction := recovery.NewComputeAndStoreEncryptedMuunKeyAction(
		walletServer.keyValueStorage,
		walletServer.keyProvider,
	)
	walletServer.finishChallengeSetup = challenge_keys.NewFinishChallengeSetupAction(
		walletServer.houstonService,
		walletServer.keyValueStorage,
		computeAndStoreEncryptedMuunKeyAction,
	)

	_, err = walletServer.StartChallengeSetup(
		context.Background(),
		api.ChallengeSetupRequest_builder{
			Type:                "RECOVERY_CODE",
			PublicKey:           hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
			Salt:                "dfb80ea8c30959e8",
			EncryptedPrivateKey: encryptedPrivateKey,
			Version:             2,
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}

	_, err = walletServer.FinishRecoveryCodeSetup(
		context.Background(),
		api.FinishRecoveryCodeSetupRequest_builder{
			RecoveryCodePublicKeyHex: hex.EncodeToString(
				recoveryCodePublicKey.SerializeCompressed(),
			),
		}.Build(),
	)
	if err != nil {
		t.Fatal(err)
	}
}

func createFirstSession(t *testing.T, key *libwallet.HDPublicKey) model.CreateFirstSessionOkJson {
	provider := defaultProvider()
	strClientVersion, err := strconv.Atoi(provider.ClientVersion)
	if err != nil {
		t.Fatal(err)
	}
	sessionJson := model.CreateFirstSessionJson{ //nolint:staticcheck // TODO: var sessionJson should be sessionJSON
		Client: model.ClientJson{
			Type:        provider.ClientType,
			BuildType:   "debug",
			Version:     strClientVersion,
			VersionName: provider.ClientVersionName,
			Language:    provider.Language,
		},
		GcmToken:        nil,
		PrimaryCurrency: "USD",
		BasePublicKey: model.PublicKeyJson{
			Key:  key.String(),
			Path: "m/schema:1'/recovery:1'",
		},
	}
	sessionOkJson, err := walletServer.houstonService.CreateFirstSession( //nolint:staticcheck // TODO: var sessionOkJson should be sessionOkJSON
		sessionJson,
	)
	if err != nil {
		t.Fatal(err)
	}
	return sessionOkJson
}

func setupKeyValueStorage(t *testing.T, migrationPlan []storage.Migration) {
	dbPath := path.Join(t.TempDir(), "test.db")
	var schema map[string]storage.Classification
	pool, err := walletdb.NewPool(dbPath, func(db *walletdb.DB) error {
		var migErr error
		schema, migErr = storage.RunKeyValueMigrations(db, migrationPlan)
		return migErr
	})
	if err != nil {
		t.Fatalf("failed to open db: %v", err)
	}
	walletServer.keyValueStorage = storage.NewKeyValueStorage(
		pool.NewKeyValueRepository(),
		schema,
	)
	walletServer.resetData = reset.NewResetDataAction(dbPath, pool, migrationPlan)
}

func newGrpcClient(t *testing.T) (*grpc.ClientConn, context.Context) {
	ctx := context.Background()
	resolver.SetDefaultScheme("passthrough")
	conn, err := grpc.NewClient(
		"bufnet",
		grpc.WithContextDialer(dialer()),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatalf("failed to dial bufnet: %v", err)
	}
	return conn, ctx
}

func dialer() func(context.Context, string) (net.Conn, error) {
	return func(ctx context.Context, s string) (net.Conn, error) { //nolint:revive // TODO: use or remove ctx
		return bufconnListener.Dial()
	}
}

func getErrorDetail(t *testing.T, grpcStatus *status.Status) *api.ErrorDetail {
	for _, d := range grpcStatus.Details() {
		switch detailsInfo := d.(type) {
		case *api.ErrorDetail:
			return detailsInfo
		default:
			t.Errorf("Unexpected type for detailsInfo")
			t.Fatalf("Error details = %s", d)
		}
	}
	t.Fatalf("gRPC error details not found")
	return nil
}

func failWithGrpcErrorDetails(t testing.TB, err error) {
	t.Helper()
	t.Errorf("Error = %v", err)
	grpcStatus := status.Convert(err)
	for _, d := range grpcStatus.Details() {
		switch detailsInfo := d.(type) {
		case *api.ErrorDetail:
			t.Fatalf("Error details = %s", detailsInfo.String())
		default:
			t.Errorf("Unexpected type for detailsInfo")
			t.Fatalf("Error details = %s", d)
		}
	}
}

func buildTestMigrationPlan() []storage.Migration {
	return []storage.Migration{
		{Description: "Schema for testing purpose", Changes: []storage.Change{
			storage.Define(
				"email",
				storage.NoAutoBackup,
				storage.NotApplicable,
				false,
				&storage.StringType{},
			),
			storage.Define(
				"emergencyKitVersion",
				storage.NoAutoBackup,
				storage.NotApplicable,
				false,
				&storage.IntType{},
			),
			storage.Define(
				"gcmToken",
				storage.NoAutoBackup,
				storage.NotApplicable,
				false,
				&storage.StringType{},
			),
			storage.Define(
				"isEmailVerified",
				storage.NoAutoBackup,
				storage.NotApplicable,
				false,
				&storage.BoolType{},
			),
			storage.Define(
				"primaryCurrency",
				storage.NoAutoBackup,
				storage.NotApplicable,
				false,
				&storage.StringType{},
			),
			storage.Define(
				"featureFlag:useDiagnosticMode",
				storage.NoAutoBackup,
				storage.NotApplicable,
				false,
				&storage.BoolType{},
			),
			storage.Define(
				"featureFlag:isDogfood",
				storage.NoAutoBackup,
				storage.NotApplicable,
				false,
				&storage.BoolType{},
			),
			storage.Define(
				"featureFlag:supportsNfc",
				storage.NoAutoBackup,
				storage.NotApplicable,
				false,
				&storage.BoolType{},
			),
			storage.Define(
				"featureFlag:utxoSelectionStrategy",
				storage.NoAutoBackup,
				storage.NotApplicable,
				false,
				&storage.StringType{},
			),
		}},
	}
}

type mockKeyProvider struct {
	userPrivateKey  *libwallet.HDPrivateKey
	muunPublicKey   *libwallet.HDPublicKey
	maxDerivedIndex int
}

func NewMockKeyProvider(
	userPrivateKey *libwallet.HDPrivateKey,
	muunPublicKey *libwallet.HDPublicKey,
	maxDerivedIndex int,
) keys.KeyProvider {
	return &mockKeyProvider{
		userPrivateKey:  userPrivateKey,
		muunPublicKey:   muunPublicKey,
		maxDerivedIndex: maxDerivedIndex,
	}
}

func (m *mockKeyProvider) UserPrivateKey() (*libwallet.HDPrivateKey, error) {
	return m.userPrivateKey, nil
}

func (m *mockKeyProvider) UserPublicKey() (*libwallet.HDPublicKey, error) {
	return m.userPrivateKey.PublicKey(), nil
}

func (m *mockKeyProvider) MuunPublicKey() (*libwallet.HDPublicKey, error) {
	return m.muunPublicKey, nil
}

func (m *mockKeyProvider) MaxDerivedIndex() int {
	return m.maxDerivedIndex
}

func (m *mockKeyProvider) EncryptedMuunPrivateKey() (*libwallet.EncryptedPrivateKeyInfo, error) {
	return nil, goerr.New("not implemented")
}

func (m *mockKeyProvider) SetMaxDerivedIndex(maxDerivedIndex int) {
	m.maxDerivedIndex = maxDerivedIndex
}
