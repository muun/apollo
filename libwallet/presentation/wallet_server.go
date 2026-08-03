package presentation

import (
	"bytes"
	"context"
	"encoding/hex"
	"errors"
	"log/slog"
	"time"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcutil"
	goerr "github.com/go-errors/errors"
	"github.com/google/uuid"
	"google.golang.org/grpc"
	"google.golang.org/protobuf/types/known/emptypb"

	"github.com/muun/libwallet"
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/data/securekv"
	"github.com/muun/libwallet/domain/action/challenge_keys"
	"github.com/muun/libwallet/domain/action/debug"
	"github.com/muun/libwallet/domain/action/diagnostic_mode_reports"
	"github.com/muun/libwallet/domain/action/emergency_kit"
	"github.com/muun/libwallet/domain/action/nfc"
	"github.com/muun/libwallet/domain/action/recovery"
	"github.com/muun/libwallet/domain/action/reset"
	"github.com/muun/libwallet/domain/action/security_cards_marketplace"
	"github.com/muun/libwallet/domain/diagnostic_mode"
	security_cards_marketplace_model "github.com/muun/libwallet/domain/model/security_cards_marketplace"
	apierrors "github.com/muun/libwallet/errors"
	"github.com/muun/libwallet/platform/preconditions"
	"github.com/muun/libwallet/presentation/api"
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
	"github.com/muun/libwallet/storage"
)

type WalletServer struct {
	api.UnsafeWalletServiceServer
	nfcBridge                   app_provided_data.NfcBridge
	keyProvider                 keys.KeyProvider
	network                     *libwallet.Network
	houstonService              service.HoustonService
	keyValueStorage             *storage.KeyValueStorage
	resetData                   reset.ResetDataAction
	startChallengeSetup         *challenge_keys.StartChallengeSetupAction
	finishChallengeSetup        *challenge_keys.FinishChallengeSetupAction
	populateEncryptedMuunKey    *recovery.PopulateEncryptedMuunKeyAction
	scanForFunds                *recovery.ScanForFundsAction
	submitDiagnostic            *diagnostic_mode_reports.SubmitDiagnosticAction
	buildSweepTx                *recovery.BuildSweepTxAction
	signSweepTx                 *recovery.SignSweepTxAction
	pairSecurityCardV2          *nfc.PairSecurityCardActionV2
	signMessageSecurityCardV2   *nfc.SignMessageSecurityCardActionV2
	pairRequestChallenge        *nfc.PairRequestChallengeAction
	pairSignAndSubmitChallenge  *nfc.PairSignAndSubmitChallengeAction
	getSecurityCardsMarketplace *security_cards_marketplace.GetSecurityCardsMarketplaceAction
	generateEmergencyKitPDF     *emergency_kit.GenerateEmergencyKitPDFAction
	zipDataDir                  *debug.ZipDataDirAction
	secureKeyValueStorage       securekv.SecureKeyValueStorage
}

