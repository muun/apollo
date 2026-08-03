package service

import (
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/service/model"
)

type HoustonService interface {
	HealthCheck() error
	ChallengeKeySetupStart(req model.ChallengeSetupJson) (model.SetupChallengeResponseJson, error)
	ChallengeKeySetupFinish(req model.ChallengeSetupVerifyJson) error
	ChallengeSetupFinishWithVerifiableMuunKey(
		req model.ChallengeSetupVerifyJson,
	) (model.VerifiableMuunKeyJson, error)
	VerifiableMuunKey() (model.VerifiableMuunKeyJson, error)
	CreateFirstSession(
		createSessionJson model.CreateFirstSessionJson, //nolint:staticcheck // TODO: interface method parameter createSessionJson should be createSessionJSON
	) (model.CreateFirstSessionOkJson, error)
	FetchFeeWindow() (model.FeeWindowJson, error)
	SubmitDiagnosticsScanData(req model.DiagnosticScanDataJson) error
	PairRequestChallenge() (model.PairRequestChallengeResponseJSON, error)
	PairSubmitSignedChallenge(
		req model.PairSubmitSignedChallengeJSON,
	) (model.PairSubmitSignedChallengeResponseJSON, error)
	SignRequestChallenge(
		req model.SignRequestChallengeJSON,
	) (model.SignRequestChallengeResponseJSON, error)
	SignSubmitSignedChallenge(req model.SignSubmitSignedChallengeJSON) error
	RegisterSecurityCard(
		req model.RegisterSecurityCardJson,
	) (model.RegisterSecurityCardOkJson, error)
	ChallengeSecurityCardSign(
		req model.ChallengeSecurityCardSignJson,
	) (model.ChallengeSecurityCardSignResponseJson, error)
	SolveSecurityCardChallenge(req model.SolveSecurityCardChallengeJson) error
	FetchSecurityCardsMarketplace() (model.SecurityCardsMarketplaceJson, error)
}

type HoustonClient struct {
	client client
}

var _ HoustonService = (*HoustonClient)(nil)

func NewHoustonService(configurator app_provided_data.HttpClientSessionProvider) HoustonService {
	return &HoustonClient{client: client{configurator: configurator}}
}

func (h *HoustonClient) HealthCheck() error {
	r := request[any]{
		Method: MethodGet,
		Path:   "/admin/healthcheck",
		Body:   nil,
	}
	_, err := r.do(&h.client)
	return err
}

func (h *HoustonClient) ChallengeKeySetupStart(
	req model.ChallengeSetupJson,
) (model.SetupChallengeResponseJson, error) {
	r := request[model.SetupChallengeResponseJson]{
		Method: MethodPost,
		Path:   "/user/challenge/setup/start",
		Body:   req,
	}
	return r.do(&h.client)
}

func (h *HoustonClient) ChallengeKeySetupFinish(req model.ChallengeSetupVerifyJson) error {
	r := request[any]{
		Method: MethodPost,
		Path:   "/user/challenge/setup/finish",
		Body:   req,
	}

	_, err := r.do(&h.client)
	return err
}

func (h *HoustonClient) ChallengeSetupFinishWithVerifiableMuunKey(
	req model.ChallengeSetupVerifyJson,
) (model.VerifiableMuunKeyJson, error) {

	r := request[model.VerifiableMuunKeyJson]{
		Method: MethodPost,
		Path:   "/user/challenge/setup/finish-with-verifiable-muun-key",
		Body:   req,
	}

	return r.do(&h.client)
}

func (h *HoustonClient) VerifiableMuunKey() (model.VerifiableMuunKeyJson, error) {
	r := request[model.VerifiableMuunKeyJson]{
		Method: MethodGet,
		Path:   "/user/verifiable-muun-key",
	}

	return r.do(&h.client)
}

func (h *HoustonClient) CreateFirstSession(
	createSessionJson model.CreateFirstSessionJson, //nolint:staticcheck // TODO: method parameter createSessionJson should be createSessionJSON
) (model.CreateFirstSessionOkJson, error) {

	r := request[model.CreateFirstSessionOkJson]{
		Method: MethodPost,
		Path:   "sessions-v2/first",
		Body:   createSessionJson,
	}
	return r.do(&h.client)
}

func (h *HoustonClient) FetchFeeWindow() (model.FeeWindowJson, error) {
	r := request[model.FeeWindowJson]{
		Method: MethodGet,
		Path:   "fees/latest",
	}
	return r.do(&h.client)
}

func (h *HoustonClient) SubmitDiagnosticsScanData(req model.DiagnosticScanDataJson) error {
	r := request[any]{
		Method: MethodPost,
		Path:   "diagnostics/submit_scan_data",
		Body:   req,
	}
	_, err := r.do(&h.client)
	return err
}

func (h *HoustonClient) PairRequestChallenge() (model.PairRequestChallengeResponseJSON, error) {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) PairSubmitSignedChallenge(
	req model.PairSubmitSignedChallengeJSON, //nolint:revive // req is required by the interface; unused until this production stub is implemented
) (model.PairSubmitSignedChallengeResponseJSON, error) {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) SignRequestChallenge(
	req model.SignRequestChallengeJSON, //nolint:revive // req is required by the interface; unused until this production stub is implemented
) (model.SignRequestChallengeResponseJSON, error) {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) SignSubmitSignedChallenge(
	req model.SignSubmitSignedChallengeJSON, //nolint:revive // req is required by the interface; unused until this production stub is implemented
) error {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) RegisterSecurityCard(
	req model.RegisterSecurityCardJson, //nolint:revive // TODO: use or remove req
) (model.RegisterSecurityCardOkJson, error) {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) ChallengeSecurityCardSign(
	req model.ChallengeSecurityCardSignJson, //nolint:revive // TODO: use or remove req
) (model.ChallengeSecurityCardSignResponseJson, error) {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) SolveSecurityCardChallenge(
	req model.SolveSecurityCardChallengeJson, //nolint:revive // TODO: use or remove req
) error {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) FetchSecurityCardsMarketplace() (
	model.SecurityCardsMarketplaceJson,
	error,
) {
	//TODO implement me
	panic("implement me")
}
