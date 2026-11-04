package presentation

import (
	diagnostics_model "github.com/muun/libwallet/subsystems/diagnostics/presentation/model"
)

type Diagnostics interface {
	ZipDataDir(outputPath string) error
	SubmitDiagnostic(sessionID string, debugLog string) error
	AddDiagnosticSession(data *diagnostics_model.DiagnosticSessionData) error
	GetDiagnosticSession(id string) (*diagnostics_model.DiagnosticSessionData, bool)
	DeleteDiagnosticSession(id string)
}
