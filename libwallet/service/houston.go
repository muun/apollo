package service

import (
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/service/model"
)

type HoustonService interface {
	HealthCheck() error
	ChallengeKeySetupStart(req model.ChallengeSetupJson) (model.SetupChallengeResponseJson, error)
	ChallengeKeySetupFinish(req model.ChallengeSetupVerifyJson) error
	ChallengeSetupFinishWithVerifiableMuunKey(req model.ChallengeSetupVerifyJson) (model.VerifiableMuunKeyJson, error)
	VerifiableMuunKey() (model.VerifiableMuunKeyJson, error)
	CreateFirstSession(createSessionJson model.CreateFirstSessionJson) (model.CreateFirstSessionOkJson, error)
	FetchFeeWindow() (model.FeeWindowJson, error)
	SubmitDiagnosticsScanData(req model.DiagnosticScanDataJson) error
	ChallengeSecurityCardPair() (model.ChallengeSecurityCardPairJson, error)
	RegisterSecurityCard(req model.RegisterSecurityCardJson) (model.RegisterSecurityCardOkJson, error)
	ChallengeSecurityCardSign(req model.ChallengeSecurityCardSignJson) (model.ChallengeSecurityCardSignResponseJson, error)
	SolveSecurityCardChallenge(req model.SolveSecurityCardChallengeJson) error
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

func (h *HoustonClient) ChallengeKeySetupStart(req model.ChallengeSetupJson) (model.SetupChallengeResponseJson, error) {
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

func (h *HoustonClient) ChallengeSetupFinishWithVerifiableMuunKey(req model.ChallengeSetupVerifyJson) (model.VerifiableMuunKeyJson, error) {

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
	createSessionJson model.CreateFirstSessionJson,
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

func (h *HoustonClient) ChallengeSecurityCardPair() (model.ChallengeSecurityCardPairJson, error) {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) RegisterSecurityCard(
	req model.RegisterSecurityCardJson,
) (model.RegisterSecurityCardOkJson, error) {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) ChallengeSecurityCardSign(
	req model.ChallengeSecurityCardSignJson,
) (model.ChallengeSecurityCardSignResponseJson, error) {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) SolveSecurityCardChallenge(req model.SolveSecurityCardChallengeJson) error {
	//TODO implement me
	panic("implement me")
}
