package testutils

import (
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
)

// Compile-time check.
var _ service.HoustonService = (*MockHoustonService)(nil)

// MockHoustonService is a configurable test double for service.HoustonService.
// Set the fields you need for your test; all unconfigured methods panic.
type MockHoustonService struct {
	VerifiableMuunKeyResult    model.VerifiableMuunKeyJson
	VerifiableMuunKeyErr       error
	FinishWithVerifiableResult model.VerifiableMuunKeyJson
	FinishWithVerifiableErr    error

	// Captured requests for assertions
	CapturedChallengeSetupVerify *model.ChallengeSetupVerifyJson
}

func (m *MockHoustonService) VerifiableMuunKey() (model.VerifiableMuunKeyJson, error) {
	return m.VerifiableMuunKeyResult, m.VerifiableMuunKeyErr
}

func (m *MockHoustonService) ChallengeSetupFinishWithVerifiableMuunKey(
	req model.ChallengeSetupVerifyJson,
) (model.VerifiableMuunKeyJson, error) {
	m.CapturedChallengeSetupVerify = &req
	return m.FinishWithVerifiableResult, m.FinishWithVerifiableErr
}

// Methods below are not used in the actions under test — they panic if called.

func (m *MockHoustonService) HealthCheck() error {
	panic("MockHoustonService: unexpected call to HealthCheck")
}

func (m *MockHoustonService) ChallengeKeySetupStart(
	model.ChallengeSetupJson,
) (model.SetupChallengeResponseJson, error) {
	panic("MockHoustonService: unexpected call to ChallengeKeySetupStart")
}

func (m *MockHoustonService) ChallengeKeySetupFinish(model.ChallengeSetupVerifyJson) error {
	panic("MockHoustonService: unexpected call to ChallengeKeySetupFinish")
}

func (m *MockHoustonService) CreateFirstSession(
	model.CreateFirstSessionJson,
) (model.CreateFirstSessionOkJson, error) {
	panic("MockHoustonService: unexpected call to CreateFirstSession")
}

func (m *MockHoustonService) FetchFeeWindow() (model.FeeWindowJson, error) {
	panic("MockHoustonService: unexpected call to FetchFeeWindow")
}

func (m *MockHoustonService) SubmitDiagnosticsScanData(model.DiagnosticScanDataJson) error {
	panic("MockHoustonService: unexpected call to SubmitDiagnosticsScanData")
}

func (m *MockHoustonService) PairRequestChallenge() (model.PairRequestChallengeResponseJSON, error) {
	panic("MockHoustonService: unexpected call to PairRequestChallenge")
}

func (m *MockHoustonService) PairSubmitSignedChallenge(
	model.PairSubmitSignedChallengeJSON,
) (model.PairSubmitSignedChallengeResponseJSON, error) {
	panic("MockHoustonService: unexpected call to PairSubmitSignedChallenge")
}

func (m *MockHoustonService) SignRequestChallenge(
	model.SignRequestChallengeJSON,
) (model.SignRequestChallengeResponseJSON, error) {
	panic("MockHoustonService: unexpected call to SignRequestChallenge")
}

func (m *MockHoustonService) SignSubmitSignedChallenge(
	model.SignSubmitSignedChallengeJSON,
) error {
	panic("MockHoustonService: unexpected call to SignSubmitSignedChallenge")
}

func (m *MockHoustonService) RegisterSecurityCard(
	model.RegisterSecurityCardJson,
) (model.RegisterSecurityCardOkJson, error) {
	panic("MockHoustonService: unexpected call to RegisterSecurityCard")
}

func (m *MockHoustonService) ChallengeSecurityCardSign(
	model.ChallengeSecurityCardSignJson,
) (model.ChallengeSecurityCardSignResponseJson, error) {
	panic("MockHoustonService: unexpected call to ChallengeSecurityCardSign")
}

func (m *MockHoustonService) SolveSecurityCardChallenge(
	model.SolveSecurityCardChallengeJson,
) error {
	panic("MockHoustonService: unexpected call to SolveSecurityCardChallenge")
}

func (m *MockHoustonService) FetchSecurityCardsMarketplace() (model.SecurityCardsMarketplaceJson, error) {
	panic("MockHoustonService: unexpected call to FetchSecurityCardsMarketplace")
}
