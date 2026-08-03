package muun_model_constructor

import (
	"testing"

	"golang.org/x/tools/go/analysis/analysistest"
)

// TestAnalyzer runs the linter against the testdata packages using analysistest. Each testdata
// source file contains "// want" comments that declare the diagnostics the linter must produce.
// See https://pkg.go.dev/golang.org/x/tools/go/analysis/analysistest for the framework.
func TestAnalyzer(t *testing.T) {
	testdata := analysistest.TestData()
	analyzer := newAnalyzer("model")
	analysistest.Run(t, testdata, analyzer, "model", "consumer")
}
