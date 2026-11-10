package muun_one_struct

import (
	"testing"

	"golang.org/x/tools/go/analysis/analysistest"
)

// TestAnalyzer runs the linter against the testdata packages using analysistest. Each
// testdata source file contains "// want" comments declaring the expected diagnostics.
func TestAnalyzer(t *testing.T) {
	testdata := analysistest.TestData()
	analyzer := newAnalyzer("onestruct", "onestruct/model")
	analysistest.Run(t, testdata, analyzer, "onestruct/good", "onestruct/bad",
		"onestruct/model", "unrelated")
}
