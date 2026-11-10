package server

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
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/emptypb"

	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/houston/model"
	"github.com/muun/libwallet/base/data/storage"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/platform/observability/logging"
	"github.com/muun/libwallet/platform/observability/tracing"
	"github.com/muun/libwallet/server/api"
	background_actions "github.com/muun/libwallet/subsystems/background_actions/presentation"
	ba_model "github.com/muun/libwallet/subsystems/background_actions/presentation/model"
	diagnostics "github.com/muun/libwallet/subsystems/diagnostics/presentation"
	diagnostics_model "github.com/muun/libwallet/subsystems/diagnostics/presentation/model"
	emergency_kit "github.com/muun/libwallet/subsystems/emergency_kit/presentation"

	"github.com/muun/libwallet/app_provided_data/adapted"
	marketplace "github.com/muun/libwallet/subsystems/marketplace/presentation"
	marketplace_model "github.com/muun/libwallet/subsystems/marketplace/presentation/model"
	rates "github.com/muun/libwallet/subsystems/rates/presentation"
	rates_model "github.com/muun/libwallet/subsystems/rates/presentation/model"
	recovery "github.com/muun/libwallet/subsystems/recovery/presentation"
	recovery_tool "github.com/muun/libwallet/subsystems/recovery_tool/presentation"
	security_card "github.com/muun/libwallet/subsystems/security_card/presentation"
	sc_model "github.com/muun/libwallet/subsystems/security_card/presentation/model"
)

type WalletServer struct {
	api.UnsafeWalletServiceServer
	nfcBridge         app_provided_data.NfcBridge
	keyProvider       adapted.KeyProvider
	network           *libwallet.Network
	houstonService    houston.HoustonService
	keyValueStorage   *storage.KeyValueStorage
	resetData         func() error
	recovery          recovery.Recovery
	recoveryTool      recovery_tool.RecoveryTool
	diagnostics       diagnostics.Diagnostics
	securityCard      security_card.SecurityCard
	marketplace       marketplace.Marketplace
	emergencyKit      emergency_kit.EmergencyKit
	backgroundActions background_actions.BackgroundActionsRunner
	rates             rates.Rates
}

func NewWalletServer(
	nfcBridge app_provided_data.NfcBridge,
	keyProvider adapted.KeyProvider,
	network *libwallet.Network,
	houstonService houston.HoustonService,
	keyValueStorage *storage.KeyValueStorage,
	resetData func() error,
	recovery recovery.Recovery,
	recoveryTool recovery_tool.RecoveryTool,
	diagnostics diagnostics.Diagnostics,
	securityCard security_card.SecurityCard,
	marketplace marketplace.Marketplace,
	emergencyKit emergency_kit.EmergencyKit,
	backgroundActions background_actions.BackgroundActionsRunner,
	rates rates.Rates,
) *WalletServer {

	return &WalletServer{
		nfcBridge:         nfcBridge,
		keyProvider:       keyProvider,
		network:           network,
		houstonService:    houstonService,
		keyValueStorage:   keyValueStorage,
		resetData:         resetData,
		recovery:          recovery,
		recoveryTool:      recoveryTool,
		diagnostics:       diagnostics,
		securityCard:      securityCard,
		marketplace:       marketplace,
		emergencyKit:      emergencyKit,
		backgroundActions: backgroundActions,
		rates:             rates,
	}
}

// Check we actually implement the interface
var _ api.WalletServiceServer = (*WalletServer)(nil)