func NewWalletServer(
	nfcBridge app_provided_data.NfcBridge,
	keyProvider keys.KeyProvider,
	network *libwallet.Network,
	houstonService service.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	resetData reset.ResetDataAction,
	startChallengeSetup *challenge_keys.StartChallengeSetupAction,
	finishChallengeSetup *challenge_keys.FinishChallengeSetupAction,
	obtainVerifiedEncryptedMuunKeyIfAbsent *recovery.PopulateEncryptedMuunKeyAction,
	scanForFunds *recovery.ScanForFundsAction,
	submitDiagnostic *diagnostic_mode_reports.SubmitDiagnosticAction,
	buildSweepTx *recovery.BuildSweepTxAction,
	signSweepTx *recovery.SignSweepTxAction,
	pairSecurityCardV2 *nfc.PairSecurityCardActionV2,
	signMessageSecurityCardV2 *nfc.SignMessageSecurityCardActionV2,
	pairRequestChallenge *nfc.PairRequestChallengeAction,
	pairSignAndSubmitChallenge *nfc.PairSignAndSubmitChallengeAction,
	getSecurityCardsMarketplace *security_cards_marketplace.GetSecurityCardsMarketplaceAction,
	generateEmergencyKitPDF *emergency_kit.GenerateEmergencyKitPDFAction,
	zipDataDir *debug.ZipDataDirAction,
	secureKeyValueStorage securekv.SecureKeyValueStorage,
) *WalletServer {

	return &WalletServer{
		nfcBridge:                   nfcBridge,
		keyProvider:                 keyProvider,
		network:                     network,
		houstonService:              houstonService,
		keyValueStorage:             keyValueStorage,
		resetData:                   resetData,
		startChallengeSetup:         startChallengeSetup,
		finishChallengeSetup:        finishChallengeSetup,
		populateEncryptedMuunKey:    obtainVerifiedEncryptedMuunKeyIfAbsent,
		scanForFunds:                scanForFunds,
		submitDiagnostic:            submitDiagnostic,
		buildSweepTx:                buildSweepTx,
		signSweepTx:                 signSweepTx,
		pairSecurityCardV2:          pairSecurityCardV2,
		signMessageSecurityCardV2:   signMessageSecurityCardV2,
		pairRequestChallenge:        pairRequestChallenge,
		pairSignAndSubmitChallenge:  pairSignAndSubmitChallenge,
		getSecurityCardsMarketplace: getSecurityCardsMarketplace,
		generateEmergencyKitPDF:     generateEmergencyKitPDF,
		zipDataDir:                  zipDataDir,
		secureKeyValueStorage:       secureKeyValueStorage,
	}
}

// Check we actually implement the interface
var _ api.WalletServiceServer = (*WalletServer)(nil)

func (ws WalletServer) SetupSecurityCardV2(
	ctx context.Context, //nolint:revive // TODO: use or remove ctx
	message *emptypb.Empty, //nolint:revive // TODO: use or remove message
) (*api.SetupSecurityCardResponse, error) {
	response, err := ws.pairSecurityCardV2.Run()
	if err != nil {
		var invalidMacErr *nfc.InvalidMacError
		var challengeExpiredErr *nfc.ChallengeExpiredError

		switch {
		case errors.As(err, &invalidMacErr):
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrSignMacValidation, err)
		case errors.As(err, &challengeExpiredErr):
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrChallengeExpired, err)
		default:
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrPairInternalError, err)
		}
	}

	slog.Debug("card paired successfully")
	slog.Debug("MetadaCard", "metadata", response.Metadata)

	return api.SetupSecurityCardResponse_builder{
		IsKnownProvider:   response.IsKnownProvider,
		IsCardAlreadyUsed: response.IsCardAlreadyUsed,
	}.Build(), nil
}

func (ws WalletServer) SignMessageSecurityCardV2(
	ctx context.Context, //nolint:revive // TODO: use or remove ctx
	message *emptypb.Empty, //nolint:revive // TODO: use or remove message
) (*emptypb.Empty, error) {
	err := ws.signMessageSecurityCardV2.Run()
	if err != nil {
		var invalidMacErr *nfc.InvalidMacError
		var challengeExpiredErr *nfc.ChallengeExpiredError
		var pairInternalErr *nfc.PairInternalError
		var noSlotsAvailableErr *nfc.NoSlotsAvailableError
		var muunAppletNotFoundErr *nfc.MuunAppletNotFoundError

		switch {
		case errors.As(err, &invalidMacErr):
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrSignMacValidation, err)
		case errors.As(err, &challengeExpiredErr):
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrChallengeExpired, err)
		case errors.As(err, &pairInternalErr):
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrPairInternalError, err)
		case errors.As(err, &noSlotsAvailableErr):
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrNoSlotsAvailable, err)
		case errors.As(err, &muunAppletNotFoundErr):
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrAppletNotFound, err)
		default:
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrSignInternalError, err)
		}
	}
	return &emptypb.Empty{}, nil
}

func (ws WalletServer) PairRequestChallenge(
	_ context.Context,
	_ *emptypb.Empty,
) (*emptypb.Empty, error) {
	err := ws.pairRequestChallenge.Run()
	if err != nil {
		return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrPairInternalError, err)
	}
	return &emptypb.Empty{}, nil
}

