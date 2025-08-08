package presentation

import (
	"context"
	"encoding/hex"
	"errors"
	"github.com/grpc-ecosystem/go-grpc-middleware"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/resolver"
	"google.golang.org/grpc/status"
	"net"
	"path"
	"strconv"
	"testing"

	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/muun/libwallet"
	apierrors "github.com/muun/libwallet/errors"
	"github.com/muun/libwallet/presentation/api"
	"github.com/muun/libwallet/recoverycode"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
	"google.golang.org/grpc"
	"google.golang.org/grpc/test/bufconn"
)

var bufconnListener *bufconn.Listener
var walletServer = &WalletServer{}

const houstonUrl string = "http://localhost:8080"

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

	// Initialize grpc server of WalletService with bufconn
	bufconnListener = bufconn.Listen(1024 * 1024)

	// Add our interceptor for panic in order to test it.
	opts := []grpc.ServerOption{
		grpc.UnaryInterceptor(
			grpc_middleware.ChainUnaryServer(
				LoggingInterceptor(),
				RecoverUnknownErrorInterceptor(),
				RecoverPanicInterceptor(),
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

func TestSaveAndGetAndDelete(t *testing.T) {

	t.Run("success when saving, reading and deleting a key-value pair", func(t *testing.T) {
		setupKeyValueStorage(t)

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

		setupKeyValueStorage(t)

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

		setupKeyValueStorage(t)

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

		wantDevMsg := "failed to save key with given data: classification not found for key: invalid-key"
		gotDevMsg := errorDetail.GetDeveloperMessage()
		if gotDevMsg != wantDevMsg {
			t.Errorf("want %v, but got %v", wantDevMsg, gotDevMsg)
		}

	})

	t.Run("success when saving a key with NullValue", func(t *testing.T) {

		setupKeyValueStorage(t)

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
		setupKeyValueStorage(t)

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
			Keys: []string{"primaryCurrency", "email", "isEmailVerified", "emergencyKitVersion", "gcmToken"},
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

func TestErrorInterceptors(t *testing.T) {

	t.Run("return internal error when rpc execution raises a panic", func(t *testing.T) {
		setupKeyValueStorage(t)

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

	t.Run("return internal error when intercepting a generic error", func(t *testing.T) {

		// Create a generic error with fmt package
		wantDevMsg := "generic error for testing"
		handler := func(ctx context.Context, req any) (any, error) {
			return nil, errors.New(wantDevMsg)
		}

		interceptor := RecoverUnknownErrorInterceptor()
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

	t.Run("return internal error when intercepting unknown grpc error", func(t *testing.T) {

		// Create gRPC error with codes.Unknown
		errorMsg := "an unknown error for testing"
		unknownErrorStatus := status.New(codes.Unknown, errorMsg)

		handler := func(ctx context.Context, req any) (any, error) {
			return nil, unknownErrorStatus.Err()
		}

		interceptor := RecoverUnknownErrorInterceptor()
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

}

func TestFinishRecoveryCodeSetupEndpoint_Integration(t *testing.T) {
	encryptedPrivateKey := "v1:512:1:8:582626b7244e1e72:01b0df9b1ffbdfe8eabae00a2c208317:0a6593c59927797f6a850f1775792378b81078ba29c203b41fac51d6d62069fbb5306e6585c7abab06905d86cc01bc3c0302087edec71c9018191e68e72348e06f2b87c4b5ee0efb55c99fd9f3703ebfc94127bebd41fa72bced4747718a5c43219dd010aa2e9be848e387b3c29f4df3:6d2f736368656d613a31272f7265636f766572793a3127"

	seed, err := hdkeychain.GenerateSeed(64 /*bytes*/)
	if err != nil {
		t.Fatalf("seed generation failed")
	}
	master, err := hdkeychain.NewMaster(seed, walletServer.network.ToParams())
	if err != nil {
		t.Fatalf("master seed generation failed")
	}
	masterPublic, err := master.Neuter()
	if err != nil {
		t.Fatalf("derivation failed")
	}

	recoveryCode := recoverycode.Generate()
	recoveryCodePrivateKey, err := recoverycode.ConvertToKey(recoveryCode, "")
	if err != nil {
		t.Fatal(err)
	}

	recoveryCodePublicKey := recoveryCodePrivateKey.PubKey()

	createFirstSession(t)

	_, err = walletServer.houstonService.ChallengeKeySetupStart(model.ChallengeSetupJson{
		Type:                "RECOVERY_CODE",
		PublicKey:           hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
		EncryptedPrivateKey: encryptedPrivateKey,
		Version:             2,
		Salt:                "dfb80ea8c30959e8",
	})
	if err != nil {
		t.Fatal(err)
	}

	_, err = walletServer.FinishRecoveryCodeSetup(context.Background(), api.FinishRecoveryCodeSetupRequest_builder{
		ExtendedServerCosigningPublicKeyBase58: masterPublic.String(),
		RecoveryCodePublicKeyHex:               hex.EncodeToString(recoveryCodePublicKey.SerializeCompressed()),
	}.Build())
	if err != nil {
		t.Fatal(err)
	}
}

func createFirstSession(t *testing.T) model.CreateFirstSessionOkJson {
	provider := defaultProvider()
	strClientVersion, err := strconv.Atoi(provider.ClientVersion)
	if err != nil {
		t.Fatal(err)
	}
	sessionJson := model.CreateFirstSessionJson{
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
			Key:  "tpubDAygaiK3eZ9hpC3aQkxtu5fGSTK4P7QKTwwGExN8hGZytjpEfsrUjtM8ics8Y7YLrvf1GLBZTFjcpmkEP1KKTRyo8D2ku5zz49bRudDrngd",
			Path: "m/schema:1'/recovery:1'",
		},
	}
	sessionOkJson, err := walletServer.houstonService.CreateFirstSession(sessionJson)
	if err != nil {
		t.Fatal(err)
	}
	return sessionOkJson
}

func setupKeyValueStorage(t *testing.T) {
	// Create a new empty DB providing a new dataFilePath
	dataFilePath := path.Join(t.TempDir(), "test.db")
	keyValueStorage := storage.NewKeyValueStorage(dataFilePath, buildStorageSchemaForTests())

	// For testing purpose, change reference to this new keyValueStorage in order to have a new empty DB
	walletServer.keyValueStorage = keyValueStorage
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
	return func(ctx context.Context, s string) (net.Conn, error) {
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

func buildStorageSchemaForTests() map[string]storage.Classification {
	return map[string]storage.Classification{
		"email": {
			BackupType:       storage.NoAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.StringType{},
		},
		"emergencyKitVersion": {
			BackupType:       storage.NoAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.IntType{},
		},
		"gcmToken": {
			BackupType:       storage.NoAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.StringType{},
		},
		"isEmailVerified": {
			BackupType:       storage.NoAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.BoolType{},
		},
		"primaryCurrency": {
			BackupType:       storage.NoAutoBackup,
			BackupSecurity:   storage.NotApplicable,
			SecurityCritical: false,
			ValueType:        &storage.StringType{},
		},
	}
}
