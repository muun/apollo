package service

import (
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/service/model"
)

type HoustonService struct {
	client client
}

func NewHoustonService(configurator app_provided_data.HttpClientSessionProvider) *HoustonService {
	return &HoustonService{client: client{configurator: configurator}}
}

func (h *HoustonService) ChallengeKeySetupStart(req model.ChallengeSetupJson) (model.SetupChallengeResponseJson, error) {
	r := request[model.SetupChallengeResponseJson]{
		Method: MethodPost,
		Path:   "/user/challenge/setup/start",
		Body:   req,
	}
	return r.do(&h.client)
}

func (h *HoustonService) ChallengeKeySetupFinish(req model.ChallengeSetupVerifyJson) error {
	r := request[any]{
		Method: MethodPost,
		Path:   "/user/challenge/setup/finish",
		Body:   req,
	}

	_, err := r.do(&h.client)
	return err
}

func (h *HoustonService) VerifiableServerCosginingKey() (model.VerifiableServerCosigningKeyJson, error) {
	r := request[model.VerifiableServerCosigningKeyJson]{
		Method: MethodGet,
		Path:   "/user/verifiable-server-cosigning-key",
	}

	return r.do(&h.client)
}

func (h *HoustonService) CreateFirstSession(
	createSessionJson model.CreateFirstSessionJson,
) (model.CreateFirstSessionOkJson, error) {

	r := request[model.CreateFirstSessionOkJson]{
		Method: MethodPost,
		Path:   "sessions-v2/first",
		Body:   createSessionJson,
	}
	return r.do(&h.client)
}

func (h *HoustonService) FetchFeeWindow() (model.FeeWindowJson, error) {
	r := request[model.FeeWindowJson]{
		Method: MethodGet,
		Path:   "fees/latest",
	}
	return r.do(&h.client)
}

func (h *HoustonService) SubmitDiagnosticsScanData(req model.DiagnosticScanDataJson) error {
	r := request[any]{
		Method: MethodPost,
		Path:   "diagnostics/submit_scan_data",
		Body:   req,
	}
	_, err := r.do(&h.client)
	return err
}