func (ws WalletServer) PairSignAndSubmitChallenge(
	_ *emptypb.Empty,
	g grpc.ServerStreamingServer[api.PairSignAndSubmitChallengeProgress],
) error {
	cardPaired, err := ws.pairSignAndSubmitChallenge.Run(func(progress nfc.PairProgress) error {
		switch progress {
		case nfc.PairProgressRefreshingChallenge:
			return g.Send(api.PairSignAndSubmitChallengeProgress_builder{
				RefreshingChallenge: &api.RefreshingChallenge{},
			}.Build())
		case nfc.PairProgressChallengeSigned:
			// The user can stop holding the card (or whatever device) near to the phone
			return g.Send(api.PairSignAndSubmitChallengeProgress_builder{
				ChallengeSigned: &api.ChallengeSigned{},
			}.Build())
		default:
			slog.Error("PairProgress value was not handled")
		}
		return nil
	})

	if err != nil {
		var noSlotsAvailableErr *nfc.NoSlotsAvailableError
		var muunAppletNotFoundErr *nfc.MuunAppletNotFoundError
		var invalidMacErr *nfc.InvalidMacError
		var challengeExpiredErr *nfc.ChallengeExpiredError
		switch {
		case errors.As(err, &noSlotsAvailableErr):
			return NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrNoSlotsAvailable, err)
		case errors.As(err, &muunAppletNotFoundErr):
			return NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrAppletNotFound, err)
		case errors.As(err, &invalidMacErr):
			return NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrSignMacValidation, err)
		case errors.As(err, &challengeExpiredErr):
			return NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrChallengeExpired, err)
		default:
			return NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrPairInternalError, err)
		}
	}

	return g.Send(api.PairSignAndSubmitChallengeProgress_builder{
		Completed: api.PairSignAndSubmitChallengeResponse_builder{
			IsKnownProvider:   cardPaired.IsKnownProvider,
			IsCardAlreadyUsed: cardPaired.IsCardAlreadyUsed,
		}.Build(),
	}.Build())
}

func (ws WalletServer) StartDiagnosticSession(
	ctx context.Context, //nolint:revive // TODO: use or remove ctx
	empty *emptypb.Empty, //nolint:revive // TODO: use or remove empty
) (*api.DiagnosticSessionDescriptor, error) {
	sessionID := uuid.NewString()

	logBuffer := bytes.NewBuffer(nil)
	textHandler := slog.NewTextHandler(logBuffer, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})
	debugLog := slog.New(textHandler)

	err := diagnostic_mode.AddDiagnosticSession(&diagnostic_mode.DiagnosticSessionData{
		Id:        sessionID,
		LogBuffer: logBuffer,
		Logger:    debugLog,
	})
	if err != nil {
		return nil, err
	}
	return api.DiagnosticSessionDescriptor_builder{
		SessionId: sessionID,
	}.Build(), nil
}

