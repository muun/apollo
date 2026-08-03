package diagnostic_mode

import (
	"bytes"
	"log/slog"

	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/scanner"
)

type DiagnosticSessionData struct {
	Id             string //nolint:staticcheck // TODO: struct field Id should be ID
	LogBuffer      *bytes.Buffer
	Logger         *slog.Logger
	LastScanReport *scanner.Report
	SweepTx        *wire.MsgTx
}

var diagnosticData = make(map[string]*DiagnosticSessionData)

func AddDiagnosticSession(data *DiagnosticSessionData) error {
	if _, ok := diagnosticData[data.Id]; ok {
		return errors.Errorf("id %s already exists", data.Id)
	}

	diagnosticData[data.Id] = data
	return nil
}

func GetDiagnosticSession(id string) (*DiagnosticSessionData, bool) {
	result, ok := diagnosticData[id]
	return result, ok
}

func DeleteDiagnosticSession(id string) {
	delete(diagnosticData, id)
}
