package diagnostic_mode_reports

import (
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/base/data/houston/model"
)

type SubmitDiagnosticAction interface {
	Run(sessionID string, debugLog string) error
}

type submitDiagnosticAction struct {
	houstonService houston.HoustonService
}

func NewSubmitDiagnosticAction(service houston.HoustonService) SubmitDiagnosticAction {
	return &submitDiagnosticAction{service}
}

func (action *submitDiagnosticAction) Run(
	sessionID string,
	debugLog string,
) error {
	return action.houstonService.SubmitDiagnosticsScanData(model.DiagnosticScanDataJSON{
		ScanID: sessionID,
		Logs:   debugLog,
	})
}