func (ws WalletServer) PerformDiagnosticScanForUtxos(
	descriptor *api.DiagnosticSessionDescriptor,
	g grpc.ServerStreamingServer[api.ScanProgressUpdate],
) error {
	sessionID := descriptor.GetSessionId()

	if sessionData, ok := diagnostic_mode.GetDiagnosticSession(sessionID); ok {
		reports, err := ws.scanForFunds.Run(sessionData.Logger)
		if err != nil {
			return NewGrpcError(goerr.Errorf("error scanning for funds: %w", err))
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
		return NewGrpcError(goerr.Errorf("invalid sessionID %s", descriptor.GetSessionId()))
	}
}

func (ws WalletServer) SubmitDiagnosticLog(
	ctx context.Context, //nolint:revive // TODO: use or remove ctx
	descriptor *api.DiagnosticSessionDescriptor,
) (*api.DiagnosticSubmitStatus, error) {
	sessionID := descriptor.GetSessionId()
	if session, ok := diagnostic_mode.GetDiagnosticSession(sessionID); ok {
		err := ws.submitDiagnostic.Run(sessionID, session.LogBuffer.String())
		if err != nil {
			return nil, err
		}

		diagnostic_mode.DeleteDiagnosticSession(sessionID)
		return api.DiagnosticSubmitStatus_builder{
			StatusCode:    200,
			StatusMessage: "OK",
		}.Build(), nil
	} else {
		return nil, goerr.Errorf("invalid sessionID %s", descriptor.GetSessionId())
	}
}

func (ws WalletServer) PrepareSweepTx(
	ctx context.Context, //nolint:revive // TODO: use or remove ctx
	parameters *api.PrepareSweepTxRequest,
) (*api.PrepareSweepTxResponse, error) {
	destinationAddressString := parameters.GetDestinationAddress()
	address, err := btcutil.DecodeAddress(destinationAddressString, ws.network.ToParams())
	if err != nil {
		return nil, err
	}

	descriptor := parameters.GetSessionDescriptor()

	sessionID := descriptor.GetSessionId()
	if session, ok := diagnostic_mode.GetDiagnosticSession(sessionID); ok {
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
		return nil, goerr.Errorf("invalid sessionID %s", sessionID)
	}
}

func (ws WalletServer) SignAndBroadcastSweepTx(
	ctx context.Context, //nolint:revive // TODO: use or remove ctx
	confirmation *api.SignAndBroadcastSweepTxRequest,
) (*api.SignAndBroadcastSweepTxResponse, error) {
	sessionID := confirmation.GetSessionDescriptor().GetSessionId()
	if session, ok := diagnostic_mode.GetDiagnosticSession(sessionID); ok {
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

		return nil, goerr.Errorf("signed tx %v but did not broadcast", txString)
	} else {
		return nil, goerr.Errorf("invalid sessionID %s", sessionID)
	}
}

// StartChallengeSetup is part of a migration test.
// This is a minimal switch to call houston from libwallet instead of from native code.
// Do NOT treat this as a reference.
// Future implementations should move native logic as much as possible to libwallet instead of
// duplicating this pattern.
func (ws WalletServer) StartChallengeSetup(
	ctx context.Context, req *api.ChallengeSetupRequest, //nolint:revive // TODO: use or remove ctx
) (*api.SetupChallengeResponse, error) {

	challengeSetupJson := model.ChallengeSetupJson{ //nolint:staticcheck // TODO: var challengeSetupJson should be challengeSetupJSON
		Type:                req.GetType(),
		PublicKey:           req.GetPublicKey(),
		Salt:                req.GetSalt(),
		EncryptedPrivateKey: req.GetEncryptedPrivateKey(),
		Version:             int(req.GetVersion()),
	}

	setupChallengeResponseJson, err := ws.startChallengeSetup.Run( //nolint:staticcheck // TODO: var setupChallengeResponseJson should be setupChallengeResponseJSON
		challengeSetupJson,
	)
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to start challenge setup: %w", err))
	}

	return api.SetupChallengeResponse_builder{
		MuunKey:            setupChallengeResponseJson.MuunKey,
		MuunKeyFingerprint: setupChallengeResponseJson.MuunKeyFingerprint,
	}.Build(), nil
}

