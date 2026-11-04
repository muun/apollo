package presentation

import (
	"github.com/muun/libwallet/subsystems/diagnostics/internal/domain/action/debug"
	"github.com/muun/libwallet/subsystems/diagnostics/internal/domain/action/diagnostic_mode_reports"
	"github.com/muun/libwallet/subsystems/diagnostics/internal/domain/diagnostic_mode"
	diagnostics_model "github.com/muun/libwallet/subsystems/diagnostics/presentation/model"
)

type Diagnostics struct {
	zipDataDir       debug.ZipDataDirAction
	submitDiagnostic diagnostic_mode_reports.SubmitDiagnosticAction
	sessionStore     diagnostic_mode.SessionStore
}

func NewDiagnostics(
	zipDataDir debug.ZipDataDirAction,
	submitDiagnostic diagnostic_mode_reports.SubmitDiagnosticAction,
	sessionStore diagnostic_mode.SessionStore,
) Diagnostics {
	return Diagnostics{
		zipDataDir:       zipDataDir,
		submitDiagnostic: submitDiagnostic,
		sessionStore:     sessionStore,
	}
}

func (d *Diagnostics) ZipDataDir(outputPath string) error {
	return d.zipDataDir.Run(outputPath)
}

func (d *Diagnostics) SubmitDiagnostic(sessionID string, debugLog string) error {
	return d.submitDiagnostic.Run(sessionID, debugLog)
}

func (d *Diagnostics) AddDiagnosticSession(
	data *diagnostics_model.DiagnosticSessionData,
) error {
	return d.sessionStore.Add(data)
}

func (d *Diagnostics) GetDiagnosticSession(
	id string,
) (*diagnostics_model.DiagnosticSessionData, bool) {
	return d.sessionStore.Get(id)
}

func (d *Diagnostics) DeleteDiagnosticSession(id string) {
	d.sessionStore.Delete(id)
}
