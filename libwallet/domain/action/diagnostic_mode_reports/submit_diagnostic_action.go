package diagnostic_mode_reports

import (
	"github.com/muun/libwallet/service"
	"github.com/muun/libwallet/service/model"
)

type SubmitDiagnosticAction struct {
	houstonService service.HoustonService
}

func NewSubmitDiagnosticAction(service service.HoustonService) *SubmitDiagnosticAction {
	return &SubmitDiagnosticAction{service}
}

func (action SubmitDiagnosticAction) Run(sessionId string, debugLog string) error {
	return action.houstonService.SubmitDiagnosticsScanData(model.DiagnosticScanDataJson{
		ScanId: sessionId,
		Logs:   debugLog,
	})
}
