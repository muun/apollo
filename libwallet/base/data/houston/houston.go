package houston

import (
	"github.com/muun/libwallet/app_provided_data"
	"github.com/muun/libwallet/base/data/houston/model"
)

type HoustonService interface {
	HealthCheck() error
	ChallengeKeySetupStart(req model.ChallengeSetupJSON) (model.SetupChallengeResponseJSON, error)
	ChallengeKeySetupFinish(req model.ChallengeSetupVerifyJSON) error
	ChallengeSetupFinishWithVerifiableCosignerKey(
		req model.ChallengeSetupVerifyJSON,
	) (model.VerifiableCosignerKeyJSON, error)
	VerifiableCosignerKey() (model.VerifiableCosignerKeyJSON, error)
	CreateFirstSession(
		createSessionJSON model.CreateFirstSessionJSON,
	) (model.CreateFirstSessionOkJSON, error)
	FetchFeeWindow() (model.FeeWindowJSON, error)
	SubmitDiagnosticsScanData(req model.DiagnosticScanDataJSON) error
	PairRequestChallenge() (model.PairRequestChallengeResponseJSON, error)
	PairSubmitSignedChallenge(
		req model.PairSubmitSignedChallengeJSON,
	) (model.PairSubmitSignedChallengeResponseJSON, error)
	SignRequestChallenge(
		req model.SignRequestChallengeJSON,
	) (model.SignRequestChallengeResponseJSON, error)
	SignSubmitSignedChallenge(req model.SignSubmitSignedChallengeJSON) error
	FetchSecurityCardsMarketplaceCards() (model.SecurityCardsMarketplaceCardsJSON, error)
	FetchSecurityCardsMarketplacePrices() (model.SecurityCardsMarketplacePricesJSON, error)
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
	req model.ChallengeSetupJSON,
) (model.SetupChallengeResponseJSON, error) {
	r := request[model.SetupChallengeResponseJSON]{
		Method: MethodPost,
		Path:   "/user/challenge/setup/start",
		Body:   req,
	}
	return r.do(&h.client)
}

func (h *HoustonClient) ChallengeKeySetupFinish(req model.ChallengeSetupVerifyJSON) error {
	r := request[any]{
		Method: MethodPost,
		Path:   "/user/challenge/setup/finish",
		Body:   req,
	}

	_, err := r.do(&h.client)
	return err
}

func (h *HoustonClient) ChallengeSetupFinishWithVerifiableCosignerKey(
	req model.ChallengeSetupVerifyJSON,
) (model.VerifiableCosignerKeyJSON, error) {

	r := request[model.VerifiableCosignerKeyJSON]{
		Method: MethodPost,
		Path:   "/user/challenge/setup/finish-with-verifiable-muun-key",
		Body:   req,
	}

	return r.do(&h.client)
}

func (h *HoustonClient) VerifiableCosignerKey() (model.VerifiableCosignerKeyJSON, error) {
	r := request[model.VerifiableCosignerKeyJSON]{
		Method: MethodGet,
		Path:   "/user/verifiable-muun-key",
	}

	return r.do(&h.client)
}

func (h *HoustonClient) CreateFirstSession(
	createSessionJSON model.CreateFirstSessionJSON,
) (model.CreateFirstSessionOkJSON, error) {

	r := request[model.CreateFirstSessionOkJSON]{
		Method: MethodPost,
		Path:   "sessions-v2/first",
		Body:   createSessionJSON,
	}
	return r.do(&h.client)
}

func (h *HoustonClient) FetchFeeWindow() (model.FeeWindowJSON, error) {
	r := request[model.FeeWindowJSON]{
		Method: MethodGet,
		Path:   "fees/latest",
	}
	return r.do(&h.client)
}

func (h *HoustonClient) SubmitDiagnosticsScanData(req model.DiagnosticScanDataJSON) error {
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

func (h *HoustonClient) FetchSecurityCardsMarketplaceCards() (
	model.SecurityCardsMarketplaceCardsJSON,
	error,
) {
	//TODO implement me
	panic("implement me")
}

func (h *HoustonClient) FetchSecurityCardsMarketplacePrices() (
	model.SecurityCardsMarketplacePricesJSON,
	error,
) {
	//TODO implement me
	panic("implement me")
}
