package diagnostics

import (
	"github.com/muun/libwallet/base/data/houston"
	"github.com/muun/libwallet/subsystems/diagnostics/internal/domain/action/debug"
	"github.com/muun/libwallet/subsystems/diagnostics/internal/domain/action/diagnostic_mode_reports"
	"github.com/muun/libwallet/subsystems/diagnostics/internal/domain/diagnostic_mode"
	"github.com/muun/libwallet/subsystems/diagnostics/internal/presentation"
)

type Diagnostics = presentation.Diagnostics

func NewDiagnostics(
	houstonService houston.HoustonService,
	dataDir string,
) Diagnostics {
	return presentation.NewDiagnostics(
		debug.NewZipDataDirAction(dataDir),
		diagnostic_mode_reports.NewSubmitDiagnosticAction(houstonService),
		diagnostic_mode.NewSessionStore(),
	)
}
