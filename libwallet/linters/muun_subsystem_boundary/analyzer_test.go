package muun_subsystem_boundary

import (
	"testing"

	"golang.org/x/tools/go/analysis/analysistest"
)

// TestAnalyzer runs the linter against the testdata packages using analysistest. Each
// testdata source file contains "// want" comments declaring the expected diagnostics.
func TestAnalyzer(t *testing.T) {
	// The analyzer reports a subsystem's structure once per process, so a repeated run
	// would otherwise see no structure diagnostics.
	structureChecked.Clear()

	testdata := analysistest.TestData()
	analysistest.Run(t, testdata, Analyzer,
		"github.com/muun/libwallet/subsystems/beta/presentation/generated",
		"github.com/muun/libwallet/subsystems/alpha/internal/domain/action",
		"github.com/muun/libwallet/subsystems/beta/domain/model",
		"unrelated")
}
