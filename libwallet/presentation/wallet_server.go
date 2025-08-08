package presentation

import (
	"bytes"
	"context"
	"encoding/hex"
	"fmt"
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/google/uuid"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/domain/action/challenge_keys"
	"github.com/muun/libwallet/domain/action/diagnostic_mode_reports"
	"github.com/muun/libwallet/domain/action/nfc"
	"github.com/muun/libwallet/domain/diagnostic_mode"
	"github.com/muun/libwallet/domain/recovery"
	"github.com/muun/libwallet/electrum"
	apierrors "github.com/muun/libwallet/errors"
	"github.com/muun/libwallet/presentation/api"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
	"google.golang.org/grpc"
	"google.golang.org/protobuf/types/known/emptypb"
)

type WalletServer struct {
	api.UnsafeWalletServiceServer
	nfcBridge               app_provided_data.NfcBridge
	keysProvider            app_provided_data.KeyProvider
	network                 *libwallet.Network
	houstonService          *service.HoustonService
	keyValueStorage         *storage.KeyValueStorage
	startChallengeSetup     *challenge_keys.StartChallengeSetupAction
	submitDiagnostic        *diagnostic_mode_reports.SubmitDiagnosticAction
	pairSecurityCard        *nfc.PairSecurityCardAction
	resetSecurityCard       *nfc.ResetSecurityCardAction
	signMessageSecurityCard *nfc.SignMessageSecurityCardAction
}

func NewWalletServer(
	nfcBridge app_provided_data.NfcBridge,
	keysProvider app_provided_data.KeyProvider,
	network *libwallet.Network,
	houstonService *service.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	startChallengeSetup *challenge_keys.StartChallengeSetupAction,
	submitDiagnostic *diagnostic_mode_reports.SubmitDiagnosticAction,
	pairSecurityCard *nfc.PairSecurityCardAction,
	resetSecurityCard *nfc.ResetSecurityCardAction,
	signMessageSecurityCard *nfc.SignMessageSecurityCardAction,
) *WalletServer {

	return &WalletServer{
		nfcBridge:               nfcBridge,
		keysProvider:            keysProvider,
		network:                 network,
		houstonService:          houstonService,
		keyValueStorage:         keyValueStorage,
		startChallengeSetup:     startChallengeSetup,
		submitDiagnostic:        submitDiagnostic,
		pairSecurityCard:        pairSecurityCard,
		resetSecurityCard:       resetSecurityCard,
		signMessageSecurityCard: signMessageSecurityCard,
	}
}

// Check we actually implement the interface
var _ api.WalletServiceServer = (*WalletServer)(nil)

func (WalletServer) DeleteWallet(context.Context, *emptypb.Empty) (*api.OperationStatus, error) {
	// For now, do nothing. This will probably change in the future.
	return api.OperationStatus_builder{
		Status: "ok",
	}.Build(), nil
}

func (ws WalletServer) NfcTransmit(ctx context.Context, req *api.NfcTransmitRequest) (*api.NfcTransmitResponse, error) {

	fmt.Printf("WalletServer: nfcTransmit")
	slog.Debug("WalletServer: nfcTransmit")

	nfcBridgeResponse, err := ws.nfcBridge.Transmit(req.GetApduCommand())
	if err != nil {
		// TODO error logging
		return nil, err
	}

	return api.NfcTransmitResponse_builder{
		ApduResponse: nfcBridgeResponse.Response,
		StatusCode:   nfcBridgeResponse.StatusCode,
	}.Build(), nil
}

func (ws WalletServer) SetupSecurityCard(ctx context.Context, message *emptypb.Empty) (*api.XpubResponse, error) {
	extendedPublicKey, err := ws.pairSecurityCard.Run()
	if err != nil {
		return nil, fmt.Errorf("error pairing security card: %w", err)
	}

	base58Xpub := base58.Encode(extendedPublicKey.RawBytes)

	return api.XpubResponse_builder{
		Base58Xpub: base58Xpub,
	}.Build(), nil
}