// SignMessageSecurityCard signs with the V3 security card the user taps.
func (ws WalletServer) SignMessageSecurityCard(
	ctx context.Context,
	_ *emptypb.Empty,
) (*api.SignMessageSecurityCardResponse, error) {
	cardSigned, err := ws.securityCard.SignMessageSecurityCard(ctx)
	if err != nil {
		var invalidMacErr *sc_model.InvalidMacError
		var challengeExpiredErr *sc_model.ChallengeExpiredError
		var pairInternalErr *sc_model.PairInternalError
		var muunAppletNotFoundErr *sc_model.MuunAppletNotFoundError
		var unsupportedVersionErr *sc_model.UnsupportedCardVersionError
		var cardMustPairAgainErr *sc_model.CardMustPairAgainError
		var cardOutOfPairingsErr *sc_model.CardOutOfPairingsError

		switch {
		case errors.As(err, &invalidMacErr):
			return nil, newGrpcErrorFromCodeAndErr(errorCodes.ErrSignMacValidation, err)
		case errors.As(err, &challengeExpiredErr):
			return nil, newGrpcErrorFromCodeAndErr(errorCodes.ErrChallengeExpired, err)
		case errors.As(err, &pairInternalErr):
			return nil, newGrpcErrorFromCodeAndErr(errorCodes.ErrPairInternalError, err)
		case errors.As(err, &muunAppletNotFoundErr):
			return nil, newGrpcErrorFromCodeAndErr(errorCodes.ErrAppletNotFound, err)
		case errors.As(err, &unsupportedVersionErr):
			return nil, newGrpcErrorFromCodeAndErr(
				errorCodes.ErrUnsupportedCardVersion,
				err,
			)
		case errors.As(err, &cardMustPairAgainErr):
			return nil, newGrpcErrorFromCodeAndErr(
				errorCodes.ErrCardMustPairAgain,
				err,
			)
		case errors.As(err, &cardOutOfPairingsErr):
			return nil, newGrpcErrorFromCodeAndErr(
				errorCodes.ErrCardOutOfPairings,
				err,
			)
		default:
			return nil, newGrpcErrorFromCodeAndErr(errorCodes.ErrSignInternalError, err)
		}
	}
	return api.SignMessageSecurityCardResponse_builder{
		FirmwareVersion: cardSigned.FirmwareVersion,
	}.Build(), nil
}

func (ws WalletServer) PairRequestChallenge(
	ctx context.Context,
	_ *emptypb.Empty,
) (*emptypb.Empty, error) {
	err := ws.securityCard.PairRequestChallenge(ctx)
	if err != nil {
		return nil, newGrpcErrorFromCodeAndErr(errorCodes.ErrPairInternalError, err)
	}
	return &emptypb.Empty{}, nil
}

