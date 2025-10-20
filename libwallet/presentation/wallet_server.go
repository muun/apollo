package presentation

import (
	"bytes"
	"context"
	"encoding/hex"
	"fmt"
	"github.com/btcsuite/btcd/btcutil"
	"log/slog"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil/base58"
	"github.com/google/uuid"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/domain/action/challenge_keys"
	"github.com/muun/libwallet/domain/action/diagnostic_mode_reports"
	"github.com/muun/libwallet/domain/action/nfc"
	"github.com/muun/libwallet/domain/action/recovery"
	"github.com/muun/libwallet/domain/diagnostic_mode"
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
	nfcBridge                app_provided_data.NfcBridge
	keyProvider              keys.KeyProvider
	network                  *libwallet.Network
	houstonService           service.HoustonService
	keyValueStorage          *storage.KeyValueStorage
	startChallengeSetup      *challenge_keys.StartChallengeSetupAction
	finishChallengeSetup     *challenge_keys.FinishChallengeSetupAction
	populateEncryptedMuunKey *recovery.PopulateEncryptedMuunKeyAction
	scanForFunds             *recovery.ScanForFundsAction
	submitDiagnostic         *diagnostic_mode_reports.SubmitDiagnosticAction
	buildSweepTx             *recovery.BuildSweepTxAction
	signSweepTx              *recovery.SignSweepTxAction
	pairSecurityCard         *nfc.PairSecurityCardAction
	resetSecurityCard        *nfc.ResetSecurityCardAction
	signMessageSecurityCard  *nfc.SignMessageSecurityCardAction
	pairSecurityCardV2       *nfc.PairSecurityCardActionV2
}

func NewWalletServer(
	nfcBridge app_provided_data.NfcBridge,
	keyProvider keys.KeyProvider,
	network *libwallet.Network,
	houstonService service.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	startChallengeSetup *challenge_keys.StartChallengeSetupAction,
	finishChallengeSetup *challenge_keys.FinishChallengeSetupAction,
	obtainVerifiedEncryptedMuunKeyIfAbsent *recovery.PopulateEncryptedMuunKeyAction,
	scanForFunds *recovery.ScanForFundsAction,
	submitDiagnostic *diagnostic_mode_reports.SubmitDiagnosticAction,
	buildSweepTx *recovery.BuildSweepTxAction,
	signSweepTxAction *recovery.SignSweepTxAction,
	pairSecurityCard *nfc.PairSecurityCardAction,
	resetSecurityCard *nfc.ResetSecurityCardAction,
	signMessageSecurityCard *nfc.SignMessageSecurityCardAction,
	pairSecurityCardV2 *nfc.PairSecurityCardActionV2,
) *WalletServer {

	return &WalletServer{
		nfcBridge:                nfcBridge,
		keyProvider:              keyProvider,
		network:                  network,
		houstonService:           houstonService,
		keyValueStorage:          keyValueStorage,
		startChallengeSetup:      startChallengeSetup,
		finishChallengeSetup:     finishChallengeSetup,
		populateEncryptedMuunKey: obtainVerifiedEncryptedMuunKeyIfAbsent,
		scanForFunds:             scanForFunds,
		submitDiagnostic:         submitDiagnostic,
		buildSweepTx:             buildSweepTx,
		signSweepTx:              signSweepTxAction,
		pairSecurityCard:         pairSecurityCard,
		resetSecurityCard:        resetSecurityCard,
		signMessageSecurityCard:  signMessageSecurityCard,
		pairSecurityCardV2:       pairSecurityCardV2,
	}
}

// Check we actually implement the interface
var _ api.WalletServiceServer = (*WalletServer)(nil)

func (ws WalletServer) SetupSecurityCard(
	ctx context.Context,
	message *emptypb.Empty,
) (*api.XpubResponse, error) {
	extendedPublicKey, err := ws.pairSecurityCard.Run()
	if err != nil {
		return nil, fmt.Errorf("error pairing security card: %w", err)
	}

	base58Xpub := base58.Encode(extendedPublicKey.RawBytes)

	return api.XpubResponse_builder{
		Base58Xpub: base58Xpub,
	}.Build(), nil
}

func (ws WalletServer) SetupSecurityCardV2(
	ctx context.Context,
	message *emptypb.Empty,
) (*api.SetupSecurityCardResponse, error) {
	response, err := ws.pairSecurityCardV2.Run()
	if err != nil {
		return nil, fmt.Errorf("error pairing security card: %w", err)
	}

	slog.Debug("card paired succesfully")
	slog.Debug("MetadaCard", "metadata", response.Metadata)

	return api.SetupSecurityCardResponse_builder{
		IsKnownProvider:   response.IsKnownProvider,
		IsCardAlreadyUsed: response.IsCardAlreadyUsed,
	}.Build(), nil
}