func (ws WalletServer) ResetSecurityCard(ctx context.Context, message *emptypb.Empty) (*emptypb.Empty, error) {
	err := ws.resetSecurityCard.Run()
	if err != nil {
		return nil, err
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) SignMessageSecurityCard(
	ctx context.Context,
	request *api.SignMessageSecurityCardRequest,
) (*api.SignMessageSecurityCardResponse, error) {

	signedMessage, err := ws.signMessageSecurityCard.Run(request.GetMessageHex())
	if err != nil {
		return nil, err
	}

	base58SignedMessage := base58.Encode(signedMessage.RawBytes)

	return api.SignMessageSecurityCardResponse_builder{
		SignedMessageHex: base58SignedMessage,
		IsValidated:      true,
	}.Build(), nil
}

func (ws WalletServer) StartDiagnosticSession(ctx context.Context, empty *emptypb.Empty) (*api.DiagnosticSessionDescriptor, error) {
	sessionId := uuid.NewString()
	err := diagnostic_mode.AddDiagnosticSession(&diagnostic_mode.DiagnosticSessionData{
		Id: sessionId,
	})
	if err != nil {
		return nil, err
	}
	return api.DiagnosticSessionDescriptor_builder{
		SessionId: sessionId,
	}.Build(), nil
}

func (ws WalletServer) PerformDiagnosticScanForUtxos(descriptor *api.DiagnosticSessionDescriptor, g grpc.ServerStreamingServer[api.ScanProgressUpdate]) error {
	sessionId := descriptor.GetSessionId()

	if sessionData, ok := diagnostic_mode.GetDiagnosticSession(sessionId); ok {
		sessionData.DebugLog = bytes.NewBuffer(nil)
		textHandler := slog.NewTextHandler(sessionData.DebugLog, &slog.HandlerOptions{
			Level: slog.LevelInfo,
		})

		var servers []string = electrum.PublicServers
		reports, err := diagnostic_mode.ScanAddresses(ws.keysProvider, electrum.NewServerProvider(servers), ws.network, slog.New(textHandler))
		if err != nil {
			return err
		}

		for report := range reports {
			for _, utxo := range report.UtxosFound {
				_ = g.Send(api.ScanProgressUpdate_builder{
					FoundUtxoReport: api.FoundUtxoReport_builder{
						Address: utxo.Address.Address(),
						Amount:  utxo.Amount,
					}.Build(),
				}.Build())
			}
		}

		return g.Send(api.ScanProgressUpdate_builder{
			ScanComplete: api.ScanComplete_builder{
				Status: "DONE",
			}.Build(),
		}.Build())
	} else {
		return fmt.Errorf("invalid sessionId %s", descriptor.GetSessionId())
	}
}

func (ws WalletServer) SubmitDiagnosticLog(ctx context.Context, descriptor *api.DiagnosticSessionDescriptor) (*api.DiagnosticSubmitStatus, error) {
	sessionId := descriptor.GetSessionId()
	if session, ok := diagnostic_mode.GetDiagnosticSession(sessionId); ok {
		err := ws.submitDiagnostic.Run(sessionId, session.DebugLog.String())
		if err != nil {
			return nil, err
		}

		diagnostic_mode.DeleteDiagnosticSession(sessionId)
		return api.DiagnosticSubmitStatus_builder{
			StatusCode:    200,
			StatusMessage: "OK",
		}.Build(), nil
	} else {
		return nil, fmt.Errorf("invalid sessionId %s", descriptor.GetSessionId())
	}
}

// StartChallengeSetup is part of a migration test.
// This is a minimal switch to call houston from libwallet instead of from native code.
// Do NOT treat this as a reference.
// Future implementations should move native logic as much as possible to libwallet instead of duplicating this pattern.
func (ws WalletServer) StartChallengeSetup(ctx context.Context, req *api.ChallengeSetupRequest) (*api.SetupChallengeResponse, error) {

	challengeSetupJson := model.ChallengeSetupJson{
		Type:                req.GetType(),
		PublicKey:           req.GetPublicKey(),
		Salt:                req.GetSalt(),
		EncryptedPrivateKey: req.GetEncryptedPrivateKey(),
		Version:             int(req.GetVersion()),
	}

	setupChallengeResponseJson, err := ws.startChallengeSetup.Run(challengeSetupJson)
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to start challenge setup: %w", err))
	}

	return api.SetupChallengeResponse_builder{
		MuunKey:            setupChallengeResponseJson.MuunKey,
		MuunKeyFingerprint: setupChallengeResponseJson.MuunKeyFingerprint,
	}.Build(), nil
}

func (ws WalletServer) FinishRecoveryCodeSetup(ctx context.Context, keys *api.FinishRecoveryCodeSetupRequest) (*emptypb.Empty, error) {
	extendedServerCosigningPublicKey, err := hdkeychain.NewKeyFromString(keys.GetExtendedServerCosigningPublicKeyBase58())
	if err != nil {
		return nil, fmt.Errorf("error parsing extended server cosigning key: %w", err)
	}

	if extendedServerCosigningPublicKey.IsPrivate() {
		return nil, fmt.Errorf("extended server cosigning key must be public")
	}

	recoveryCodePublicKey, err := hexToPublicKey(keys.GetRecoveryCodePublicKeyHex())
	if err != nil {
		return nil, fmt.Errorf("error parsing recovery code public key: %w", err)
	}

	err = recovery.FinishRecoveryCodeSetupStub(ws.houstonService, *extendedServerCosigningPublicKey, *recoveryCodePublicKey)
	if err != nil {
		return nil, err
	}
	return &emptypb.Empty{}, nil
}

func hexToPublicKey(keyHex string) (*btcec.PublicKey, error) {

	keyBytes, err := hex.DecodeString(keyHex)
	if err != nil {
		return nil, err
	}

	return btcec.ParsePubKey(keyBytes)
}