func (ws WalletServer) FinishRecoveryCodeSetup(
	ctx context.Context, //nolint:revive // TODO: use or remove ctx
	req *api.FinishRecoveryCodeSetupRequest,
) (*emptypb.Empty, error) {

	recoveryCodePublicKey, err := hexToPublicKey(req.GetRecoveryCodePublicKeyHex())
	if err != nil {
		return nil, goerr.Errorf("error parsing recovery code public key: %w", err)
	}

	err = ws.finishChallengeSetup.Run(recoveryCodePublicKey)
	if err != nil {
		return nil, err
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) PopulateEncryptedMuunKey(
	ctx context.Context, //nolint:revive // TODO: use or remove ctx
	req *api.PopulateEncryptedMuunKeyRequest,
) (*emptypb.Empty, error) {
	recoveryCodePublicKey, err := hexToPublicKey(req.GetRecoveryCodePublicKeyHex())
	if err != nil {
		return nil, goerr.Errorf("error parsing recovery code public key: %w", err)
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
		return nil, NewGrpcError(
			goerr.Errorf("failed to convert proto Value to internal type: %w", err),
		)
	}

	err = ws.keyValueStorage.Save(req.GetKey(), value)
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to save key with given data: %w", err))
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
		return nil, NewGrpcError(goerr.Errorf("failed to get key: %w", err))
	}

	protoValue, err := toProtoValue(value)
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to convert data to proto Value: %w", err))
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
		return nil, NewGrpcError(goerr.Errorf("failed to delete key: %w", err))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) SaveBatch(
	_ context.Context,
	req *api.SaveBatchRequest,
) (*emptypb.Empty, error) {
	if req.GetItems() == nil {
		return nil, NewGrpcErrorFromCode(apierrors.ErrorCodes.ErrItemsEmpty)
	}

	items, err := toAnyMap(req.GetItems())
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to convert proto Struct to map: %w", err))
	}
	err = ws.keyValueStorage.SaveBatch(items)
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to save batch with given data: %w", err))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) GetBatch(
	_ context.Context,
	req *api.GetBatchRequest,
) (*api.GetBatchResponse, error) {
	keys := req.GetKeys()
	if len(keys) == 0 {
		return nil, NewGrpcErrorFromCode(apierrors.ErrorCodes.ErrKeyEmpty)
	}

	items, err := ws.keyValueStorage.GetBatch(keys)
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to get batch with given keys: %w", err))
	}

	if len(items) == 0 {
		return nil, NewGrpcError(goerr.Errorf("failed to found values for keys: %v", keys))
	}

	protoItems, err := toProtoValueMap(items)
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to convert data to proto Struct: %w", err))
	}

	return api.GetBatchResponse_builder{
		Items: protoItems,
	}.Build(), nil
}

func (ws WalletServer) GetByPrefix(
	_ context.Context,
	req *api.GetByPrefixRequest,
) (*api.GetBatchResponse, error) {
	prefix := req.GetPrefix()
	if prefix == "" {
		return nil, NewGrpcErrorFromCode(apierrors.ErrorCodes.ErrKeyEmpty)
	}

	items, err := ws.keyValueStorage.GetByPrefix(prefix)
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to get by prefix with given prefix: %w", err))
	}

	protoItems, err := toProtoValueMap(items)
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to convert data to proto Struct: %w", err))
	}

	return api.GetBatchResponse_builder{
		Items: protoItems,
	}.Build(), nil
}

func (ws WalletServer) GetSecurityCardsMarketplace(
	ctx context.Context, req *emptypb.Empty, //nolint:revive // TODO: use or remove ctx
) (*api.GetSecurityCardsMarketplaceResponse, error) {

	marketplace, err := ws.getSecurityCardsMarketplace.Run()
	if err != nil {
		return nil, NewGrpcError(
			goerr.Errorf("failed to get security cards marketplace data: %w", err),
		)
	}

	providers := make([]*api.SecurityCardsProvider, 0, len(marketplace.Providers))
	for _, provider := range marketplace.Providers {

		securityCards := make([]*api.SecurityCard, 0, len(provider.SecurityCards))
		for _, securityCard := range provider.SecurityCards {

			securityCards = append(securityCards, api.SecurityCard_builder{
				Id:       securityCard.Id,
				AssetUrl: securityCard.AssetUrl,
				Tag:      securityCard.Tag,
				SpecId:   securityCard.SpecId,
				CardCost: toProtoPriceInfo(securityCard.CardCost),
			}.Build())
		}

		shippingPrices := make([]*api.ShippingPriceInfo, 0, len(provider.EstimatedShippingPrices))
		for _, shippingPrice := range provider.EstimatedShippingPrices {

			countries := make([]*api.CountryInfo, 0, len(shippingPrice.Countries))
			for _, country := range shippingPrice.Countries {
				countries = append(countries, api.CountryInfo_builder{
					Code: country.Code,
					Name: country.Name,
					Flag: country.Flag,
				}.Build())
			}

			shippingPrices = append(shippingPrices, api.ShippingPriceInfo_builder{
				Price:     toProtoPriceInfo(shippingPrice.Price),
				Countries: countries,
			}.Build())
		}

		providers = append(providers, api.SecurityCardsProvider_builder{
			Id:                      provider.Id,
			Name:                    provider.Name,
			Description:             provider.Description,
			SiteUrl:                 provider.SiteUrl,
			LightTheme:              toProtoProviderTheme(provider.LightTheme),
			DarkTheme:               toProtoProviderTheme(provider.DarkTheme),
			SecurityCards:           securityCards,
			EstimatedShippingPrices: shippingPrices,
		}.Build())
	}

	specs := make([]*api.SecurityCardSpec, 0, len(marketplace.Specs))
	for _, spec := range marketplace.Specs {

		items := make(map[string]*api.SpecsItemList, len(spec.Items))
		for locale, specItems := range spec.Items {
			protoItems := make([]*api.SpecsItem, 0, len(specItems))
			for _, item := range specItems {
				protoItems = append(protoItems, api.SpecsItem_builder{
					IconUrl:        item.IconUrl,
					Label:          item.Label,
					Value:          item.Value,
					AdditionalData: item.AdditionalData,
				}.Build())
			}
			items[locale] = api.SpecsItemList_builder{Items: protoItems}.Build()
		}

		specs = append(specs, api.SecurityCardSpec_builder{
			SpecId: spec.SpecId,
			Items:  items,
		}.Build())
	}

	return api.GetSecurityCardsMarketplaceResponse_builder{
		Providers: providers,
		Specs:     specs,
	}.Build(), nil
}

