package model

import (
	"bytes"
	"log/slog"

	"github.com/btcsuite/btcd/wire"

	"github.com/muun/libwallet/legacy/scanner"
)

type DiagnosticSessionData struct {
	ID             string
	LogBuffer      *bytes.Buffer
	Logger         *slog.Logger
	LastScanReport *scanner.Report
	SweepTx        *wire.MsgTx
}