func (ws WalletServer) Save(_ context.Context, req *api.SaveRequest) (*emptypb.Empty, error) {
	if req.GetKey() == "" {
		return nil, NewGrpcErrorFromCode(apierrors.ErrorCodes.ErrKeyEmpty)
	}
	if req.GetValue() == nil {
		return nil, NewGrpcErrorFromCode(apierrors.ErrorCodes.ErrValueEmpty)
	}

	value, err := toAny(req.GetValue())
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to convert proto Value to internal type: %w", err))
	}

	err = ws.keyValueStorage.Save(req.GetKey(), value)
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to save key with given data: %w", err))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) Get(_ context.Context, req *api.GetRequest) (*api.GetResponse, error) {

	key := req.GetKey()
	if key == "" {
		return nil, NewGrpcErrorFromCode(apierrors.ErrorCodes.ErrKeyEmpty)
	}

	value, err := ws.keyValueStorage.Get(key)
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to get key: %w", err))
	}

	protoValue, err := toProtoValue(value)
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to convert data to proto Value: %w", err))
	}

	return api.GetResponse_builder{
		Value: protoValue,
	}.Build(), nil
}

func (ws WalletServer) Delete(_ context.Context, req *api.DeleteRequest) (*emptypb.Empty, error) {
	if req.GetKey() == "" {
		return nil, NewGrpcErrorFromCode(apierrors.ErrorCodes.ErrKeyEmpty)
	}

	err := ws.keyValueStorage.Delete(req.GetKey())
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to delete key: %w", err))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) SaveBatch(_ context.Context, req *api.SaveBatchRequest) (*emptypb.Empty, error) {
	if req.GetItems() == nil {
		return nil, NewGrpcErrorFromCode(apierrors.ErrorCodes.ErrItemsEmpty)
	}

	items, err := toAnyMap(req.GetItems())
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to convert proto Struct to map: %w", err))
	}
	err = ws.keyValueStorage.SaveBatch(items)
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to save batch with given data: %w", err))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) GetBatch(_ context.Context, req *api.GetBatchRequest) (*api.GetBatchResponse, error) {
	keys := req.GetKeys()
	if len(keys) == 0 {
		return nil, NewGrpcErrorFromCode(apierrors.ErrorCodes.ErrKeyEmpty)
	}

	items, err := ws.keyValueStorage.GetBatch(keys)
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to get batch with given keys: %w", err))
	}

	if len(items) == 0 {
		return nil, NewGrpcError(fmt.Errorf("failed to found values for keys: %v", keys))
	}

	protoItems, err := toProtoValueMap(items)
	if err != nil {
		return nil, NewGrpcError(fmt.Errorf("failed to convert data to proto Struct: %w", err))
	}

	return api.GetBatchResponse_builder{
		Items: protoItems,
	}.Build(), nil
}

func toAny(protoValue *api.Value) (any, error) {
	switch protoValue.WhichKind() {
	case api.Value_NullValue_case:
		return nil, nil
	case api.Value_DoubleValue_case:
		return protoValue.GetDoubleValue(), nil
	case api.Value_IntValue_case:
		return protoValue.GetIntValue(), nil
	case api.Value_LongValue_case:
		return protoValue.GetLongValue(), nil
	case api.Value_StringValue_case:
		return protoValue.GetStringValue(), nil
	case api.Value_BoolValue_case:
		return protoValue.GetBoolValue(), nil
	default:
		return nil, fmt.Errorf("invalid value kind: %s", protoValue.WhichKind().String())
	}
}

func toProtoValue(value any) (*api.Value, error) {
	protoValue := &api.Value{}
	switch v := value.(type) {
	case nil:
		protoValue.SetNullValue(api.NullValue_NULL_VALUE)
		return protoValue, nil
	case float64:
		protoValue.SetDoubleValue(v)
		return protoValue, nil
	case int64:
		protoValue.SetLongValue(v)
		return protoValue, nil
	case int32:
		protoValue.SetIntValue(v)
		return protoValue, nil
	case string:
		protoValue.SetStringValue(v)
		return protoValue, nil
	case bool:
		protoValue.SetBoolValue(v)
		return protoValue, nil
	default:
		return nil, fmt.Errorf("unknown type %T", v)
	}
}

func toAnyMap(protoItems *api.Struct) (map[string]any, error) {
	protoValues := protoItems.GetFields()
	if protoValues == nil {
		return nil, fmt.Errorf("proto values are required")
	}
	items := make(map[string]any, len(protoValues))
	for key, value := range protoValues {
		anyValue, err := toAny(value)
		if err != nil {
			return nil, err
		}
		items[key] = anyValue
	}
	return items, nil
}

func toProtoValueMap(items map[string]any) (*api.Struct, error) {
	if items == nil {
		return nil, fmt.Errorf("items are required")
	}
	protoItems := make(map[string]*api.Value, len(items))
	for key, value := range items {
		protoItem, err := toProtoValue(value)
		if err != nil {
			return nil, err
		}
		protoItems[key] = protoItem
	}
	return api.Struct_builder{Fields: protoItems}.Build(), nil
}