func toProtoPriceInfo(in security_cards_marketplace_model.Price) *api.PriceInfo {
	return api.PriceInfo_builder{
		CurrencyCode: in.CurrencyCode,
		Amount:       in.Amount,
	}.Build()
}

func toProtoProviderTheme(
	in security_cards_marketplace_model.ProviderTheme,
) *api.SecurityCardsProviderTheme {
	return api.SecurityCardsProviderTheme_builder{
		PrimaryColor: in.PrimaryColor,
		SurfaceColor: in.SurfaceColor,
	}.Build()
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
		return nil, goerr.Errorf("invalid value kind: %s", protoValue.WhichKind().String())
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
		return nil, goerr.Errorf("unknown type %T", v)
	}
}

func toAnyMap(protoItems *api.Struct) (map[string]any, error) {
	protoValues := protoItems.GetFields()
	if protoValues == nil {
		return nil, goerr.Errorf("proto values are required")
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
		return nil, goerr.Errorf("items are required")
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

func (ws WalletServer) ResetData(
	_ context.Context,
	_ *emptypb.Empty,
) (*emptypb.Empty, error) {
	err := ws.resetData.Run()
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to reset database: %w", err))
	}
	return &emptypb.Empty{}, nil
}

// GenerateEmergencyKitPDF outputPath must be the full path where the PDF should be saved (including
// filename). Example: "/path/to/documents/expected_kit_name.pdf" The directory will be created if
// it doesn't exist.
func (ws WalletServer) GenerateEmergencyKitPDF(
	ctx context.Context, //nolint:revive // TODO: use or remove ctx
	request *api.GenerateEmergencyKitPDFRequest,
) (*api.GenerateEmergencyKitPDFResponse, error) {
	startGo := time.Now()
	ekInput := request.GetEkInput()
	ekParams := &libwallet.EKInput{
		FirstEncryptedKey:  ekInput.GetFirstEncryptedKey(),
		FirstFingerprint:   ekInput.GetFirstFingerprint(),
		SecondEncryptedKey: ekInput.GetSecondEncryptedKey(),
		SecondFingerprint:  ekInput.GetSecondFingerprint(),
		RcChecksum:         ekInput.GetRcChecksum(),
	}

	result, err := ws.generateEmergencyKitPDF.Run(
		ekParams,
		request.GetOutputPath(),
		request.GetLanguage(),
	)
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to generate emergency kit PDF: %w", err))
	}

	profiling := result.Profiling
	return api.GenerateEmergencyKitPDFResponse_builder{
		VerificationCode: result.VerificationCode,
		Version:          int32(result.Version),
		Profiling: api.RenderProfiling_builder{
			LoadTranslationsMs:      profiling.LoadTranslationsMs,
			RegisterFontsMs:         profiling.RegisterFontsMs,
			RegisterImagesMs:        profiling.RegisterImagesMs,
			ComponentsRenderingMs:   profiling.ComponentsRenderingMs,
			CreateAndSaveOnDiskMs:   profiling.CreateAndSaveOnDiskMs,
			TotalHeapAllocatedBytes: profiling.TotalHeapAllocatedBytes,
			TotalObjectsAllocated:   profiling.TotalObjectsAllocated,
			EmbedMetadataMs:         profiling.EmbedMetadataMs,
			TotalInsideGoMs:         time.Since(startGo).Milliseconds(),
		}.Build(),
	}.Build(), nil
}