func (ws WalletServer) ResetSecurityCard(
	ctx context.Context,
	message *emptypb.Empty,
) (*emptypb.Empty, error) {
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

func (ws WalletServer) StartDiagnosticSession(
	ctx context.Context,
	empty *emptypb.Empty,
) (*api.DiagnosticSessionDescriptor, error) {
	sessionId := uuid.NewString()

	logBuffer := bytes.NewBuffer(nil)
	textHandler := slog.NewTextHandler(logBuffer, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})
	debugLog := slog.New(textHandler)

	err := diagnostic_mode.AddDiagnosticSession(&diagnostic_mode.DiagnosticSessionData{
		Id:        sessionId,
		LogBuffer: logBuffer,
		Logger:    debugLog,
	})
	if err != nil {
		return nil, err
	}
	return api.DiagnosticSessionDescriptor_builder{
		SessionId: sessionId,
	}.Build(), nil
}

func (ws WalletServer) PerformDiagnosticScanForUtxos(
	descriptor *api.DiagnosticSessionDescriptor,
	g grpc.ServerStreamingServer[api.ScanProgressUpdate],
) error {
	sessionId := descriptor.GetSessionId()

	if sessionData, ok := diagnostic_mode.GetDiagnosticSession(sessionId); ok {
		reports, err := ws.scanForFunds.Run(sessionData.Logger)
		if err != nil {
			return err
		}

		for report := range reports {
			sessionData.LastScanReport = report
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

func (ws WalletServer) SubmitDiagnosticLog(
	ctx context.Context,
	descriptor *api.DiagnosticSessionDescriptor,
) (*api.DiagnosticSubmitStatus, error) {
	sessionId := descriptor.GetSessionId()
	if session, ok := diagnostic_mode.GetDiagnosticSession(sessionId); ok {
		err := ws.submitDiagnostic.Run(sessionId, session.LogBuffer.String())
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

func (ws WalletServer) PrepareSweepTx(ctx context.Context, parameters *api.PrepareSweepTxRequest) (*api.PrepareSweepTxResponse, error) {
	destinationAddressString := parameters.GetDestinationAddress()
	address, err := btcutil.DecodeAddress(destinationAddressString, ws.network.ToParams())
	if err != nil {
		return nil, err
	}

	descriptor := parameters.GetSessionDescriptor()

	sessionId := descriptor.GetSessionId()
	if session, ok := diagnostic_mode.GetDiagnosticSession(sessionId); ok {
		session.SweepTx, err = ws.buildSweepTx.Run(
			session.LastScanReport.UtxosFound,
			address,
			parameters.GetFeeRateInSatsPerVByte(),
		)
		if err != nil {
			return nil, err
		}

		return api.PrepareSweepTxResponse_builder{
			SessionDescriptor:  descriptor,
			DestinationAddress: destinationAddressString,
			TxSizeInBytes:      int64(session.SweepTx.SerializeSize()),
		}.Build(), nil
	} else {
		return nil, fmt.Errorf("invalid sessionId %s", sessionId)
	}
}

func (ws WalletServer) SignAndBroadcastSweepTx(ctx context.Context, confirmation *api.SignAndBroadcastSweepTxRequest) (*api.SignAndBroadcastSweepTxResponse, error) {
	sessionId := confirmation.GetSessionDescriptor().GetSessionId()
	if session, ok := diagnostic_mode.GetDiagnosticSession(sessionId); ok {
		signedTx, err := ws.signSweepTx.Run(
			session.LastScanReport.UtxosFound,
			session.SweepTx,
			confirmation.GetRecoveryCode(),
		)
		if err != nil {
			return nil, err
		}
		var buf bytes.Buffer
		err = signedTx.Serialize(&buf)
		if err != nil {
			return nil, err
		}
		txString := hex.EncodeToString(buf.Bytes())

		return nil, fmt.Errorf("signed tx %v but did not broadcast", txString)
	} else {
		return nil, fmt.Errorf("invalid sessionId %s", sessionId)
	}
}

// StartChallengeSetup is part of a migration test.
// This is a minimal switch to call houston from libwallet instead of from native code.
// Do NOT treat this as a reference.
// Future implementations should move native logic as much as possible to libwallet instead of
// duplicating this pattern.
func (ws WalletServer) StartChallengeSetup(
	ctx context.Context, req *api.ChallengeSetupRequest,
) (*api.SetupChallengeResponse, error) {

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

func (ws WalletServer) FinishRecoveryCodeSetup(
	ctx context.Context,
	req *api.FinishRecoveryCodeSetupRequest,
) (*emptypb.Empty, error) {

	recoveryCodePublicKey, err := hexToPublicKey(req.GetRecoveryCodePublicKeyHex())
	if err != nil {
		return nil, fmt.Errorf("error parsing recovery code public key: %w", err)
	}

	err = ws.finishChallengeSetup.Run(recoveryCodePublicKey)
	if err != nil {
		return nil, err
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) PopulateEncryptedMuunKey(
	ctx context.Context,
	req *api.PopulateEncryptedMuunKeyRequest,
) (*emptypb.Empty, error) {
	recoveryCodePublicKey, err := hexToPublicKey(req.GetRecoveryCodePublicKeyHex())
	if err != nil {
		return nil, fmt.Errorf("error parsing recovery code public key: %w", err)
	}

	err = ws.populateEncryptedMuunKey.Run(recoveryCodePublicKey)
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
