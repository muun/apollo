package muun_struct_order

import (
	"testing"

	"golang.org/x/tools/go/analysis/analysistest"
)

// TestAnalyzer runs the linter against the testdata packages using analysistest. Each
// testdata source file contains "// want" comments declaring the expected diagnostics.
func TestAnalyzer(t *testing.T) {
	testdata := analysistest.TestData()
	analyzer := newAnalyzer("structorder")
	analysistest.Run(t, testdata, analyzer, "structorder/good", "structorder/bad")
}