func (ws WalletServer) ZipDataDir(
	_ context.Context,
	req *api.ZipDataDirRequest,
) (*emptypb.Empty, error) {
	err := ws.zipDataDir.Run(req.GetOutputPath())
	if err != nil {
		return nil, NewGrpcError(goerr.Errorf("failed to zip data directory: %w", err))
	}
	return &emptypb.Empty{}, nil
}

func (ws WalletServer) SecureKeyValueStoragePut(
	ctx context.Context,
	request *api.SecureKeyValueStoragePutRequest,
) (*emptypb.Empty, error) {
	preconditions.CheckStatef(
		ws.secureKeyValueStorage != nil,
		"secure key-value storage bridge not configured",
	)
	err := ws.secureKeyValueStorage.Put(ctx, request.GetKey(), request.GetValue())
	if err != nil {
		return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrSecureKvStorageFailed, err)
	}
	return &emptypb.Empty{}, nil
}

func (ws WalletServer) SecureKeyValueStorageDelete(
	ctx context.Context,
	request *api.SecureKeyValueStorageDeleteRequest,
) (*emptypb.Empty, error) {
	preconditions.CheckStatef(
		ws.secureKeyValueStorage != nil,
		"secure key-value storage bridge not configured",
	)
	err := ws.secureKeyValueStorage.Delete(ctx, request.GetKey())
	if err != nil {
		return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrSecureKvStorageFailed, err)
	}
	return &emptypb.Empty{}, nil
}

func (ws WalletServer) SecureKeyValueStorageGet(
	ctx context.Context,
	request *api.SecureKeyValueStorageGetRequest,
) (*api.SecureKeyValueStorageGetResponse, error) {
	preconditions.CheckStatef(
		ws.secureKeyValueStorage != nil,
		"secure key-value storage bridge not configured",
	)
	secret, err := ws.secureKeyValueStorage.Get(ctx, request.GetKey())
	if err != nil {
		return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrSecureKvStorageFailed, err)
	}

	var response *api.SecureKeyValueStorageGetResponse
	err = secret.WithSecret(func(b []byte) error {
		response = api.SecureKeyValueStorageGetResponse_builder{
			Value: append([]byte(nil), b...),
		}.Build()
		return nil
	})
	if err != nil {
		var notFound *securekv.NotFoundError
		var decryptionFailed *securekv.DecryptionFailedError
		switch {
		case errors.As(err, &notFound):
			return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrSecureKvNotFound, err)
		case errors.As(err, &decryptionFailed):
			return nil, NewGrpcErrorFromCodeAndErr(
				apierrors.ErrorCodes.ErrSecureKvDecryptionFailed,
				err,
			)
		default:
			return nil, NewGrpcErrorFromCodeAndErr(
				apierrors.ErrorCodes.ErrSecureKvStorageFailed,
				err,
			)
		}
	}
	return response, nil
}

func (ws WalletServer) SecureKeyValueStorageWipe(
	ctx context.Context,
	_ *emptypb.Empty,
) (*emptypb.Empty, error) {
	preconditions.CheckStatef(
		ws.secureKeyValueStorage != nil,
		"secure key-value storage bridge not configured",
	)
	err := ws.secureKeyValueStorage.Wipe(ctx)
	if err != nil {
		return nil, NewGrpcErrorFromCodeAndErr(apierrors.ErrorCodes.ErrSecureKvStorageFailed, err)
	}
	return &emptypb.Empty{}, nil
}