func (ws WalletServer) PairSignAndSubmitChallenge(
	_ *emptypb.Empty,
	g grpc.ServerStreamingServer[api.PairSignAndSubmitChallengeProgress],
) error {
	cardPaired, err := ws.securityCard.PairSignAndSubmitChallenge(g.Context(), func(
		progress sc_model.PairProgress,
	) error {
		switch progress {
		case sc_model.PairProgressRefreshingChallenge:
			return g.Send(api.PairSignAndSubmitChallengeProgress_builder{
				RefreshingChallenge: &api.RefreshingChallenge{},
			}.Build())
		case sc_model.PairProgressChallengeSigned:
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
		var cardOutOfPairingsErr *sc_model.CardOutOfPairingsError
		var muunAppletNotFoundErr *sc_model.MuunAppletNotFoundError
		var unsupportedVersionErr *sc_model.UnsupportedCardVersionError
		var invalidMacErr *sc_model.InvalidMacError
		var challengeExpiredErr *sc_model.ChallengeExpiredError
		switch {
		case errors.As(err, &cardOutOfPairingsErr):
			return newGrpcErrorFromCodeAndErr(errorCodes.ErrCardOutOfPairings, err)
		case errors.As(err, &muunAppletNotFoundErr):
			return newGrpcErrorFromCodeAndErr(errorCodes.ErrAppletNotFound, err)
		case errors.As(err, &unsupportedVersionErr):
			return newGrpcErrorFromCodeAndErr(errorCodes.ErrUnsupportedCardVersion, err)
		case errors.As(err, &invalidMacErr):
			return newGrpcErrorFromCodeAndErr(errorCodes.ErrSignMacValidation, err)
		case errors.As(err, &challengeExpiredErr):
			return newGrpcErrorFromCodeAndErr(errorCodes.ErrChallengeExpired, err)
		default:
			return newGrpcErrorFromCodeAndErr(errorCodes.ErrPairInternalError, err)
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
	_ context.Context,
	_ *emptypb.Empty,
) (*api.DiagnosticSessionDescriptor, error) {
	sessionID := uuid.NewString()

	logBuffer := bytes.NewBuffer(nil)
	textHandler := slog.NewTextHandler(logBuffer, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})
	debugLog := slog.New(textHandler)

	err := ws.diagnostics.AddDiagnosticSession(&diagnostics_model.DiagnosticSessionData{
		ID:        sessionID,
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

	if sessionData, ok := ws.diagnostics.GetDiagnosticSession(sessionID); ok {
		reports, err := ws.recoveryTool.ScanForFunds(sessionData.Logger)
		if err != nil {
			return newGrpcError(goerr.Errorf("error scanning for funds: %w", err))
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
		return newGrpcError(goerr.Errorf("invalid sessionID %s", descriptor.GetSessionId()))
	}
}

func (ws WalletServer) SubmitDiagnosticLog(
	_ context.Context,
	descriptor *api.DiagnosticSessionDescriptor,
) (*api.DiagnosticSubmitStatus, error) {
	sessionID := descriptor.GetSessionId()
	if session, ok := ws.diagnostics.GetDiagnosticSession(sessionID); ok {
		err := ws.diagnostics.SubmitDiagnostic(sessionID, session.LogBuffer.String())
		if err != nil {
			return nil, err
		}

		ws.diagnostics.DeleteDiagnosticSession(sessionID)
		return api.DiagnosticSubmitStatus_builder{
			StatusCode:    200,
			StatusMessage: "OK",
		}.Build(), nil
	} else {
		return nil, goerr.Errorf("invalid sessionID %s", descriptor.GetSessionId())
	}
}

func (ws WalletServer) PrepareSweepTx(
	_ context.Context,
	parameters *api.PrepareSweepTxRequest,
) (*api.PrepareSweepTxResponse, error) {
	destinationAddressString := parameters.GetDestinationAddress()
	address, err := btcutil.DecodeAddress(destinationAddressString, ws.network.ToParams())
	if err != nil {
		return nil, err
	}

	descriptor := parameters.GetSessionDescriptor()

	sessionID := descriptor.GetSessionId()
	if session, ok := ws.diagnostics.GetDiagnosticSession(sessionID); ok {
		session.SweepTx, err = ws.recoveryTool.BuildSweepTx(
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
	_ context.Context,
	confirmation *api.SignAndBroadcastSweepTxRequest,
) (*api.SignAndBroadcastSweepTxResponse, error) {
	sessionID := confirmation.GetSessionDescriptor().GetSessionId()
	if session, ok := ws.diagnostics.GetDiagnosticSession(sessionID); ok {
		_, err := ws.recoveryTool.SignSweepTx(
			session.LastScanReport.UtxosFound,
			session.SweepTx,
			confirmation.GetRecoveryCode(),
		)
		if err != nil {
			return nil, err
		}

		return nil, goerr.New("signed the sweep tx but broadcasting it is not implemented")
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
	_ context.Context, req *api.ChallengeSetupRequest,
) (*api.SetupChallengeResponse, error) {

	challengeSetupJSON := model.ChallengeSetupJSON{
		Type:                req.GetType(),
		PublicKey:           req.GetPublicKey(),
		Salt:                req.GetSalt(),
		EncryptedPrivateKey: req.GetEncryptedPrivateKey(),
		Version:             int(req.GetVersion()),
	}

	setupChallengeResponseJSON, err := ws.recovery.StartChallengeSetup(
		challengeSetupJSON,
	)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to start challenge setup: %w", err))
	}

	return api.SetupChallengeResponse_builder{
		CosignerKey:            setupChallengeResponseJSON.CosignerKey,
		CosignerKeyFingerprint: setupChallengeResponseJSON.CosignerKeyFingerprint,
	}.Build(), nil
}

func (ws WalletServer) FinishRecoveryCodeSetup(
	_ context.Context,
	req *api.FinishRecoveryCodeSetupRequest,
) (*emptypb.Empty, error) {

	recoveryCodePublicKey, err := hexToPublicKey(req.GetRecoveryCodePublicKeyHex())
	if err != nil {
		return nil, goerr.Errorf("error parsing recovery code public key: %w", err)
	}

	err = ws.recovery.FinishChallengeSetup(recoveryCodePublicKey)
	if err != nil {
		return nil, err
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) PopulateEncryptedCosignerKey(
	_ context.Context,
	req *api.PopulateEncryptedCosignerKeyRequest,
) (*emptypb.Empty, error) {
	recoveryCodePublicKey, err := hexToPublicKey(req.GetRecoveryCodePublicKeyHex())
	if err != nil {
		return nil, goerr.Errorf("error parsing recovery code public key: %w", err)
	}

	err = ws.recovery.PopulateEncryptedCosignerKey(recoveryCodePublicKey)
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
		return nil, newGrpcErrorFromCode(errorCodes.ErrKeyEmpty)
	}
	if req.GetValue() == nil {
		return nil, newGrpcErrorFromCode(errorCodes.ErrValueEmpty)
	}

	value, err := toAny(req.GetValue())
	if err != nil {
		return nil, newGrpcError(
			goerr.Errorf("failed to convert proto Value to internal type: %w", err),
		)
	}

	err = ws.keyValueStorage.Save(req.GetKey(), value)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to save key with given data: %w", err))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) Get(_ context.Context, req *api.GetRequest) (*api.GetResponse, error) {

	key := req.GetKey()
	if key == "" {
		return nil, newGrpcErrorFromCode(errorCodes.ErrKeyEmpty)
	}

	value, err := ws.keyValueStorage.Get(key)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to get key: %w", err))
	}

	protoValue, err := toProtoValue(value)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to convert data to proto Value: %w", err))
	}

	return api.GetResponse_builder{
		Value: protoValue,
	}.Build(), nil
}

func (ws WalletServer) Delete(_ context.Context, req *api.DeleteRequest) (*emptypb.Empty, error) {
	if req.GetKey() == "" {
		return nil, newGrpcErrorFromCode(errorCodes.ErrKeyEmpty)
	}

	err := ws.keyValueStorage.Delete(req.GetKey())
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to delete key: %w", err))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) SaveBatch(
	_ context.Context,
	req *api.SaveBatchRequest,
) (*emptypb.Empty, error) {
	if req.GetItems() == nil {
		return nil, newGrpcErrorFromCode(errorCodes.ErrItemsEmpty)
	}

	items, err := toAnyMap(req.GetItems())
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to convert proto Struct to map: %w", err))
	}
	err = ws.keyValueStorage.SaveBatch(items)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to save batch with given data: %w", err))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) GetBatch(
	_ context.Context,
	req *api.GetBatchRequest,
) (*api.GetBatchResponse, error) {
	keys := req.GetKeys()
	if len(keys) == 0 {
		return nil, newGrpcErrorFromCode(errorCodes.ErrKeyEmpty)
	}

	items, err := ws.keyValueStorage.GetBatch(keys)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to get batch with given keys: %w", err))
	}

	if len(items) == 0 {
		return nil, newGrpcError(goerr.Errorf("failed to found values for keys: %v", keys))
	}

	protoItems, err := toProtoValueMap(items)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to convert data to proto Struct: %w", err))
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
		return nil, newGrpcErrorFromCode(errorCodes.ErrKeyEmpty)
	}

	items, err := ws.keyValueStorage.GetByPrefix(prefix)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to get by prefix with given prefix: %w", err))
	}

	protoItems, err := toProtoValueMap(items)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to convert data to proto Struct: %w", err))
	}

	return api.GetBatchResponse_builder{
		Items: protoItems,
	}.Build(), nil
}

func (ws WalletServer) FetchSecurityCardsCountries(
	_ context.Context, _ *emptypb.Empty,
) (*api.SCMCountriesResponse, error) {
	countries, err := ws.marketplace.FetchAvailableCountries()
	if err != nil {
		return nil, newGrpcError(
			goerr.Errorf(
				"failed to fetch marketplace countries: %w", err,
			),
		)
	}

	protoCountries := make(
		[]*api.SCMCountryInfo, 0, len(countries),
	)
	for _, c := range countries {
		protoCountries = append(
			protoCountries,
			api.SCMCountryInfo_builder{Code: c.Code}.Build(),
		)
	}

	return api.SCMCountriesResponse_builder{
		Countries: protoCountries,
	}.Build(), nil
}

func (ws WalletServer) FetchSecurityCardsCountriesByProvider(
	_ context.Context, req *api.SCMCountriesByProviderRequest,
) (*api.SCMCountriesResponse, error) {
	countries, err := ws.marketplace.FetchAvailableCountriesByProvider(
		req.GetProviderUuid(),
	)
	if err != nil {
		return nil, newGrpcError(
			goerr.Errorf(
				"failed to fetch marketplace countries for provider: %w",
				err,
			),
		)
	}

	protoCountries := make(
		[]*api.SCMCountryInfo, 0, len(countries),
	)
	for _, c := range countries {
		protoCountries = append(
			protoCountries,
			api.SCMCountryInfo_builder{Code: c.Code}.Build(),
		)
	}

	return api.SCMCountriesResponse_builder{
		Countries: protoCountries,
	}.Build(), nil
}

func (ws WalletServer) FetchSecurityCardsMarketplaceByCountry(
	_ context.Context,
	req *api.SCMMarketplaceRequest,
) (*api.SCMMarketplaceResponse, error) {
	country := marketplace_model.NewCountryInfo(
		req.GetCountry().GetCode(),
	)

	listings, err := ws.marketplace.FetchProviderListingsByCountry(country)
	if err != nil {
		return nil, newGrpcError(
			goerr.Errorf(
				"failed to fetch marketplace listings: %w", err,
			),
		)
	}

	protoListings := make(
		[]*api.SCMMarketplaceResponse_ProviderListing,
		0,
		len(listings),
	)
	for _, listing := range listings {
		protoListings = append(
			protoListings,
			api.SCMMarketplaceResponse_ProviderListing_builder{
				Provider: toProtoSCMProvider(
					listing.Provider,
				),
				Cards: toProtoSCMCards(listing.Cards),
				EstimatedShippingPrice: toProtoSCMBitcoinAmount(
					listing.ShippingPrice,
				),
				CardPrice: toProtoSCMBitcoinAmount(
					listing.CardPrice,
				),
			}.Build(),
		)
	}

	return api.SCMMarketplaceResponse_builder{
		Providers: protoListings,
	}.Build(), nil
}

func (ws WalletServer) FetchSecurityCardDetail(
	_ context.Context,
	req *api.SCMCardDetailRequest,
) (*api.SCMCardDetailResponse, error) {
	country := marketplace_model.NewCountryInfo(
		req.GetCountry().GetCode(),
	)

	offer, err := ws.marketplace.FetchCardOffer(
		country, req.GetSecurityCardUuid(),
	)
	if err != nil {
		return nil, newGrpcError(
			goerr.Errorf(
				"failed to fetch card detail: %w", err,
			),
		)
	}

	return api.SCMCardDetailResponse_builder{
		Provider: api.SCMCardDetailResponse_Provider_builder{
			Provider: toProtoSCMProvider(offer.Provider),
			ShippingCountry: api.SCMCountryInfo_builder{
				Code: offer.ShippingCountry.Code,
			}.Build(),
			MinShippingTimeDays: offer.MinShippingTimeDays,
			MaxShippingTimeDays: offer.MaxShippingTimeDays,
			EstimatedShippingPrice: toProtoSCMBitcoinAmount(
				offer.ShippingPrice,
			),
			CardPrice: toProtoSCMBitcoinAmount(offer.CardPrice),
		}.Build(),
		Card: toProtoSCMCard(offer.Card),
	}.Build(), nil
}

func (ws WalletServer) FetchSecurityCardFullSpecs(
	_ context.Context,
	req *api.SCMFullSpecsRequest,
) (*api.SCMFullSpecsResponse, error) {
	country := marketplace_model.NewCountryInfo(
		req.GetCountry().GetCode(),
	)

	offer, err := ws.marketplace.FetchCardOffer(
		country, req.GetSecurityCardUuid(),
	)
	if err != nil {
		return nil, newGrpcError(
			goerr.Errorf(
				"failed to fetch card full specs: %w", err,
			),
		)
	}

	return api.SCMFullSpecsResponse_builder{
		Provider: api.SCMFullSpecsResponse_Provider_builder{
			Provider: toProtoSCMProvider(offer.Provider),
			ShippingCountry: api.SCMCountryInfo_builder{
				Code: offer.ShippingCountry.Code,
			}.Build(),
			MinShippingTimeDays: offer.MinShippingTimeDays,
			MaxShippingTimeDays: offer.MaxShippingTimeDays,
		}.Build(),
		Card: api.SCMFullSpecsResponse_Card_builder{
			Card:        toProtoSCMCard(offer.Card),
			WidthMm:     offer.Card.WidthMm,
			HeightMm:    offer.Card.HeightMm,
			ThicknessMm: offer.Card.ThicknessMm,
			WeightGrams: offer.Card.WeightGrams,
			SecureElement: toProtoSCMSecureElement(
				offer.Card.SecureElement,
			),
		}.Build(),
	}.Build(), nil
}

func (ws WalletServer) FetchSecurityCardShippingMethods(
	ctx context.Context,
	req *api.SCMShippingMethodsRequest,
) (*api.SCMShippingMethodsResponse, error) {
	country := marketplace_model.NewCountryInfo(
		req.GetCountry().GetCode(),
	)

	shippingMethods, err := ws.marketplace.FetchShippingMethods(
		ctx, country, req.GetSecurityCardUuid(),
	)
	if err != nil {
		err = goerr.Errorf("failed to fetch shipping methods: %w", err)

		var providerErr *marketplace_model.ProviderError
		if errors.As(err, &providerErr) {
			return nil, newGrpcErrorFromCodeAndErr(
				toMarketplaceErrorCode(providerErr.Kind), err,
			)
		}
		return nil, newGrpcError(err)
	}

	return api.SCMShippingMethodsResponse_builder{
		ShippingOptions: toProtoSCMShippingOptions(shippingMethods),
	}.Build(), nil
}

func (ws WalletServer) SaveSecurityCardsMarketplaceControlPlane(
	_ context.Context,
	req *api.SCMSaveControlPlaneRequest,
) (*emptypb.Empty, error) {
	listings := make(
		[]marketplace_model.ControlPlaneListing, 0, len(req.GetListings()),
	)
	for _, listing := range req.GetListings() {
		listings = append(listings, marketplace_model.NewControlPlaneListing(
			listing.GetProviderUuid(),
			listing.GetCountries(),
		))
	}
	controlPlane := marketplace_model.NewControlPlane(
		req.GetCountries(),
		listings,
		req.GetCards(),
	)

	if err := ws.marketplace.SaveControlPlane(controlPlane); err != nil {
		return nil, newGrpcError(goerr.Errorf(
			"store marketplace control plane: %w", err,
		))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) RefreshSecurityCardsMarketplaceCards(
	_ context.Context,
	_ *emptypb.Empty,
) (*emptypb.Empty, error) {
	if err := ws.marketplace.RefreshCards(); err != nil {
		return nil, newGrpcError(goerr.Errorf(
			"failed to refresh marketplace cards: %w", err,
		))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) RefreshSecurityCardsMarketplacePrices(
	_ context.Context,
	_ *emptypb.Empty,
) (*emptypb.Empty, error) {
	if err := ws.marketplace.RefreshPrices(); err != nil {
		return nil, newGrpcError(goerr.Errorf(
			"failed to refresh marketplace prices: %w", err,
		))
	}

	return &emptypb.Empty{}, nil
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
	ctx context.Context,
	_ *emptypb.Empty,
) (*emptypb.Empty, error) {
	if err := ws.backgroundActions.Reset(ctx); err != nil {
		// We swallow any background actions subsystem reset errors.
		tracing.AddTag(ctx, "background_actions_reset_failed", true)
		logging.Error(ctx, "Failed to reset the background actions subsystem",
			"error", err,
		)
	}

	if err := ws.resetData(); err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to reset database: %w", err))
	}

	return &emptypb.Empty{}, nil
}

func (ws WalletServer) RunDueBackgroundActions(
	ctx context.Context,
	req *api.RunDueBackgroundActionsRequest,
) (*api.RunDueBackgroundActionsResponse, error) {
	executionCtx := ba_model.FromProtoExecutionContext(req.GetExecutionContext())

	err := ws.backgroundActions.RunDue(ctx, executionCtx)
	if err != nil {
		// The caller canceled the call or its deadline passed.
		// We preserve the correct error code.
		if ctxErr := ctx.Err(); ctxErr != nil {
			return nil, status.Error(status.FromContextError(ctxErr).Code(), err.Error())
		}
		return nil, newGrpcError(err)
	}

	return &api.RunDueBackgroundActionsResponse{}, nil
}

// GenerateEmergencyKitPDF outputPath must be the full path where the PDF should be saved (including
// filename). Example: "/path/to/documents/expected_kit_name.pdf" The directory will be created if
// it doesn't exist.
func (ws WalletServer) GenerateEmergencyKitPDF(
	_ context.Context,
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

	result, err := ws.emergencyKit.GenerateEmergencyKitPDF(
		ekParams,
		request.GetOutputPath(),
		request.GetLanguage(),
	)
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to generate emergency kit PDF: %w", err))
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
			KitSizeBytes:            profiling.KitSizeBytes,
			DrawIconsMs:             profiling.DrawIconsMs,
		}.Build(),
	}.Build(), nil
}

