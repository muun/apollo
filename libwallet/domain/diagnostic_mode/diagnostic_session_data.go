package diagnostic_mode

import (
	"bytes"
	"fmt"
	"github.com/btcsuite/btcd/wire"
	"github.com/muun/libwallet/scanner"
	"log/slog"
)

type DiagnosticSessionData struct {
	Id             string
	LogBuffer      *bytes.Buffer
	Logger         *slog.Logger
	LastScanReport *scanner.Report
	SweepTx        *wire.MsgTx
}

var diagnosticData = make(map[string]*DiagnosticSessionData)

func AddDiagnosticSession(data *DiagnosticSessionData) error {
	if _, ok := diagnosticData[data.Id]; ok {
		return fmt.Errorf("id %s already exists", data.Id)
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
