package diagnostic_mode

import (
	"github.com/go-errors/errors"

	diagnostics_model "github.com/muun/libwallet/subsystems/diagnostics/presentation/model"
)

var diagnosticData = make(map[string]*diagnostics_model.DiagnosticSessionData)

func AddDiagnosticSession(data *diagnostics_model.DiagnosticSessionData) error {
	if _, ok := diagnosticData[data.ID]; ok {
		return errors.Errorf("id %s already exists", data.ID)
	}

	diagnosticData[data.ID] = data
	return nil
}

func GetDiagnosticSession(
	id string,
) (*diagnostics_model.DiagnosticSessionData, bool) {
	result, ok := diagnosticData[id]
	return result, ok
}

func DeleteDiagnosticSession(id string) {
	delete(diagnosticData, id)
}