func (ws WalletServer) ZipDataDir(
	_ context.Context,
	req *api.ZipDataDirRequest,
) (*emptypb.Empty, error) {
	err := ws.diagnostics.ZipDataDir(req.GetOutputPath())
	if err != nil {
		return nil, newGrpcError(goerr.Errorf("failed to zip data directory: %w", err))
	}
	return &emptypb.Empty{}, nil
}

func (ws WalletServer) SaveExchangeRateWindow(
	ctx context.Context,
	req *api.SaveExchangeRateWindowRequest,
) (*emptypb.Empty, error) {
	err := ws.rates.SaveExchangeRateWindow(
		ctx,
		rates_model.FromProtoExchangeRateWindow(req.GetWindow()),
	)
	if err != nil {
		var invalidWindowErr *rates_model.InvalidExchangeRateWindowError
		if errors.As(err, &invalidWindowErr) {
			return nil, newGrpcErrorFromCodeAndErr(errorCodes.ErrInvalidRateWindow, err)
		}
		return nil, newGrpcError(err)
	}
	return &emptypb.Empty{}, nil
}

func (ws WalletServer) GetExchangeRateWindow(
	ctx context.Context,
	_ *emptypb.Empty,
) (*api.GetExchangeRateWindowResponse, error) {
	window, err := ws.rates.LoadExchangeRateWindow(ctx)
	if err != nil {
		return nil, newGrpcError(err)
	}
	if window == nil {
		return api.GetExchangeRateWindowResponse_builder{}.Build(), nil
	}

	return api.GetExchangeRateWindowResponse_builder{
		Window: rates_model.ToProtoExchangeRateWindow(*window),
	}.Build(), nil
}
